package com.richardsenger.piratesnships.world.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.world.port.Berth;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads berth markers (art/README.md "Structures (ST1)"): a {@code minecraft:jigsaw} named {@code pirates_n_ships:berth}
 * at the berth's centre at sea level, whose front points along the bow. The marker is read from the template, so it
 * does not matter that placement replaces it with its final state (water).
 */
public final class BerthMarkers {

    public static final ResourceLocation BERTH = Constants.id("berth");

    private BerthMarkers() {
    }

    /**
     * Berths among already transformed jigsaw infos (world positions, rotated states), as
     * {@code StructurePoolElement.getShuffledJigsawBlocks} returns them. Sorted by position for a stable order.
     */
    public static List<Berth> fromJigsaws(List<StructureTemplate.StructureBlockInfo> jigsaws) {
        List<Berth> out = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : jigsaws) {
            if (!isBerth(info.state(), info.nbt())) continue;
            out.add(new Berth(info.pos().immutable(), JigsawBlock.getFrontFacing(info.state())));
        }
        out.sort(Comparator.comparing(Berth::pos));
        return out;
    }

    /**
     * Berths of {@code template} placed at {@code pos} with {@code settings} (rotation, mirror, pivot), transformed the
     * way {@link StructureTemplate#placeInWorld} transforms positions and states. Pool elements never mirror, so world
     * generation goes through {@link #fromJigsaws}; this is the general form and the one JUnit checks.
     */
    public static List<Berth> fromTemplate(StructureTemplate template, BlockPos pos, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> placed = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW, false)) {
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(pos);
            BlockState state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            placed.add(new StructureTemplate.StructureBlockInfo(world, state, info.nbt()));
        }
        return fromJigsaws(placed);
    }

    private static boolean isBerth(BlockState state, CompoundTag nbt) {
        return state.is(Blocks.JIGSAW) && nbt != null && BERTH.toString().equals(nbt.getString("name"));
    }
}
