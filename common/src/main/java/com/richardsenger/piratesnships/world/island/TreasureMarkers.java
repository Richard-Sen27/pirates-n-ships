package com.richardsenger.piratesnships.world.island;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads buried-treasure markers (art/README.md "Pirate island (ST2)"): a {@code minecraft:jigsaw} named
 * {@code pirates_n_ships:treasure}, two blocks under the surface of the treasure spot. As with berths, the marker is
 * read from the template, so it does not matter that placement turns it into its final state (sand) first.
 */
public final class TreasureMarkers {

    public static final ResourceLocation TREASURE = Constants.id("treasure");

    private TreasureMarkers() {
    }

    /** Treasure positions among already transformed jigsaw infos (world positions), sorted for a stable order. */
    public static List<BlockPos> fromJigsaws(List<StructureTemplate.StructureBlockInfo> jigsaws) {
        List<BlockPos> out = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : jigsaws) {
            if (isTreasure(info.state(), info.nbt())) out.add(info.pos().immutable());
        }
        out.sort(BlockPos::compareTo);
        return out;
    }

    /** Treasure positions of {@code template} placed at {@code pos} with {@code settings} (rotation, mirror, pivot). */
    public static List<BlockPos> fromTemplate(StructureTemplate template, BlockPos pos, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> placed = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW, false)) {
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(pos);
            placed.add(new StructureTemplate.StructureBlockInfo(world, info.state(), info.nbt()));
        }
        return fromJigsaws(placed);
    }

    private static boolean isTreasure(BlockState state, CompoundTag nbt) {
        return state.is(Blocks.JIGSAW) && nbt != null && TREASURE.toString().equals(nbt.getString("name"));
    }
}
