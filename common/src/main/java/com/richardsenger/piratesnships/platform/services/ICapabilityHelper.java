package com.richardsenger.piratesnships.platform.services;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Exposes our block entities to other mods' automation (pipes, conveyors, ...) through the loader's item transfer
 * API: the item handler capability on NeoForge, the transfer API ({@code ItemStorage.SIDED}) on Fabric. Vanilla
 * hoppers already work through {@link Container} and need none of this.
 *
 * <p>The loader wraps the vanilla container view, so the block entity's own rules still apply: a
 * {@link WorldlyContainer} is wrapped per side ({@code getSlotsForFace}, {@code canPlaceItemThroughFace},
 * {@code canTakeItemThroughFace}; a {@code null} side means "no particular face", all slots), a plain
 * {@link Container} through {@code canPlaceItem}, {@code removeItem} and {@code setChanged}.
 *
 * <pre>{@code
 * // in registerContent(); PANTRY_BLOCK_ENTITY's block entity implements WorldlyContainer
 * Services.CAPABILITIES.registerBlockContainer(CrewContent.PANTRY_BLOCK_ENTITY);
 * Services.CAPABILITIES.registerBlockContainer(CargoContainers.BLOCK_ENTITY, (be, side) -> side == Direction.DOWN ? null : be);
 * }</pre>
 */
public interface ICapabilityHelper {

    /**
     * Exposes the container that {@code view} returns for a block entity and side (or {@code null} for "nothing on
     * this side"). Call once per type during mod initialization ({@code registerContent()}); the type supplier is
     * resolved later, when the loader collects capabilities.
     */
    <T extends BlockEntity> void registerBlockContainer(Supplier<? extends BlockEntityType<T>> type, ContainerView<? super T> view);

    /** Shorthand for a block entity that is its own container on every side. */
    default <T extends BlockEntity & Container> void registerBlockContainer(Supplier<? extends BlockEntityType<T>> type) {
        registerBlockContainer(type, (be, side) -> be);
    }

    /** The vanilla container a block entity exposes on {@code side}. */
    @FunctionalInterface
    interface ContainerView<T> {
        @Nullable Container get(T blockEntity, @Nullable Direction side);
    }
}
