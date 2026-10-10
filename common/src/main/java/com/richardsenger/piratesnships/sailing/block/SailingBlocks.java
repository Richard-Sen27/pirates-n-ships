package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Sailing blocks: the yard (square sails, docs/design.md §5.2 rule F5a), the sail winch and the capstan. The cleat
 * of the triangular sails (rule F5b) is registered in {@code sail.TriangularSailContent}.
 */
public final class SailingBlocks {

    /** Blocks that may stand between the two yards of a square sail (besides air): logs, wooden fences, our masts later. */
    public static final TagKey<Block> MASTS = TagKey.create(Registries.BLOCK, Constants.id("masts"));

    public static final RegistryEntry<Block, YardBlock> YARD = ModRegistry.blockWithItem("yard",
            () -> new YardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<YardBlockEntity>> YARD_BLOCK_ENTITY =
            ModRegistry.blockEntity("yard", YardBlockEntity::new, YARD);
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
