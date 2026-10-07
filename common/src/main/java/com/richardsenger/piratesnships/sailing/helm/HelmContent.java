package com.richardsenger.piratesnships.sailing.helm;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Registrations of wheel steering (HELM1): the helm's block entity (for the helm block of {@code ship.assembly}), and
 * {@link #WHEEL_MODEL}, a block that is never placed in survival and only carries the hand-made wheel model
 * ({@code block/helm_wheel}) into the baked model set, so {@code client/HelmWheelRenderer} can draw it through its block
 * state, the way {@code SwivelGunRenderer} draws its parts. The common code has no loader-neutral way to bake a
 * stand-alone model; a platform hook for that would make this block unnecessary.
 */
public final class HelmContent {

    public static final RegistryEntry<Block, Block> WHEEL_MODEL = ModRegistry.block("helm_wheel",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD)
                    .noOcclusion().noCollission().noLootTable()));

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<HelmBlockEntity>> HELM_ENTITY =
            ModRegistry.blockEntity("helm", HelmBlockEntity::new, AssemblyContent.HELM);

    private HelmContent() {
    }

    /** Loads the class so the entries above are registered. Called from {@code registerContent()}. */
    public static void init() {
    }
}
