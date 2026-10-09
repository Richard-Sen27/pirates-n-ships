package com.richardsenger.piratesnships.apparel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.apparel.client.ApparelClient;
import com.richardsenger.piratesnships.apparel.client.CoatArmorModel;
import com.richardsenger.piratesnships.platform.NeoForgeArmorModels;
import com.richardsenger.piratesnships.platform.NeoForgeClientSetup;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ART9: the coats' tails reach the game only through NeoForge's {@code IClientItemExtensions#getHumanoidArmorModel},
 * which the GameTest server never calls (it loads no client code). This is the headless guard: our extension really
 * overrides NeoForge's method (signature pinned against the NeoForge classes the mod runs with), it hands NeoForge the
 * provider's model for the registered items, the client setup forwards {@code ClientEvents.armorModels()} on
 * {@code RegisterClientExtensionsEvent}, and the apparel client registers the coat model for both coats.
 */
class CoatArmorModelExtensionTest {

    @Test
    void extensionOverridesNeoForgesArmourModelHook() throws NoSuchMethodException {
        Method hook = IClientItemExtensions.class.getMethod("getHumanoidArmorModel", LivingEntity.class,
                net.minecraft.world.item.ItemStack.class, EquipmentSlot.class, HumanoidModel.class);
        assertTrue(IClientItemExtensions.class.isAssignableFrom(NeoForgeArmorModels.Extension.class));
        Method mine = NeoForgeArmorModels.Extension.class.getMethod(hook.getName(), hook.getParameterTypes());
        assertEquals(NeoForgeArmorModels.Extension.class, mine.getDeclaringClass(), "getHumanoidArmorModel is not overridden");
        assertEquals(hook.getReturnType(), mine.getReturnType());
        // NeoForge's generic hook (what HumanoidArmorLayer calls) still goes through it and copies the pose
        Method generic = NeoForgeArmorModels.Extension.class.getMethod("getGenericArmorModel", hook.getParameterTypes());
        assertEquals(IClientItemExtensions.class, generic.getDeclaringClass(), "getGenericArmorModel must stay NeoForge's");
    }

    @Test
    void registerHandsNeoForgeTheProvidersModelPerItem() {
        HumanoidModel<?> original = new HumanoidModel<LivingEntity>(
                LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.0F)), 64, 32).bakeRoot());
        CoatArmorModel coat = new CoatArmorModel(CoatArmorModel.createLayer().bakeRoot());
        ClientEvents.ArmorModelProvider provider = (wearer, stack, slot, orig) -> slot == EquipmentSlot.CHEST ? coat : orig;
        Supplier<Item> none = () -> null;
        List<IClientItemExtensions> extensions = new ArrayList<>();
        List<Integer> itemCounts = new ArrayList<>();
        NeoForgeArmorModels.register(List.of(new ClientEvents.ArmorModel(provider, List.of(none, none))), (ext, items) -> {
            extensions.add(ext);
            itemCounts.add(items.length);
        });
        assertEquals(1, extensions.size(), "one extension per registration");
        assertEquals(List.of(2), itemCounts, "for every item of it");
        IClientItemExtensions ext = extensions.getFirst();
        assertSame(coat, ext.getHumanoidArmorModel(null, null, EquipmentSlot.CHEST, original), "the coat model for the chest");
        assertSame(original, ext.getHumanoidArmorModel(null, null, EquipmentSlot.LEGS, original), "vanilla's elsewhere");
    }

    @Test
    void clientSetupForwardsArmourModelsToNeoForge() throws IOException {
        ClassNode setup = read(NeoForgeClientSetup.class);
        assertTrue(calls(setup, Type.of(NeoForgeArmorModels.class), "register"), "NeoForgeClientSetup never calls NeoForgeArmorModels.register");
        assertTrue(calls(setup, Type.of(ClientEvents.class), "armorModels"), "NeoForgeClientSetup never reads ClientEvents.armorModels()");
        assertTrue(references(setup, "net/neoforged/neoforge/client/extensions/common/RegisterClientExtensionsEvent"),
                "NeoForgeClientSetup does not listen to RegisterClientExtensionsEvent");
    }

    /** {@code ApparelClient.init()} registers the coat model for the officer's coat and the captain's coat, in one call. */
    @Test
    void apparelClientRegistersTheCoatModelForBothCoats() throws IOException {
        ClassNode client = read(ApparelClient.class);
        MethodNode init = client.methods.stream().filter(m -> m.name.equals("init")).findFirst().orElseThrow();
        List<String> coats = new ArrayList<>();
        boolean registered = false;
        for (AbstractInsnNode insn : init.instructions) {
            if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.owner.equals(Type.of(ApparelContent.class))) {
                coats.add(f.name);
            }
            if (insn instanceof MethodInsnNode m && m.owner.equals(Type.of(ClientEvents.class)) && m.name.equals("registerArmorModel")) {
                registered = true;
                assertEquals(List.of("OFFICERS_COAT", "CAPTAINS_COAT"), coats, "the items passed to registerArmorModel");
            }
        }
        assertTrue(registered, "ApparelClient.init() never calls ClientEvents.registerArmorModel");
        assertTrue(calls(client, Type.of(ClientEvents.class), "registerModelLayer"), "the coat's model layer is not registered");
    }

    private static final class Type {
        static String of(Class<?> c) {
            return c.getName().replace('.', '/');
        }
    }

    private static ClassNode read(Class<?> c) throws IOException {
        try (InputStream in = c.getClassLoader().getResourceAsStream(Type.of(c) + ".class")) {
            assertNotNull(in, "class file of " + c);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static boolean calls(ClassNode node, String owner, String name) {
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }

    private static boolean references(ClassNode node, String internalName) {
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.LdcInsnNode ldc && ldc.cst instanceof org.objectweb.asm.Type t
                        && t.getInternalName().equals(internalName)) return true;
            }
        }
        return false;
    }
}
