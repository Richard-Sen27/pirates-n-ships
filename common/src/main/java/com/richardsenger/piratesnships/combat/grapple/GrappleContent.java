package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.sailing.rope.RopeAnchorBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registered content of the grappling hook (docs/design.md §8.3): the thrown hook entity, the mooring ring (GR1),
 * the rope rider (GR2) and the {@code grapple_loaded} component of a hook-loaded launcher (GR3).
 * The item itself is {@code combat.content.CombatContent#GRAPPLING_HOOK}, a {@link GrapplingHookItem}.
 */
public final class GrappleContent {

    /** Tracked from 10 chunks and updated every other tick, so the rope to a moving ship stays in view and close. */
    public static final RegistryEntry<EntityType<?>, EntityType<GrapplingHookEntity>> HOOK = ModRegistry.entity("grappling_hook",
            () -> EntityType.Builder.<GrapplingHookEntity>of(GrapplingHookEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).noSummon().clientTrackingRange(10).updateInterval(2));

    /**
     * The invisible handle a player hangs on while sliding along a rope (GR2): not saved, tracked like the hook and
     * updated every tick so the hanging player moves smoothly.
     */
    public static final RegistryEntry<EntityType<?>, EntityType<RopeRiderEntity>> ROPE_RIDER = ModRegistry.entity("rope_rider",
            () -> EntityType.Builder.<RopeRiderEntity>of(RopeRiderEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).noSummon().noSave().fireImmune().clientTrackingRange(10).updateInterval(1));

    /** A small iron ring on a plate: a sure target for hooks and a tie-off for the rope (see {@link MooringRingBlock}). */
    public static final RegistryEntry<Block, MooringRingBlock> MOORING_RING = ModRegistry.blockWithItem("mooring_ring",
            () -> new MooringRingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f)
                    .sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** The ring's rope lines (RP1): a plain rope anchor block entity. */
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<RopeAnchorBlockEntity>> MOORING_RING_BLOCK_ENTITY =
            ModRegistry.blockEntity("mooring_ring",
                    (pos, state) -> new RopeAnchorBlockEntity(GrappleContent.MOORING_RING_BLOCK_ENTITY.get(), pos, state), MOORING_RING);

    /**
     * Present on a musket that holds a grappling hook (GR3, {@link LoadedHook}); removed when it fires.
     * Saved and synced to the client (the musket's {@code musket_hook} model reads it).
     */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<LoadedHook>> LOADED_HOOK = ModRegistry.dataComponent(
            "grapple_loaded", b -> b.persistent(LoadedHook.CODEC).networkSynchronized(LoadedHook.STREAM_CODEC));

    /**
     * Blocks a flying hook slips off instead of latching (GR4: "non-solid" surfaces such as leaves and glass panes;
     * water never stops the hook). Filled by datagen, extendable by data packs.
     */
    public static final TagKey<Block> HOOK_SLIPS = TagKey.create(Registries.BLOCK, Constants.id("grapple_slips"));

    private GrappleContent() {
    }

    public static void init() {
        // class load registers the entries
    }

    /** The hook sitting in {@code launcher}, or {@code null}. */
    public static @Nullable LoadedHook loadedHook(ItemStack launcher) {
        return launcher.get(LOADED_HOOK.get());
    }

    public static boolean isHookLoaded(ItemStack launcher) {
        return launcher.has(LOADED_HOOK.get());
    }
}
