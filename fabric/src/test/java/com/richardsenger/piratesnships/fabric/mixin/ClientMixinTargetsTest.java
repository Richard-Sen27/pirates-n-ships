package com.richardsenger.piratesnships.fabric.mixin;

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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks every mixin of both our mixin configs ({@code pirates_n_ships.mixins.json} from common and
 * {@code pirates_n_ships.fabric.mixins.json}; common, client and server lists) against the classes the Fabric dev
 * client loads, without starting a client: the Fabric twin of neoforge's {@code ClientMixinTargetsTest} (HV1b, FAB2).
 * The GameTest server never applies client mixins, so a client mixin whose selector misses its target crashes only when
 * a human opens a world; this test fails the build instead.
 *
 * <p>Classpath: Loom puts the Mojang-named, merged (client and server) Minecraft jar on this test's classpath, the one
 * the dev runs use (Fabric does not patch Minecraft; only mixins change it at runtime, so these are the classes our
 * mixins meet, give or take other mods' mixins). {@link #readsVanillaClasses()} proves it is vanilla, not
 * NeoForge-patched code.
 *
 * <p>Per injector it resolves the {@code method} selectors the way Mixin's {@code TargetSelectors} does (bare name or
 * name plus descriptor: the first method that matches; {@code name*}: every method of that name; a selector may match
 * nothing as long as the injector has a target in total) and, for {@code @At("INVOKE")}, counts the call sites of the
 * {@code target} in the selected methods and demands at least {@code require} (or the config's {@code defaultRequire}),
 * and more than {@code ordinal} when the {@code @At} has one. Other {@code @At} values only get the method check.
 * Selectors and targets it cannot parse (regex, {@code @Desc}) are skipped and printed by name.
 */
class ClientMixinTargetsTest {

    private static final List<String> CONFIGS = List.of("pirates_n_ships.mixins.json", "pirates_n_ships.fabric.mixins.json");
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
    private static final String VANILLA_COMPILE_DESC = "(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;"
            + "Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";
    private static final String GET_BLOCK_STATE = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;getBlockState"
            + "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;";

    @Test
    void readsVanillaClasses() {
        ClassNode compiler = readClass(SECTION_COMPILER);
        List<MethodNode> compiles = compiler.methods.stream().filter(m -> m.name.equals("compile")).toList();
        assertEquals(1, compiles.size(), "vanilla SectionCompiler has one compile method (NeoForge adds an overload)");
        assertEquals(VANILLA_COMPILE_DESC, compiles.get(0).desc);
        assertTrue(compiler.methods.stream().noneMatch(m -> m.desc.contains("net/neoforged")),
                "SectionCompiler on the test classpath mentions NeoForge types: not the classes Fabric loads");
    }

    @Test
    void everyMixinInjectorFindsItsTarget() {
        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int checked = 0;
        int mixins = 0;
        for (String configName : CONFIGS) {
            JsonObject config = readConfig(configName);
            String pkg = config.get("package").getAsString().replace('.', '/');
            JsonObject injectors = config.has("injectors") ? config.getAsJsonObject("injectors") : new JsonObject();
            int defaultRequire = injectors.has("defaultRequire") ? injectors.get("defaultRequire").getAsInt() : 0;
            for (String list : List.of("mixins", "client", "server")) {
                if (!config.has(list)) {
                    continue;
                }
                for (JsonElement entry : config.getAsJsonArray(list)) {
                    mixins++;
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
                                String where = configName + " " + list + ":" + entry.getAsString() + "#" + handler.name + " -> " + targetName;
                                checked += checkInjector(injector, target, defaultRequire, where, failures, skipped);
                            }
                        }
                    }
                }
            }
        }
        skipped.forEach(s -> System.out.println("[ClientMixinTargetsTest] skipped: " + s));
        System.out.println("[ClientMixinTargetsTest] checked " + checked + " injector(s) of " + mixins
                + " mixin(s) against the vanilla classes");
        assertTrue(failures.isEmpty(), "Mixin injectors that would fail to apply:\n  " + String.join("\n  ", failures));
        assertTrue(checked > 0, "no injector was checked");
    }

    /** HV1 on Fabric: the common water-plant wrap lands exactly once, in vanilla's only compile method. */
    @Test
    void waterPlantWrapHitsVanillasBlockLoop() {
        ClassNode mixin = readClass("com/richardsenger/piratesnships/mixin/MixinSectionCompiler");
        AnnotationNode wrap = injector(mixin, "pirates_n_ships$hideWaterPlants",
                "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        List<MethodNode> selected = selected(wrap, readClass(SECTION_COMPILER));
        assertEquals(1, selected.size(), "only the vanilla selector matches on Fabric");
        assertEquals(VANILLA_COMPILE_DESC, selected.get(0).desc);
        assertEquals(1, countCalls(selected.get(0), GET_BLOCK_STATE), "exactly one getBlockState call site (one read per block)");
    }

    /** FLD1 on Fabric: the underwater overlay wrap and the camera's fluid hook meet vanilla's methods. */
    @Test
    void floodSurfaceMixinsHitTheirTargets() {
        ClassNode screen = readClass("net/minecraft/client/renderer/ScreenEffectRenderer");
        List<MethodNode> render = select(Selector.parse("renderScreenEffect"), screen);
        assertEquals(1, render.size());
        assertEquals(1, countCalls(render.get(0), "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"),
                "exactly one eye-in-fluid test in renderScreenEffect");
        assertTrue((render.get(0).access & Opcodes.ACC_STATIC) != 0, "the wrap handler is static");

        ClassNode camera = readClass("net/minecraft/client/Camera");
        assertTrue(camera.fields.stream().anyMatch(f -> f.name.equals("level") && f.desc.equals("Lnet/minecraft/world/level/BlockGetter;")));
        assertTrue(camera.fields.stream().anyMatch(f -> f.name.equals("position") && f.desc.equals("Lnet/minecraft/world/phys/Vec3;")));
        List<MethodNode> fluid = select(Selector.parse("getFluidInCamera"), camera);
        assertEquals(1, fluid.size());
        assertEquals("()Lnet/minecraft/world/level/material/FogType;", fluid.get(0).desc);
    }

    /**
     * FAB2's input mixin: each INTERACTION_KEY injection point exists exactly once in its method, so the event fires
     * once per input and at NeoForge's spot (see {@code MixinMinecraft}).
     */
    @Test
    void interactionKeyPointsAreUnique() {
        ClassNode minecraft = readClass("net/minecraft/client/Minecraft");
        assertEquals(1, calls(minecraft, "startAttack", "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"));
        assertEquals(1, calls(minecraft, "continueAttack",
                "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"));
        assertEquals(1, calls(minecraft, "startUseItem",
                "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"));
        assertEquals(1, calls(minecraft, "pickBlock", "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"));
        assertEquals(1, calls(minecraft, "runTick", "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"));
        // the @Local InteractionHand of the use-key injection needs exactly one local of that type in startUseItem
        MethodNode use = select(Selector.parse("startUseItem"), minecraft).get(0);
        assertEquals(1, use.localVariables == null ? 1 : use.localVariables.stream()
                .filter(v -> v.desc.equals("Lnet/minecraft/world/InteractionHand;")).count(),
                "startUseItem must hold one InteractionHand local for @Local");
    }

    /**
     * FAB2's view mixins: the camera's three {@code setRotation} calls in {@code setup} (main, mirrored third person,
     * sleeping) in that order, and the single rotation of {@code setRotation} whose z angle carries the roll; the one
     * {@code Mth.lerp} of the FOV modifier, the music choice and the sound setup task.
     */
    @Test
    void viewAndSoundPointsAreUnique() {
        ClassNode camera = readClass("net/minecraft/client/Camera");
        assertEquals(3, calls(camera, "setup", "Lnet/minecraft/client/Camera;setRotation(FF)V"));
        assertEquals(1, calls(camera, "setRotation(FF)V", "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"));
        MethodNode setup = select(Selector.parse("setup"), camera).get(0);
        assertEquals(1, java.util.Arrays.stream(Type.getArgumentTypes(setup.desc)).filter(Type.FLOAT_TYPE::equals).count(),
                "setup has exactly one float argument (the partial tick, read with @Local(argsOnly = true))");
        assertEquals(1, calls(readClass("net/minecraft/client/player/AbstractClientPlayer"), "getFieldOfViewModifier",
                "Lnet/minecraft/util/Mth;lerp(FFF)F"));
        assertEquals(1, calls(readClass("net/minecraft/client/sounds/MusicManager"), "tick",
                "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/sounds/Music;"));
        assertEquals(1, calls(readClass("net/minecraft/client/sounds/SoundEngine"),
                "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
                "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"));
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
            Integer ordinal = value(at, "ordinal");
            for (MethodNode method : selected) {
                int calls = countCalls(method, invoke);
                if (ordinal != null && ordinal >= 0 && calls > 0 && calls <= ordinal) {
                    failures.add(where + ": ordinal " + ordinal + " but only " + calls + " call site(s) of " + invoke
                            + " in " + method.name + method.desc);
                }
            }
            int calls = selected.stream().mapToInt(m -> countCalls(m, invoke)).sum();
            int needed = ordinal != null && ordinal >= 0 ? Math.max(require, ordinal + 1) : require;
            if (calls < needed) {
                failures.add(where + ": " + calls + " call site(s) of " + invoke + " in "
                        + selected.stream().map(m -> m.name + m.desc).toList() + ", need " + needed);
            }
        }
        return 1;
    }

    private static int calls(ClassNode target, String selector, String invoke) {
        List<MethodNode> methods = select(Selector.parse(selector), target);
        assertEquals(1, methods.size(), "one method matches " + selector + " in " + target.name);
        return countCalls(methods.get(0), invoke);
    }

    private static AnnotationNode injector(ClassNode mixin, String handler, String desc) {
        return mixin.methods.stream()
                .filter(m -> m.name.equals(handler))
                .flatMap(m -> annotations(m.visibleAnnotations, m.invisibleAnnotations).stream())
                .filter(a -> a.desc.equals(desc))
                .findFirst().orElseThrow();
    }

    private static List<MethodNode> selected(AnnotationNode injector, ClassNode target) {
        List<MethodNode> selected = new ArrayList<>();
        for (String selector : ClientMixinTargetsTest.<List<String>>value(injector, "method")) {
            selected.addAll(select(Selector.parse(selector), target));
        }
        return selected.stream().filter(m -> countCalls(m, ClientMixinTargetsTest.<String>value(atAnnotations(injector).get(0), "target")) > 0).toList();
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

    private static JsonObject readConfig(String name) {
        try (InputStream in = ClientMixinTargetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " is not on the test classpath");
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
