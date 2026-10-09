package com.richardsenger.piratesnships.ship.decor.wood;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.List;

/**
 * The eleven vanilla woods a wooden block family of ours can come in (RAIL1, docs/design.md §4.8 "Railings
 * (RAIL1/RAIL2)"): each with its planks block, the planks and stripped-log textures a family's child models use, its
 * English name and its sound. {@link #SHIPPED} is the set the families register; RAIL2 switches it to all of them.
 *
 * <p>Everything that touches a vanilla registry ({@link #planks()}, {@link #sound()}) is a method, not a field, so the
 * enum loads in plain JUnit without a bootstrapped game.
 */
public enum ShipWood {
    OAK("oak", "Oak"),
    SPRUCE("spruce", "Spruce"),
    BIRCH("birch", "Birch"),
    JUNGLE("jungle", "Jungle"),
    ACACIA("acacia", "Acacia"),
    DARK_OAK("dark_oak", "Dark Oak"),
    MANGROVE("mangrove", "Mangrove"),
    CHERRY("cherry", "Cherry"),
    BAMBOO("bamboo", "Bamboo"),
    CRIMSON("crimson", "Crimson"),
    WARPED("warped", "Warped");

    /**
     * The woods every family registers, in creative-tab order. RAIL1 ships oak only; RAIL2 makes this
     * {@code List.of(values())} and nothing else changes.
     */
    public static final List<ShipWood> SHIPPED = List.of(OAK);

    private final String id;
    private final String displayName;

    ShipWood(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The wood's id as vanilla spells it in block names: {@code oak}, {@code dark_oak}, {@code crimson}. */
    public String id() {
        return id;
    }

    /** The English name that leads a family member's name: "Oak", "Dark Oak". */
    public String displayName() {
        return displayName;
    }

    /** Whether the wood burns (the nether woods do not). */
    public boolean flammable() {
        return this != CRIMSON && this != WARPED;
    }

    /** Whether {@link #SHIPPED} holds this wood. */
    public boolean shipped() {
        return SHIPPED.contains(this);
    }

    /** The texture of the wood's planks: {@code minecraft:block/oak_planks}. */
    public ResourceLocation planksTexture() {
        return ResourceLocation.withDefaultNamespace("block/" + id + "_planks");
    }

    /**
     * The side texture of the wood's stripped log: {@code minecraft:block/stripped_oak_log}; the bamboo block and the
     * nether stems have their own names.
     */
    public ResourceLocation logTexture() {
        return ResourceLocation.withDefaultNamespace("block/" + switch (this) {
            case BAMBOO -> "stripped_bamboo_block";
            case CRIMSON, WARPED -> "stripped_" + id + "_stem";
            default -> "stripped_" + id + "_log";
        });
    }

    /** The wood's planks block (its recipes and map colour come from it). */
    public Block planks() {
        return switch (this) {
            case OAK -> Blocks.OAK_PLANKS;
            case SPRUCE -> Blocks.SPRUCE_PLANKS;
            case BIRCH -> Blocks.BIRCH_PLANKS;
            case JUNGLE -> Blocks.JUNGLE_PLANKS;
            case ACACIA -> Blocks.ACACIA_PLANKS;
            case DARK_OAK -> Blocks.DARK_OAK_PLANKS;
            case MANGROVE -> Blocks.MANGROVE_PLANKS;
            case CHERRY -> Blocks.CHERRY_PLANKS;
            case BAMBOO -> Blocks.BAMBOO_PLANKS;
            case CRIMSON -> Blocks.CRIMSON_PLANKS;
            case WARPED -> Blocks.WARPED_PLANKS;
        };
    }

    /** The sound of the wood's planks. */
    public SoundType sound() {
        return switch (this) {
            case CHERRY -> SoundType.CHERRY_WOOD;
            case BAMBOO -> SoundType.BAMBOO_WOOD;
            case CRIMSON, WARPED -> SoundType.NETHER_WOOD;
            default -> SoundType.WOOD;
        };
    }

    /** The vanilla wood type (RAIL3: the railing gate swings with that wood's fence-gate sounds). */
    public WoodType woodType() {
        return switch (this) {
            case OAK -> WoodType.OAK;
            case SPRUCE -> WoodType.SPRUCE;
            case BIRCH -> WoodType.BIRCH;
            case JUNGLE -> WoodType.JUNGLE;
            case ACACIA -> WoodType.ACACIA;
            case DARK_OAK -> WoodType.DARK_OAK;
            case MANGROVE -> WoodType.MANGROVE;
            case CHERRY -> WoodType.CHERRY;
            case BAMBOO -> WoodType.BAMBOO;
            case CRIMSON -> WoodType.CRIMSON;
            case WARPED -> WoodType.WARPED;
        };
    }
}
