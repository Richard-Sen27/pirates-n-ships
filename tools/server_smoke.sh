#!/usr/bin/env bash
# Dedicated-server smoke test (REL3): starts a dedicated server with the mod on a fresh world, drives the mod's server
# paths through RCON console commands, lets it tick, saves, stops, and fails on any crash sign in the log.
#
#   tools/server_smoke.sh <neoforge|fabric> [--jar]
#
# Without --jar it starts the loader's dev server run (./gradlew :<loader>:runServer). With --jar it runs the built
# release jar the way a player's server does: a real NeoForge or Fabric server (installed once by the loader's own
# installer into <loader>/build/server-smoke-install/), with our jar and the required mods as published in mods/
# (./gradlew :<loader>:serverSmokeKit collects them). Only --jar catches a client class reached from server code on
# NeoForge (the dev run's Minecraft jar keeps client classes loadable) and checks the jar-in-jar and the metadata.
#
# Run it from anywhere; it runs Gradle in the repository it lives in. What it covers and what it does not:
# docs/releasing.md, section "Dedicated-server smoke test". Output (the full log, the command replies, the summary)
# goes to build/server-smoke/<loader>/ (SMOKE_OUT overrides build/server-smoke). The world is deleted at every start;
# the normal runServer directories are never touched.
#
# Environment: SMOKE_TIMEOUT (seconds from the server's start to "Done", default 600), SMOKE_TICK_SECONDS (ticking time
# after the commands, default 30), SMOKE_SEED (level seed, default below), SMOKE_GRADLE_ARGS (extra Gradle arguments,
# e.g. "-Pversion=0.1.0-beta.1" on CI so that the run uses the release build), SMOKE_RCON_TIMEOUT (seconds one command
# reply may take, default 300: the /locate of the port structures searched for minutes on a loaded machine, WG6).
set -uo pipefail

usage() {
    echo "usage: $0 <neoforge|fabric> [--jar]" >&2
    exit 64
}
loader="${1:-}"
mode="${2:---dev}"
case "$loader" in
    neoforge | fabric) ;;
    *) usage ;;
esac
case "$mode" in
    --dev | --jar) ;;
    *) usage ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_base="${SMOKE_OUT:-$root/build/server-smoke}"
mkdir -p "$out_base"
out="$(cd "$out_base" && pwd)/$loader"
log="$out/server.log"
replies="$out/commands.txt"
summary="$out/summary.txt"
timeout="${SMOKE_TIMEOUT:-600}"
tick_seconds="${SMOKE_TICK_SECONDS:-30}"
seed="${SMOKE_SEED:-2718281828}"
export RCON_TIMEOUT="${SMOKE_RCON_TIMEOUT:-300}"
rcon_password="smoke-$RANDOM-$RANDOM"
mod_id="pirates_n_ships"

free_port() {
    python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}
server_port="$(free_port)"
rcon_port="$(free_port)"
while [[ "$rcon_port" == "$server_port" ]]; do rcon_port="$(free_port)"; done

rm -rf "$out"
mkdir -p "$out"
: >"$replies"

status=0
failures=()
fail() {
    failures+=("$1")
    status=1
}
summary_mode="dev run (:$loader:runServer)"

if [[ "$mode" == --jar ]]; then
    # The kit: our release jar, the required mods, the loader's installer and the Java 21 launcher.
    kit="$root/$loader/build/server-smoke-kit"
    # shellcheck disable=SC2086 # SMOKE_GRADLE_ARGS is a list of arguments
    if ! "$root/gradlew" -p "$root" ":$loader:serverSmokeKit" --console=plain ${SMOKE_GRADLE_ARGS:-} >"$out/kit.log" 2>&1; then
        tail -30 "$out/kit.log"
        echo "Dedicated-server smoke test, $loader: FAILED (./gradlew :$loader:serverSmokeKit, log $out/kit.log)"
        exit 1
    fi
    java="$(head -1 "$kit/java.txt")"
    mc_version="$(sed -n 's/^minecraft=//p' "$kit/versions.txt")"
    loader_version="$(sed -n 's/^loader=//p' "$kit/versions.txt")"
    run_dir="$root/$loader/build/server-smoke-install/$mc_version-$loader_version"
    if [[ ! -f "$run_dir/.installed" ]]; then
        rm -rf "$run_dir"
        mkdir -p "$run_dir"
        echo "Installing the $loader $loader_version server into $run_dir (once per version) ..."
        if [[ "$loader" == neoforge ]]; then
            install=("$java" -jar "$kit/installer.jar" --install-server "$run_dir")
        else
            install=("$java" -jar "$kit/installer.jar" server -dir "$run_dir" -mcversion "$mc_version"
                -loader "$loader_version" -downloadMinecraft)
        fi
        if ! (cd "$run_dir" && "${install[@]}") >"$out/install.log" 2>&1; then
            tail -30 "$out/install.log"
            echo "Dedicated-server smoke test, $loader: FAILED (the $loader server installer, log $out/install.log)"
            exit 1
        fi
        touch "$run_dir/.installed"
    fi
    rm -rf "$run_dir/world" "$run_dir/mods" "$run_dir/config" "$run_dir/logs" "$run_dir/crash-reports" \
        "$run_dir/defaultconfigs" "$run_dir/server.properties"
    mkdir -p "$run_dir/mods"
    cp "$kit"/mods/*.jar "$run_dir/mods/"
    if [[ "$loader" == neoforge ]]; then
        launch=("$java" -Xmx2G @user_jvm_args.txt "@libraries/net/neoforged/neoforge/$loader_version/unix_args.txt" nogui)
    else
        launch=("$java" -Xmx2G -jar fabric-server-launch.jar nogui)
    fi
    summary_mode="release jar on a $loader $loader_version server, mods: $(cd "$kit/mods" && ls -- *.jar | tr '\n' ' ')"
else
    run_rel="build/server-smoke" # relative to the loader project, as both run configurations expect
    run_dir="$root/$loader/$run_rel"
    rm -rf "$run_dir"
    mkdir -p "$run_dir"
    # shellcheck disable=SC2206 # SMOKE_GRADLE_ARGS is a list of arguments
    launch=("$root/gradlew" -p "$root" ":$loader:runServer" "-P$mod_id.server.dir=$run_rel" --console=plain
        ${SMOKE_GRADLE_ARGS:-})
fi

# A fresh world with a fixed seed. RCON carries the console commands (Gradle does not forward stdin to the run).
echo "eula=true" >"$run_dir/eula.txt"
cat >"$run_dir/server.properties" <<EOF
online-mode=false
level-seed=$seed
level-type=minecraft\:normal
generate-structures=true
max-tick-time=-1
view-distance=4
simulation-distance=4
spawn-protection=0
server-port=$server_port
enable-rcon=true
rcon.port=$rcon_port
rcon.password=$rcon_password
broadcast-rcon-to-ops=false
motd=Pirates 'n' Ships smoke test
EOF

# The server: one process in the background, the Gradle run or the server JVM itself. Its pid is ours alone; stopping
# it (only on a timeout) stops this run without touching any other build or server on the machine.
started=$(date +%s)
(cd "$run_dir" && exec "${launch[@]}") >"$log" 2>&1 &
server_pid=$!

deadline=$((started + timeout))
stop_run() {
    if kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null
        sleep 10
        kill -9 "$server_pid" 2>/dev/null
    fi
}

rcon() {
    python3 "$root/tools/rcon.py" "$rcon_port" "$rcon_password" "$@" >>"$replies" 2>&1
}

# 1. Wait for "Done (".
done_line=""
while :; do
    done_line="$(grep -m1 -E 'Done \([0-9.]+s\)!' "$log" 2>/dev/null || true)"
    [[ -n "$done_line" ]] && break
    if ! kill -0 "$server_pid" 2>/dev/null; then
        fail "the server exited before \"Done (\""
        break
    fi
    if (($(date +%s) >= deadline)); then
        fail "no \"Done (\" within ${timeout}s"
        stop_run
        break
    fi
    sleep 2
done
ready=$(date +%s)
startup_wall=$((ready - started))
startup_server="$(sed -E -n 's/.*Done \(([0-9.]+)s\)!.*/\1/p' <<<"$done_line")"

# 2. The commands. Every reply lands in commands.txt; a command that cannot run from the console only fails there.
skipped=(
    "/pirates trade open|goods|buy|sell|weight|plunder|coins, trade contract list|accept|deliver: need a player"
    "/pirates world voyages spawn near …: spawns ahead of a player"
    "/pirates crew spawn|hire, provisions, career|rep|quest of oneself, law/brig on a player: need a player or a target entity"
    "/pirates ship forces|rigging|info: need a player standing on a ship"
)
if [[ -n "$done_line" ]]; then
    rcon "pirates ship templates" "locate biome #minecraft:is_ocean"
    sea="$(grep -A3 -F '> locate biome' "$replies" | sed -E -n 's/.*\[(-?[0-9]+), [^,]+, (-?[0-9]+)\].*/\1 \2/p' | head -1)"
    if [[ -z "$sea" ]]; then
        fail "no ocean found near spawn (seed $seed): the ship was not placed"
        sea="0 0"
    fi
    read -r sx sz <<<"$sea"
    here="execute in minecraft:overworld positioned $sx 64 $sz run"
    rcon "forceload add $((sx - 32)) $((sz - 32)) $((sx + 32)) $((sz + 32))" \
        "$here pirates ship place starter_sloop assemble" \
        "$here pirates mob spawn pirate 2" \
        "$here pirates mob spawn shark" \
        "pirates mob spawn sailor" \
        "pirates mob spawn navy_soldier" \
        "pirates mob spawn harbor_master" \
        "pirates waves set rough" \
        "pirates waves" \
        "$here pirates wind get" \
        "pirates world ports" \
        "pirates world port nearest" \
        "pirates trade port smoke_north seafarer_village tropical" \
        "pirates trade port smoke_south navy_outpost temperate" \
        "pirates trade contracts smoke_north smoke_south" \
        "pirates world factions" \
        "pirates world raid chance" \
        "pirates world patrols" \
        "pirates mob captain list" \
        "$here pirates hazard spawn waterspout"
    # Ports through the worldgen path: locate the nearest seafarer village, pirate island and navy outpost, and
    # generate their chunks (forceload) so that the structures place and the port registry records them. A convoy is
    # tried between each pair of the ports /pirates world ports then lists, until one has a sea lane.
    for structure in seafarer_village pirate_island navy_outpost; do
        rcon "locate structure $mod_id:$structure"
        at="$(grep -A2 -F "> locate structure $mod_id:$structure" "$replies" \
            | sed -E -n 's/.*\[(-?[0-9]+), [^,]+, (-?[0-9]+)\].*/\1 \2/p' | head -1)"
        if [[ -n "$at" ]]; then
            read -r px pz <<<"$at"
            rcon "forceload add $((px - 48)) $((pz - 48)) $((px + 48)) $((pz + 48))"
        fi
    done
    rcon "pirates world ports"
    ports=()
    # Port ids by pattern, not by line: on Fabric a multi-line reply comes back without line breaks (vanilla's RCON
    # source joins the messages; NeoForge patches a newline in).
    while IFS= read -r p; do ports+=("$p"); done < <(awk '/^> pirates world ports/ { n++; next } /^> / { if (n == 2) exit; next } n == 2' \
        "$replies" | grep -oE "$mod_id:(village|pirate_island|navy_outpost)_-?[0-9]+_-?[0-9]+" | awk '!seen[$0]++' | head -3)
    if ((${#ports[@]} >= 2)); then
        convoy=0
        for ((i = 0; i < ${#ports[@]} && convoy == 0; i++)); do
            for ((j = i + 1; j < ${#ports[@]}; j++)); do
                # The lane search first (its reply says why there is no lane, if there is none), then the convoy.
                rcon "pirates world voyages lane ${ports[i]} ${ports[j]}" \
                    "pirates world voyages spawn convoy ${ports[i]} ${ports[j]}"
                if ! tail -2 "$replies" | grep -q -F 'No convoy from'; then
                    convoy=1
                    break
                fi
            done
        done
        # The nearest port of each kind can lie on separate seas (LANE1: on the default seed the village, the island
        # and the outpost lie on three). The lane reply then names the extent of the closed sea around the first port,
        # "(x A..B, z C..D)": look for ports of each kind from the middle of the largest such sea and of its quarters,
        # generate them, and try each pair with a new port. (Since NAME3 the reply names the port, "Port Marlow", which
        # may hold spaces but never a parenthesis.)
        if ((convoy == 0)); then
            box="$(grep -oE 'the sea around [^(]+ \(x -?[0-9]+\.\.-?[0-9]+, z -?[0-9]+\.\.-?[0-9]+\)' "$replies" \
                | sed -E 's/.*\(x (-?[0-9]+)\.\.(-?[0-9]+), z (-?[0-9]+)\.\.(-?[0-9]+)\)/\1 \2 \3 \4/' \
                | awk '{ print ($2 - $1) * ($4 - $3), $0 }' | sort -n -r | head -1 | cut -d' ' -f2-)"
            points=()
            if [[ -n "$box" ]]; then
                read -r x0 x1 z0 z1 <<<"$box"
                points=("$(((x0 + x1) / 2)) $(((z0 + z1) / 2))" "$(((3 * x0 + x1) / 4)) $(((3 * z0 + z1) / 4))"
                    "$(((x0 + 3 * x1) / 4)) $(((3 * z0 + z1) / 4))" "$(((3 * x0 + x1) / 4)) $(((z0 + 3 * z1) / 4))"
                    "$(((x0 + 3 * x1) / 4)) $(((z0 + 3 * z1) / 4))")
            fi
            for point in "${points[@]}"; do
                read -r qx qz <<<"$point"
                for structure in seafarer_village pirate_island navy_outpost; do
                    cmd="execute in minecraft:overworld positioned $qx 64 $qz run locate structure $mod_id:$structure"
                    rcon "$cmd"
                    at="$(grep -A2 -F "> $cmd" "$replies" | sed -E -n 's/.*\[(-?[0-9]+), [^,]+, (-?[0-9]+)\].*/\1 \2/p' | head -1)"
                    if [[ -n "$at" ]]; then
                        read -r px pz <<<"$at"
                        rcon "forceload add $((px - 48)) $((pz - 48)) $((px + 48)) $((pz + 48))"
                    fi
                done
            done
            rcon "pirates world ports"
            more=()
            while IFS= read -r p; do more+=("$p"); done < <(awk '/^> pirates world ports/ { n++; next } /^> / { if (n == 3) exit; next } n == 3' \
                "$replies" | grep -oE "$mod_id:(village|pirate_island|navy_outpost)_-?[0-9]+_-?[0-9]+" | awk '!seen[$0]++')
            is_new() {
                local q
                for q in "${ports[@]}"; do [[ "$q" == "$1" ]] && return 1; done
                return 0
            }
            for ((i = 0; i < ${#more[@]} && convoy == 0; i++)); do
                for ((j = i + 1; j < ${#more[@]}; j++)); do
                    is_new "${more[i]}" || is_new "${more[j]}" || continue
                    rcon "pirates world voyages lane ${more[i]} ${more[j]}" \
                        "pirates world voyages spawn convoy ${more[i]} ${more[j]}"
                    if ! tail -2 "$replies" | grep -q -F 'No convoy from'; then
                        convoy=1
                        break
                    fi
                done
            done
        fi
        ((convoy)) || skipped+=("a convoy that sails: no sea lane between the generated ports on seed $seed")
    else
        fail "/pirates world voyages spawn convoy: fewer than two world ports after generating three port structures"
    fi
    rcon "reload"

    # 3. Ticking, then the state after it, a save and the stop.
    sleep "$tick_seconds"
    rcon "pirates world voyages" "$here pirates hazard clear 64" "pirates waves clear" "list" "save-all flush"
    stopping=$(date +%s)
    python3 "$root/tools/rcon.py" "$rcon_port" "$rcon_password" "stop" >>"$replies" 2>&1 || true

    # 4. The server must exit on its own.
    exit_deadline=$((stopping + 120))
    while kill -0 "$server_pid" 2>/dev/null; do
        if (($(date +%s) >= exit_deadline)); then
            fail "the server did not exit within 120 s of /stop"
            stop_run
            break
        fi
        sleep 2
    done
fi
wait "$server_pid"
server_exit=$?
[[ $server_exit -ne 0 ]] && fail "the server process (Gradle in dev mode) exited with code $server_exit"
grep -q -F 'Stopping server' "$log" || fail "no \"Stopping server\" in the log"
grep -q -E 'Saving chunks for level|All dimensions are saved|ThreadedAnvilChunkStorage: All dimensions are saved' "$log" \
    || fail "no save on shutdown in the log"
if [[ -n "$(ls -A "$run_dir/crash-reports" 2>/dev/null)" ]]; then
    fail "crash reports in $run_dir/crash-reports"
    cp -R "$run_dir/crash-reports" "$out/"
fi

# 5. The log scan. Every hit is a failure.
crash_pattern='Exception|NoClassDefFoundError|ClassNotFoundException|Mixin apply failed|[Cc]rash|An unexpected error occurred'
# The one exception: Fabric's mod list names the Fabric API module fabric-crash-report-info-v1.
crash_hits="$(grep -n -E "$crash_pattern" "$log" "$replies" | grep -v -E '^[^:]+:[0-9]+:[[:space:]|-]+fabric-crash-report-info-v1 [0-9]' || true)"
[[ -n "$crash_hits" ]] && fail "crash signs in the log or the replies ($(wc -l <<<"$crash_hits" | tr -d ' ') lines)"
mod_warnings="$(grep -E '(WARN|ERROR).*('"$mod_id"'|piratesnships|Pirates)' "$log" || true)"

# Ports on the seed (WG6): the nearest port structure of each kind from spawn (the first /locate replies, run from the
# console at the world spawn) and every pair of the listed ports less than 6000 blocks apart (the default convoy reach,
# world_simulation.voyages.convoy_max_distance), centre to centre from the coordinates in the port ids.
port_report="$(
    for structure in seafarer_village navy_outpost pirate_island; do
        away="$(grep -A2 -F "> locate structure $mod_id:$structure" "$replies" 2>/dev/null \
            | sed -E -n 's/.*\[(-?[0-9]+), [^,]+, (-?[0-9]+)\] \(([0-9]+) blocks away\).*/\3 blocks (at \1 \2)/p' | head -1)"
        echo "nearest $structure from spawn: ${away:-not found}"
    done
    grep -oE "$mod_id:(village|pirate_island|navy_outpost)_-?[0-9]+_-?[0-9]+" "$replies" 2>/dev/null | awk '!seen[$0]++' \
        | sed -E 's/.*_(-?[0-9]+)_(-?[0-9]+)$/& \1 \2/' \
        | awk '{ id[NR] = $1; x[NR] = $2; z[NR] = $3 }
            END {
                for (i = 1; i <= NR; i++) for (j = i + 1; j <= NR; j++) {
                    d = sqrt((x[i] - x[j]) ^ 2 + (z[i] - z[j]) ^ 2)
                    if (d < 6000) { printf "%s - %s: %d blocks\n", id[i], id[j], d; pairs++ }
                }
                printf "%d listed ports, %d pairs under 6000 blocks\n", NR, pairs
            }'
)"

{
    echo "Dedicated-server smoke test, $loader: $( ((status == 0)) && echo PASSED || echo FAILED)"
    echo "Server: $summary_mode"
    echo "Seed $seed, ocean at ${sx:-?} ${sz:-?}; log: $log"
    echo "Startup: ${startup_wall}s from the process start to \"Done\" (the server says ${startup_server:-?}s)"
    echo
    if ((${#failures[@]})); then
        echo "Failures:"
        printf '  - %s\n' "${failures[@]}"
        echo
    fi
    if [[ -n "$crash_hits" ]]; then
        echo "Crash signs (first 40):"
        head -40 <<<"$crash_hits" | sed 's/^/  /'
        echo
    fi
    echo "Ports on the seed:"
    sed 's/^/  /' <<<"$port_report"
    echo
    echo "Command replies:"
    sed 's/^/  /' "$replies"
    echo
    echo "Skipped:"
    printf '  - %s\n' "${skipped[@]}"
    echo
    if [[ -n "$mod_warnings" ]]; then
        echo "Warnings and errors mentioning the mod ($(wc -l <<<"$mod_warnings" | tr -d ' ')):"
        head -40 <<<"$mod_warnings" | sed 's/^/  /'
    else
        echo "No warnings or errors mention the mod."
    fi
} >"$summary"
cat "$summary"
exit "$status"
