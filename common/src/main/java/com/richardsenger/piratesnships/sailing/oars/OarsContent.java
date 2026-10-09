package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry entries of the oars (OAR1, docs/design.md §5.3b): the oar bench per shipped wood ({@code oak_oar_bench}), the
 * oar (the item a rower holds; craftable for the look, it does nothing in a player's hand) and the rail rower's
 * invisible seat.
 */
public final class OarsContent {

    public static final WoodFamily<OarBenchBlock> BENCHES = WoodFamily.register("oar_bench",
            wood -> new OarBenchBlock(WoodFamily.properties(wood).noOcclusion().pushReaction(PushReaction.NORMAL)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    public static final RegistryEntry<Item, Item> OAR = ModRegistry.item("oar", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryEntry<EntityType<?>, EntityType<OarSeat>> OAR_SEAT = ModRegistry.entity("oar_seat",
            () -> EntityType.Builder.<OarSeat>of(OarSeat::new, MobCategory.MISC)
                    .sized(0.25f, 0.01f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1));

    private OarsContent() {
    }

    public static void init() {
    }
}
