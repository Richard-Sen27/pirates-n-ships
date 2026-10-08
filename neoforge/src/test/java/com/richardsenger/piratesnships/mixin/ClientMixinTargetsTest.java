package com.richardsenger.piratesnships.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks every mixin of {@code pirates_n_ships.mixins.json} (common, client and server lists) against the classes the
 * NeoForge dev client loads, without starting a client (HV1b). The GameTest server never applies client mixins, so a
 * client mixin whose selector misses its target crashes only when a human opens a world; this test fails the build
 * instead.
 *
 * <p>Classpath: ModDevGradle's {@code addModdingDependenciesTo(sourceSets.test)} in {@code neoforge/build.gradle} puts
 * the same NeoForge-patched, Mojang-named Minecraft jar ({@code neoforge-<version>-minecraft-merged.jar}, client and
 * server classes) on this test's classpath that the dev runs use. {@link #readsTheNeoForgePatchedClasses()} proves it,
 * so the checks cannot silently run against vanilla classes.
 *
 * <p>Per injector it resolves the {@code method} selectors the way Mixin's {@code TargetSelectors} does (bare name or
 * name plus descriptor: the first method that matches; {@code name*}: every method of that name; a selector may match
 * nothing as long as the injector has a target in total) and, for {@code @At("INVOKE")}, counts the call sites of the
 * {@code target} in the selected methods and demands at least {@code require} (or the config's {@code defaultRequire}).
 * Other {@code @At} values only get the method check. Selectors and targets it cannot parse (regex, {@code @Desc}) are
 * skipped and printed by name.
 */
class ClientMixinTargetsTest {

    private static final String CONFIG = "pirates_n_ships.mixins.json";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

    private static final String SECTION_COMPILER = "net/minecraft/client/renderer/chunk/SectionCompiler";
    private static final String NEOFORGE_COMPILE_DESC = "(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Test
    void readsTheNeoForgePatchedClasses() {
        ClassNode compiler = readClass(SECTION_COMPILER);
        assertTrue(compiler.methods.stream().anyMatch(m -> m.name.equals("compile") && m.desc.equals(NEOFORGE_COMPILE_DESC)),
                "SectionCompiler on the test classpath lacks NeoForge's compile overload: this test is reading vanilla "
                        + "classes, not the ones the NeoForge dev client loads");
    }

    @Test
    void everyMixinInjectorFindsItsTarget() {
        JsonObject config = readConfig();
        String pkg = config.get("package").getAsString().replace('.', '/');
        JsonObject injectors = config.has("injectors") ? config.getAsJsonObject("injectors") : new JsonObject();
        int defaultRequire = injectors.has("defaultRequire") ? injectors.get("defaultRequire").getAsInt() : 0;

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int checked = 0;
        for (String list : List.of("mixins", "client", "server")) {
            if (!config.has(list)) {
                continue;
            }
            for (JsonElement entry : config.getAsJsonArray(list)) {
                String mixinName = pkg + "/" + entry.getAsString().replace('.', '/');
                ClassNode mixin = readClass(mixinName);
                List<String> targets = mixinTargets(mixin);
                assertFalse(targets.isEmpty(), mixinName + " has no @Mixin targets");
                for (String targetName : targets) {
                    ClassNode target = readClass(targetName);
                    for (MethodNode handler : mixin.methods) {
                        for (AnnotationNode injector : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                            if (!INJECTORS.contains(injector.desc)) {
                                continue;
                            }
                            String where = list + ":" + entry.getAsString() + "#" + handler.name + " -> " + targetName;
                            checked += checkInjector(injector, target, defaultRequire, where, failures, skipped);
                        }
                    }
                }
            }
        }
        skipped.forEach(s -> System.out.println("[ClientMixinTargetsTest] skipped: " + s));
        System.out.println("[ClientMixinTargetsTest] checked " + checked + " injector(s) against the NeoForge classes");
        assertTrue(failures.isEmpty(), "Mixin injectors that would fail to apply:\n  " + String.join("\n  ", failures));
        assertTrue(checked > 0, "no injector was checked");
    }

    /** The HV1 regression: the water-plant wrap lands exactly once, in NeoForge's patched compile overload. */
    @Test
    void waterPlantWrapHitsNeoForgesBlockLoop() {
        ClassNode mixin = readClass("com/richardsenger/piratesnships/mixin/MixinSectionCompiler");
        ClassNode target = readClass(SECTION_COMPILER);
        AnnotationNode wrap = mixin.methods.stream()
                .filter(m -> m.name.equals("pirates_n_ships$hideWaterPlants"))
                .flatMap(m -> annotations(m.visibleAnnotations, m.invisibleAnnotations).stream())
                .filter(a -> a.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"))
                .findFirst().orElseThrow();
        List<MethodNode> selected = new ArrayList<>();
        for (String selector : ClientMixinTargetsTest.<List<String>>value(wrap, "method")) {
            selected.addAll(select(Selector.parse(selector), target));
        }
        String invoke = ClientMixinTargetsTest.<String>value(atAnnotations(wrap).get(0), "target");
        List<MethodNode> withCall = selected.stream().filter(m -> countCalls(m, invoke) > 0).toList();
        assertEquals(1, withCall.size(), "the getBlockState call must sit in exactly one selected method");
        assertEquals(NEOFORGE_COMPILE_DESC, withCall.get(0).desc);
        assertEquals(1, countCalls(withCall.get(0), invoke), "exactly one getBlockState call site (one read per block)");
    }

    /** Guards the selector model itself against HV1's mistake: a bare "compile" picks the call-free delegate. */
    @Test
    void bareCompileSelectorPicksNeoForgesDelegate() {
        List<MethodNode> bare = select(Selector.parse("compile"), readClass(SECTION_COMPILER));
        assertEquals(1, bare.size());
        assertEquals(0, countCalls(bare.get(0), "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState"
                + "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"));
    }

    /**
     * FLD1: the underwater overlay wrap lands on the one eye-in-water test of {@code renderScreenEffect} (NeoForge keeps
     * the call on {@code LocalPlayer}, the receiver's static type), and the camera's fluid method exists once with the
     * signature the {@code @ModifyReturnValue} returns.
     */
    @Test
    void floodSurfaceMixinsHitTheirTargets() {
        ClassNode screen = readClass("net/minecraft/client/renderer/ScreenEffectRenderer");
        List<MethodNode> render = select(Selector.parse("renderScreenEffect"), screen);
        assertEquals(1, render.size());
        assertEquals(1, countCalls(render.get(0), "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"),
                "exactly one eye-in-fluid test in renderScreenEffect");
        assertTrue((render.get(0).access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0, "the wrap handler is static");

        ClassNode camera = readClass("net/minecraft/client/Camera");
        // the @Shadow fields of MixinCamera
        assertTrue(camera.fields.stream().anyMatch(f -> f.name.equals("level") && f.desc.equals("Lnet/minecraft/world/level/BlockGetter;")));
        assertTrue(camera.fields.stream().anyMatch(f -> f.name.equals("position") && f.desc.equals("Lnet/minecraft/world/phys/Vec3;")));
        List<MethodNode> fluid = select(Selector.parse("getFluidInCamera"), camera);
        assertEquals(1, fluid.size());
        assertEquals("()Lnet/minecraft/world/level/material/FogType;", fluid.get(0).desc);
        ClassNode mixin = readClass("com/richardsenger/piratesnships/mixin/MixinCamera");
        assertTrue(mixin.methods.stream().anyMatch(m -> m.name.equals("pirates_n_ships$underFloodSurface")
                && m.desc.equals("(Lnet/minecraft/world/level/material/FogType;)Lnet/minecraft/world/level/material/FogType;")));
    }

    // ---- injector check ----

    private static int checkInjector(AnnotationNode injector, ClassNode target, int defaultRequire, String where,
                                     List<String> failures, List<String> skipped) {
        List<String> selectors = value(injector, "method");
        if (selectors == null || selectors.isEmpty()) {
            skipped.add(where + " (no method selector)");
            return 0;
        }
        Integer requireValue = value(injector, "require");
        int require = Math.max(1, requireValue != null && requireValue >= 0 ? requireValue : defaultRequire);

        List<MethodNode> selected = new ArrayList<>();
        for (String raw : selectors) {
            Selector selector = Selector.parse(raw);
            if (selector == null) {
                skipped.add(where + " (unparsed selector '" + raw + "')");
                return 0;
            }
            selected.addAll(select(selector, target));
        }
        if (selected.isEmpty()) {
            failures.add(where + ": no method matches " + selectors);
            return 1;
        }
        for (AnnotationNode at : atAnnotations(injector)) {
            String point = value(at, "value");
            if (!"INVOKE".equals(point)) {
                continue; // HEAD, RETURN, TAIL, ...: the method check above is all this test does
            }
            String invoke = value(at, "target");
            if (invoke == null || !invoke.startsWith("L") || invoke.indexOf(';') < 0
                    || invoke.indexOf('(') < invoke.indexOf(';')) {
                skipped.add(where + " (unparsed INVOKE target '" + invoke + "')");
                continue;
            }
            int calls = selected.stream().mapToInt(m -> countCalls(m, invoke)).sum();
            if (calls < require) {
                failures.add(where + ": " + calls + " call site(s) of " + invoke + " in "
                        + selected.stream().map(m -> m.name + m.desc).toList() + ", require " + require);
            }
        }
        return 1;
    }

    /** Mixin's root selection: bare name or name plus descriptor take the first match, {@code name*} takes all. */
    private static List<MethodNode> select(Selector selector, ClassNode target) {
        List<MethodNode> matches = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (method.name.equals(selector.name()) && (selector.desc() == null || method.desc.equals(selector.desc()))) {
                matches.add(method);
                if (!selector.all()) {
                    break;
                }
            }
        }
        return matches;
    }

    private static int countCalls(MethodNode method, String invokeTarget) {
        String owner = invokeTarget.substring(1, invokeTarget.indexOf(';'));
        String rest = invokeTarget.substring(invokeTarget.indexOf(';') + 1);
        String name = rest.substring(0, rest.indexOf('('));
        String desc = rest.substring(rest.indexOf('('));
        int count = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(desc)) {
                count++;
            }
        }
        return count;
    }

    private record Selector(String name, String desc, boolean all) {
        /** {@code [Lowner;]name[*][(desc)ret]}; null for regex, {@code @Desc} or other forms this test does not model. */
        static Selector parse(String raw) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("@") || s.contains(" ")) {
                return null;
            }
            int semicolon = s.indexOf(';');
            int paren = s.indexOf('(');
            if (s.startsWith("L") && semicolon > 0 && (paren < 0 || semicolon < paren)) {
                s = s.substring(semicolon + 1);
                paren = s.indexOf('(');
            }
            String desc = null;
            if (paren >= 0) {
                desc = s.substring(paren);
                s = s.substring(0, paren);
            }
            boolean all = s.endsWith("*");
            if (all) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.isEmpty() || s.contains("*") || s.contains("/") || s.contains("^")) {
                return null;
            }
            return new Selector(s, desc, all);
        }
    }

    // ---- ASM and config helpers ----

    private static JsonObject readConfig() {
        try (InputStream in = ClientMixinTargetsTest.class.getClassLoader().getResourceAsStream(CONFIG)) {
            assertNotNull(in, CONFIG + " is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static ClassNode readClass(String internalName) {
        try (InputStream in = ClientMixinTargetsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                fail(internalName + " is not on the test classpath");
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static List<String> mixinTargets(ClassNode mixin) {
        List<String> targets = new ArrayList<>();
        for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
            if (!annotation.desc.equals(MIXIN)) {
                continue;
            }
            List<Type> types = value(annotation, "value");
            if (types != null) {
                types.forEach(t -> targets.add(t.getInternalName()));
            }
            List<String> names = value(annotation, "targets");
            if (names != null) {
                names.forEach(n -> targets.add(n.replace('.', '/')));
            }
        }
        return targets;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    /** {@code at} is a single {@code @At} on some injectors and an array on others. */
    private static List<AnnotationNode> atAnnotations(AnnotationNode injector) {
        Object at = value(injector, "at");
        if (at instanceof AnnotationNode single) {
            return List.of(single);
        }
        if (at instanceof List<?> list) {
            return list.stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList();
        }
        return List.of();
    }

    /** An annotation value as ASM stores it; enum values ({@code String[]{desc, name}}) come back as the name. */
    @SuppressWarnings("unchecked")
    private static <T> T value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                Object v = annotation.values.get(i + 1);
                if (v instanceof String[] enumValue) {
                    v = enumValue[1];
                }
                return (T) v;
            }
        }
        return null;
    }
}
