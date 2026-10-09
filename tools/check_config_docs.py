#!/usr/bin/env python3
"""Checks that docs/guide.md section 13 (Configuration) matches the config declared in the code.

The config is read statically from the Java sources under common/src/main/java: every
`ModConfigs.server("x", ...)` / `ModConfigs.client("x", ...)` section, every nested `.section("y", ...)` and every
value declared with a literal name (`.bool("k", ...)`, `.intRange`, `.doubleRange`, `.enumValue`, `.string`,
`.stringList`). Names built at runtime (one key per crime, deed or rank in a loop) are not literals and are skipped;
their group is still checked. Helper methods that wrap `section(...)` (`CrewConfig.sub`, `StationConfig.section`, ...)
and fields that hold a section of another class (`MobConfig.KRAKEN`) are followed.

Fails (exit 1) when:
  1. a top-level section in the code has no row in the guide's server or client table (or sits in the wrong one),
  2. a row in the guide names a section that does not exist in the code,
  3. a nested group of a section (e.g. `crew.rail_lean`, `world.structures.wreck`) is not named in its row in
     backticks (`rail_lean`, or an ancestor group with `.*`, e.g. `structures.*`),
  4. a backticked snake_case name in a row is neither a key nor a group of that section (a renamed or removed key).

Keys themselves are not required in the rows (one row per section; the full list with defaults is
docs/audit/config-defaults.md). `--keys` lists, per section, the literal keys its row does not name (informational).

Usage: python3 tools/check_config_docs.py [--keys] [--json] [--self-test]
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'common', 'src', 'main', 'java')
GUIDE = os.path.join(ROOT, 'docs', 'guide.md')

VALUE_KINDS = ('bool', 'intRange', 'doubleRange', 'enumValue', 'string', 'stringList')
SECTION_DECL = re.compile(r'ConfigSection\s+(\w+)\s*=\s*([^;]+);', re.S)
HELPER = re.compile(
    r'static\s+ConfigSection\s+(\w+)\s*\(\s*String\s+(\w+)\s*,[^)]*\)\s*\{\s*return\s+(\w+(?:\.\w+)?)\.section\(\s*\2\s*,',
    re.S)
VALUE_DECL = re.compile(r'\b(\w+(?:\.\w+)?)\.(' + '|'.join(VALUE_KINDS) + r')\(\s*"([a-z0-9_]+)"')
DYNAMIC_DECL = re.compile(r'\b(\w+(?:\.\w+)?)\.(section|' + '|'.join(VALUE_KINDS) + r')\(\s*(?!")[a-z]')


def read_sources(src=SRC):
    files = {}
    for dp, _, fns in os.walk(src):
        for fn in fns:
            if fn.endswith('.java'):
                with open(os.path.join(dp, fn), encoding='utf-8') as f:
                    text = f.read()
                if 'ConfigSection' in text:
                    files[fn[:-5]] = text
    return files


def strip_comments(text):
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    return re.sub(r'//[^\n]*', ' ', text)


def schema(files):
    """Returns (sections, keys, dynamic): sets of (type, path tuple) for literal declarations, and the sections
    under which names are built at runtime (their children cannot be listed statically)."""
    texts = {c: strip_comments(t) for c, t in files.items()}
    fields = {}   # (class, var) -> (type, path)
    helpers = {}  # (class, method) -> (class, base var)
    pending = []
    for cls, text in texts.items():
        for m in HELPER.finditer(text):
            helpers[(cls, m.group(1))] = (cls, m.group(3))
        for m in SECTION_DECL.finditer(text):
            pending.append((cls, m.group(1), ' '.join(m.group(2).split())))

    def lookup(cls, ref):
        if '.' in ref:
            c, v = ref.split('.', 1)
            return fields.get((c, v))
        return fields.get((cls, ref))

    def resolve(cls, expr):
        m = re.match(r'ModConfigs\.(server|client)\(\s*"([a-z0-9_]+)"', expr)
        if m:
            return (m.group(1), (m.group(2),))
        m = re.match(r'(\w+(?:\.\w+)?)\.section\(\s*"([a-z0-9_]+)"', expr)
        if m:
            base = lookup(cls, m.group(1))
            if base:
                return (base[0], base[1] + (m.group(2),))
        m = re.match(r'(\w+)\.(\w+)\(\s*"([a-z0-9_]+)"', expr)
        if m and (m.group(1), m.group(2)) in helpers:
            hc, hv = helpers[(m.group(1), m.group(2))]
            base = lookup(hc, hv)
            return (base[0], base[1] + (m.group(3),)) if base else None
        m = re.match(r'(\w+)\.([A-Z][A-Z0-9_]*)$', expr)
        if m:
            return fields.get((m.group(1), m.group(2)))
        return None

    changed = True
    while changed:
        changed = False
        for cls, var, expr in pending:
            if (cls, var) in fields:
                continue
            r = resolve(cls, expr)
            if r:
                fields[(cls, var)] = r
                changed = True
    sections = set(fields.values())
    keys = set()
    for cls, text in texts.items():
        for m in VALUE_DECL.finditer(text):
            base = lookup(cls, m.group(1))
            if base:
                keys.add((base[0], base[1] + (m.group(3),)))
    dynamic = set()
    for cls, text in texts.items():
        spans = [m.span() for m in HELPER.finditer(text)]
        for m in DYNAMIC_DECL.finditer(text):
            if any(a <= m.start() < b for a, b in spans):
                continue
            base = lookup(cls, m.group(1))
            if base:
                dynamic.add(base)
    # every key's ancestors are sections too (helper chains we could not follow still show up via their keys)
    for t, p in list(keys):
        for i in range(1, len(p)):
            sections.add((t, p[:i]))
    return sections, keys, dynamic


def guide_rows(text):
    """{'server'|'client': {section: row text}} from the two tables of section 13."""
    m = re.search(r'^## 13\. .*?$(.*?)^## 14\. ', text, re.S | re.M)
    if not m:
        raise SystemExit('docs/guide.md: section 13 not found')
    body = m.group(1)
    rows = {'server': {}, 'client': {}}
    current = None
    for line in body.split('\n'):
        if line.startswith('**Server config**'):
            current = 'server'
        elif line.startswith('**Client config**'):
            current = 'client'
        rm = re.match(r'\|\s*`([a-z0-9_]+)`\s*\|(.*)\|\s*$', line)
        if rm and current:
            rows[current][rm.group(1)] = rm.group(2)
    return rows


def ticked(row):
    return re.findall(r'`([^`]+)`', row)


def check(sections, keys, rows, dynamic=frozenset()):
    errors, warnings, missing_keys = [], [], {}
    for t in ('server', 'client'):
        other = 'client' if t == 'server' else 'server'
        tops = sorted(p[0] for (tt, p) in sections if tt == t and len(p) == 1)
        for s in tops:
            if s not in rows[t]:
                where = f' (it is in the {other} table)' if s in rows[other] else ''
                errors.append(f'{t} section `{s}` has no row in docs/guide.md §13{where}')
        for s in sorted(rows[t]):
            if s not in tops:
                errors.append(f'docs/guide.md §13 {t} row `{s}`: no such {t} section in the code')
        for s in tops:
            if s not in rows[t]:
                continue
            names = ticked(rows[t][s])
            groups = sorted(p[1:] for (tt, p) in sections if tt == t and len(p) > 1 and p[0] == s)
            mine = sorted(p[1:] for (tt, p) in keys if tt == t and p[0] == s)
            for g in groups:
                ok = False
                for n in names:
                    n2 = n.rstrip('.*').rstrip('.')
                    parts = tuple(n2.split('.'))
                    if n.endswith('*') and g[:len(parts)] == parts:
                        ok = True
                    elif parts == g or parts == g[-1:] or (len(parts) <= len(g) and g[-len(parts):] == parts):
                        ok = True
                if not ok:
                    errors.append(f'{t} `{s}`: group `{".".join((s,) + g)}` is not named in its guide row')
            for n in names:
                if not re.fullmatch(r'[a-z0-9_]+(\.[a-z0-9_]+)*(\.\*)?', n) or ('_' not in n and '.' not in n):
                    continue
                parts = tuple(n.rstrip('*').rstrip('.').split('.'))
                if parts[0] == s:
                    parts = parts[1:]
                if not parts:
                    continue
                every = groups + mine
                if not any(e[-len(parts):] == parts or e[:len(parts)] == parts for e in every if len(e) >= len(parts)):
                    msg = f'{t} `{s}`: guide row names `{n}`, which is neither a key nor a group of `{s}`'
                    if any(tt == t and p[0] == s for (tt, p) in dynamic):
                        warnings.append(msg + ' (the section builds names at runtime; check by hand)')
                    else:
                        errors.append(msg)
            covered = set()
            for n in names:
                covered.add(n.rstrip('*').rstrip('.').split('.')[-1])
            miss = [".".join(k) for k in mine if k[-1] not in covered]
            if miss:
                missing_keys[f'{t}:{s}'] = miss
    return errors, warnings, missing_keys


def self_test():
    files = {
        'AConfig': 'class AConfig { static final ConfigSection S = ModConfigs.server("alpha", "c");\n'
                   'static final ConfigSection N = S.section("inner", "c");\n'
                   'static ConfigSection sub(String name, String comment) { return S.section(name, comment); }\n'
                   'X = S.bool("on", true, "c"); Y = N.intRange("count", 1, 0, 2, "c"); }',
        'BConfig': 'class BConfig { private static final ConfigSection S = AConfig.sub("beta", "c");\n'
                   'Z = S.doubleRange("rate", 1.0, 0.0, 2.0, "c"); }',
    }
    sections, keys, _ = schema(files)
    assert ('server', ('alpha', 'beta')) in sections, sections
    assert ('server', ('alpha', 'beta', 'rate')) in keys, keys
    guide = ('## 13. Configuration\n**Server config**\n\n| Section | What |\n|---|---|\n'
             '| `alpha` | on/off; `inner` (count), `beta`. |\n\n**Client config**\n\n## 14. Datapacks\n')
    errors, _, _ = check(sections, keys, guide_rows(guide))
    assert errors == [], errors
    bad = guide.replace('`beta`', '`gamma_rate`')
    errors, _, _ = check(sections, keys, guide_rows(bad))
    assert any('alpha.beta' in e for e in errors) and any('gamma_rate' in e for e in errors), errors
    print('self-test ok')


def main(argv):
    if '--self-test' in argv:
        self_test()
        return 0
    sections, keys, dynamic = schema(read_sources())
    with open(GUIDE, encoding='utf-8') as f:
        rows = guide_rows(f.read())
    errors, warnings, missing = check(sections, keys, rows, dynamic)
    if '--json' in argv:
        print(json.dumps({'errors': errors, 'warnings': warnings, 'keys_not_named': missing}, indent=1))
    else:
        n_server = sum(1 for (t, p) in sections if t == 'server' and len(p) == 1)
        n_client = sum(1 for (t, p) in sections if t == 'client' and len(p) == 1)
        print(f'config: {n_server} server and {n_client} client sections, {len(keys)} literal keys')
        if '--keys' in argv:
            for s, ks in sorted(missing.items()):
                print(f'  {s}: {len(ks)} keys not named in its row: {", ".join(ks)}')
        for w in warnings:
            print('warning ' + w)
        for e in errors:
            print('ERROR ' + e)
        print('ok' if not errors else f'{len(errors)} problem(s)')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
