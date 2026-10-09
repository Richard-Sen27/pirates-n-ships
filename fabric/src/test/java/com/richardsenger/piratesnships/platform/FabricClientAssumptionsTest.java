package com.richardsenger.piratesnships.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Headless checks of what FAB2's client wiring assumes about vanilla and Fabric API (read as bytecode, so no client
 * class is initialised).
 */
class FabricClientAssumptionsTest {

    /**
     * Render bounding boxes: NeoForge culls each block entity by its renderer's {@code getRenderBoundingBox}, which is
     * why the flag, sail, rope, cannon and helm renderers in common override it with their full reach. Vanilla has no
     * such method and culls block entities only per chunk section: {@code LevelRenderer} draws every block entity of
     * every visible section and of the global set and never tests a frustum per block entity. So on Fabric those methods
     * are unused and a large renderer is never culled earlier than its section (the same or later than on NeoForge);
     * Sable draws the block entities of ship sections without a frustum test on both loaders. If this test fails, a
     * Fabric API or vanilla change added per-block-entity culling and the boxes need forwarding.
     */
    @Test
    void vanillaCullsBlockEntitiesPerSectionOnly() {
        ClassNode renderer = read("net/minecraft/client/renderer/blockentity/BlockEntityRenderer");
        assertTrue(renderer.methods.stream().noneMatch(m -> m.name.equals("getRenderBoundingBox")),
                "vanilla BlockEntityRenderer has no getRenderBoundingBox");
        ClassNode level = read("net/minecraft/client/renderer/LevelRenderer");
        for (MethodNode method : level.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/culling/Frustum")
                        && call.name.equals("isVisible")) {
                    throw new AssertionError("LevelRenderer#" + method.name + " tests the frustum per box");
                }
            }
        }
        MethodNode renderLevel = level.methods.stream().filter(m -> m.name.equals("renderLevel")).findFirst().orElseThrow();
        boolean readsGlobal = false;
        boolean readsSections = false;
        for (AbstractInsnNode insn : renderLevel.instructions) {
            if (insn instanceof FieldInsnNode field && field.name.equals("globalBlockEntities")) readsGlobal = true;
            if (insn instanceof MethodInsnNode call && call.name.equals("getRenderableBlockEntities")) readsSections = true;
        }
        assertTrue(readsGlobal && readsSections, "renderLevel draws the section and global block entities");
    }

    /**
     * {@link FabricClientSetup#ADDITIONAL_MODEL_VARIANT} is the variant Fabric API stores added models under
     * ({@code ModelLoadingConstants.RESOURCE_SPECIAL_VARIANT}); read from the class file, as the constant is in impl.
     */
    @Test
    void additionalModelVariantMatchesFabricApi() {
        ClassNode constants = read("net/fabricmc/fabric/impl/client/model/loading/ModelLoadingConstants");
        String variant = constants.fields.stream().filter(f -> f.name.equals("RESOURCE_SPECIAL_VARIANT"))
                .map(f -> (String) f.value).findFirst().orElse(null);
        if (variant == null) {
            // not a compile-time constant field value: find the string pushed in the static initialiser
            MethodNode clinit = constants.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
            for (AbstractInsnNode insn : clinit.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) variant = s;
            }
        }
        assertEquals(FabricClientSetup.ADDITIONAL_MODEL_VARIANT, variant);
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = FabricClientAssumptionsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertTrue(in != null, internalName + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
