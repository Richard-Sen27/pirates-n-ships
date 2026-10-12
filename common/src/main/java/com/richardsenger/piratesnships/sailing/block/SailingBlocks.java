package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Sailing blocks: the yard (square sails, docs/design.md §5.2 rule F5a), the boom (SAIL5), the sail winch and the capstan. The cleat
 * of the triangular sails (rule F5b) is registered in {@code sail.TriangularSailContent}.
 */
public final class SailingBlocks {

    /** Blocks that may stand between the two yards of a square sail (besides air): logs, wooden fences, our masts later. */
    public static final TagKey<Block> MASTS = TagKey.create(Registries.BLOCK, Constants.id("masts"));

    /** The yard in every wood (WOOD3); the spruce member keeps the id {@code yard}. Datagen: {@link YardData}. */
    public static final WoodFamily<YardBlock> YARDS = WoodFamily.register("yard", ShipWood.SPRUCE,
            wood -> new YardBlock(wood, WoodFamily.properties(wood).noOcclusion()));
    /** The spruce yard, legacy id {@code yard}. */
    public static final RegistryEntry<Block, YardBlock> YARD = YARDS.entry(ShipWood.SPRUCE);
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<YardBlockEntity>> YARD_BLOCK_ENTITY =
            YARDS.blockEntity("yard", YardBlockEntity::new);
    /**
     * SAIL5: the boom of a triangular sail in every wood; the spruce member keeps the id {@code boom} (as the yard).
     * Datagen: {@link BoomData}.
     */
    public static final WoodFamily<BoomBlock> BOOMS = WoodFamily.register("boom", ShipWood.SPRUCE,
            wood -> new BoomBlock(wood, WoodFamily.properties(wood).noOcclusion()));
    /** The spruce boom, legacy id {@code boom}: for placing one; every member is a boom ({@code instanceof}). */
    public static final RegistryEntry<Block, BoomBlock> BOOM = BOOMS.lead();
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<BoomBlockEntity>> BOOM_BLOCK_ENTITY =
            BOOMS.blockEntity("boom", BoomBlockEntity::new);
    /** WOOD4: the sail winch in every wood; spruce keeps the legacy id {@code sail_winch}. */
    public static final WoodFamily<SailWinchBlock> SAIL_WINCHES = WoodFamily.register("sail_winch", ShipWood.SPRUCE,
            wood -> new SailWinchBlock(wood, WoodFamily.properties(wood).noOcclusion()));
    /** The legacy member ({@code sail_winch}, spruce), for placing one; every member is a winch ({@code instanceof}). */
    public static final RegistryEntry<Block, SailWinchBlock> SAIL_WINCH = SAIL_WINCHES.lead();
    /** WOOD4: the capstan in every wood; dark oak keeps the legacy id {@code capstan}. Strength as before (2.5). */
    public static final WoodFamily<CapstanBlock> CAPSTANS = WoodFamily.register("capstan", ShipWood.DARK_OAK,
            wood -> new CapstanBlock(wood, WoodFamily.properties(wood).strength(2.5f, 3.0f).noOcclusion()));
    /** The legacy member ({@code capstan}, dark oak), for placing one; every member is a capstan ({@code instanceof}). */
    public static final RegistryEntry<Block, CapstanBlock> CAPSTAN = CAPSTANS.lead();

    private SailingBlocks() {
    }

    public static void init() {
        com.richardsenger.piratesnships.sailing.sail.TriangularSailContent.init(); // cleat and rope (F5b)
    }
}
