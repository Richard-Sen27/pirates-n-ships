package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * STR6: a door's approach stays clear where the terrain stands higher than the piece's floor. In a real world a
 * building sits on a slope or a dune and the beard does not always take the ground away; the templates write no plain
 * air (the converter drops it), so whatever the terrain leaves stands in a door's way. The generators write cave air on
 * both sides of every door (on the door's two rows, {@code buildspec.Piece.door_approaches}), which the converter keeps.
 * Here every port piece with a door is placed into a block of dirt that reaches four rows over its foundation, through
 * the processor list the pools name, and each door's sides inside the piece's box must be walkable ({@link DoorAccess};
 * a side beyond the box is the neighbour piece's, checked by the layout GameTests).
 */
public final class DoorAccessGameTests {

    /** The groups whose pieces the world generates as port structures. */
    static final List<String> GROUPS = List.of("village/", "pirate_island/", "navy_outpost/");
    /** Rows of dirt over the template's foundation row (the foundation row included). */
    static final int DIRT_ROWS = 4;
    /** The arena's free height above its floor (EMPTY_48 is 16 high). */
    static final int MAX_HEIGHT = 15;

    private DoorAccessGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(DoorAccessGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 400)
    public static void doorsStayClearInRisingGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(ConnectionsProcessor.LIST)
                .value().list().forEach(settings::addProcessor);
        List<ResourceLocation> ids = manager.listTemplates()
                .filter(id -> id.getNamespace().equals(Constants.MOD_ID) && GROUPS.stream().anyMatch(id.getPath()::startsWith))
                .sorted().toList();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        List<String> problems = new ArrayList<>();
        int pieces = 0;
        int doors = 0;
        for (ResourceLocation id : ids) {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template == null || template.getSize().getY() > MAX_HEIGHT || !hasDoor(template)) continue;
            BoundingBox box = template.getBoundingBox(settings, origin);
            fill(level, box);
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            pieces++;
            doors += (int) BlockPos.betweenClosedStream(box).filter(p -> isLowerDoor(level.getBlockState(p))).count();
            for (String problem : DoorAccess.blockedDoors(level, box, true)) problems.add(id.getPath() + ": " + problem);
        }
        clear(level, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 45, origin.getY() + MAX_HEIGHT - 1,
                origin.getZ() + 45));
        helper.assertTrue(pieces >= 30, "only " + pieces + " pieces with doors placed");
        helper.assertTrue(doors >= 40, "only " + doors + " doors checked");
        helper.assertTrue(problems.isEmpty(), problems.size() + " doors blocked by the ground: " + problems);
        helper.succeed();
    }

    private static boolean isLowerDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
    }

    private static boolean hasDoor(StructureTemplate template) {
        net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            if (palette.getCompound(i).getString("Name").endsWith("_door")) return true;
        }
        return false;
    }

    /** The template's box: dirt in its lowest {@link #DIRT_ROWS} rows, air above (the last piece's blocks go). */
    private static void fill(ServerLevel level, BoundingBox box) {
        clear(level, new BoundingBox(box.minX(), box.minY(), box.minZ(), box.minX() + 45, box.minY() + MAX_HEIGHT - 1, box.minZ() + 45));
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + DIRT_ROWS - 1, box.maxZ())) {
            level.setBlock(p, dirt, Block.UPDATE_CLIENTS);
        }
    }

    private static void clear(ServerLevel level, BoundingBox box) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!level.getBlockState(p).isAir()) {
                level.removeBlockEntity(p);
                level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }
}
