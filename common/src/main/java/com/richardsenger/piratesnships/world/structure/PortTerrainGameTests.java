package com.richardsenger.piratesnships.world.structure;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.island.IslandKeys;
import com.richardsenger.piratesnships.world.outpost.OutpostKeys;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.world.port.PortService;
import com.richardsenger.piratesnships.world.village.VillageKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * STR8 on placed blocks: doors and trails on the ground the human found them on.
 *
 * <ul>
 *     <li>{@link #doorsOnRaisedSnowyGroundAreCleared}: every port piece with a door stands with the ground beside it
 *     raised to its door row and snowed over (the human's screenshots: a snow-topped grass block squarely in the cell
 *     before a hut's door). Before {@link DoorClearing} doors are blocked beyond the pieces' boxes; after it none.</li>
 *     <li>{@code villageOnBumpySnowyShore}, {@code islandOnBumpySnowyShore}, {@code outpostOnBumpySnowyShore}: the port
 *     planned on {@link PortSiteGameTests}' bumpy, rising shore and placed on the same ground built of grass, dirt and
 *     snow: every trail piece's bed lies in one row (its template's row 0 where the plan put it, not block by block on
 *     the bumps), every door can be walked through, the port registers; the region goes to
 *     {@code <structure>_bumpy_<seed>.schem} for the renders.</li>
 * </ul>
 */
public final class PortTerrainGameTests {

    static final int SEA_Y = PortSiteGameTests.SEA_Y;
    static final int SHORE_Z = 70;
    static final int SITE_X = 80;
    static final int SITE_Z = SHORE_Z + 9;
    static final int SIZE = 160;
    static final int HEIGHT = 40;
    static final int MARGIN = 8;
    /** The arena's free height above its floor (EMPTY_48 is 16 high). */
    static final int MAX_HEIGHT = 15;

    private PortTerrainGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PortTerrainGameTests.class);
    }

    // --- doors --------------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_48, timeoutTicks = 600)
    public static void doorsOnRaisedSnowyGroundAreCleared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(ConnectionsProcessor.LIST)
                .value().list().forEach(settings::addProcessor);
        List<ResourceLocation> ids = manager.listTemplates()
                .filter(id -> id.getNamespace().equals(Constants.MOD_ID) && DoorAccessGameTests.GROUPS.stream().anyMatch(id.getPath()::startsWith))
                .sorted().toList();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BoundingBox area = new BoundingBox(origin.getX() - 3, origin.getY(), origin.getZ() - 3, origin.getX() + 40,
                origin.getY() + MAX_HEIGHT - 2, origin.getZ() + 40);
        BoundingBox column = new BoundingBox(area.minX(), level.getMinBuildHeight(), area.minZ(), area.maxX(), level.getMaxBuildHeight(),
                area.maxZ());
        int pieces = 0;
        int blockedBefore = 0;
        List<String> blockedAfter = new ArrayList<>();
        for (ResourceLocation id : ids) {
            StructureTemplate template = manager.get(id).orElse(null);
            if (template == null || template.getSize().getY() > MAX_HEIGHT - 3 || !hasDoor(template)) continue;
            BoundingBox box = template.getBoundingBox(settings, origin);
            if (box.maxX() > area.maxX() - 3 || box.maxZ() > area.maxZ() - 3) continue;
            clear(level, area);
            raisedSnowyGround(level, box);
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            pieces++;
            blockedBefore += DoorAccess.blockedDoors(level, box, false).size();
            PoolElementStructurePiece piece = new PoolElementStructurePiece(manager,
                    StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID), origin, 0, Rotation.NONE,
                    box, LiquidSettings.APPLY_WATERLOGGING);
            DoorClearing.clear(level, column, List.of(piece), manager);
            for (String problem : DoorAccess.blockedDoors(level, box, false)) blockedAfter.add(id.getPath() + ": " + problem);
        }
        clear(level, area);
        Constants.LOG.info("STR8 doors on raised snowy ground: {} pieces, {} door sides blocked before the clearing, {} after",
                pieces, blockedBefore, blockedAfter.size());
        helper.assertTrue(pieces >= 20, "only " + pieces + " pieces with doors placed");
        helper.assertTrue(blockedBefore > 0, "the raised ground blocks doors beyond the pieces' boxes");
        helper.assertTrue(blockedAfter.isEmpty(), blockedAfter.size() + " doors still blocked: " + blockedAfter);
        helper.succeed();
    }

    /**
     * Inside the box dirt up to the foundation row; outside (three blocks round it) dirt on the foundation row, a snowy
     * grass block on the door row above it and a snow layer of two on top.
     */
    private static void raisedSnowyGround(ServerLevel level, BoundingBox box) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, true);
        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2);
        int y0 = box.minY();
        for (int x = box.minX() - 3; x <= box.maxX() + 3; x++) {
            for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
                level.setBlock(new BlockPos(x, y0, z), dirt, Block.UPDATE_CLIENTS);
                if (box.isInside(new BlockPos(x, y0, z))) continue;
                level.setBlock(new BlockPos(x, y0 + 1, z), grass, Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, y0 + 2, z), snow, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static boolean hasDoor(StructureTemplate template) {
        for (Block block : List.of(Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_DOOR, Blocks.IRON_DOOR, Blocks.BIRCH_DOOR,
                Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR)) {
            if (!template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block, false).isEmpty()) return true;
        }
        net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            if (palette.getCompound(i).getString("Name").endsWith("_door")) return true;
        }
        return false;
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

    // --- whole ports on a bumpy, snowy shore --------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 600, batch = "pirates_n_ships_port_terrain")
    public static void villageOnBumpySnowyShore(GameTestHelper helper) {
        placeOnBumpyShore(helper, VillageKeys.SEAFARER_VILLAGE, PortKind.SEAFARER_VILLAGE, 7L);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 600, batch = "pirates_n_ships_port_terrain")
    public static void islandOnBumpySnowyShore(GameTestHelper helper) {
        placeOnBumpyShore(helper, IslandKeys.PIRATE_ISLAND, PortKind.PIRATE_ISLAND, 11L);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_160_DEEP, timeoutTicks = 600, batch = "pirates_n_ships_port_terrain")
    public static void outpostOnBumpySnowyShore(GameTestHelper helper) {
        placeOnBumpyShore(helper, OutpostKeys.NAVY_OUTPOST, PortKind.NAVY_OUTPOST, 2024L);
    }

    private static void placeOnBumpyShore(GameTestHelper helper, ResourceKey<Structure> key, PortKind kind, long seed) {
        ServerLevel level = helper.getLevel();
        PortStructure structure = PortSiteGameTests.structure(level, key);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int seaY = origin.getY() + SEA_Y;
        BlockPos site = new BlockPos(origin.getX() + SITE_X, seaY, origin.getZ() + SITE_Z);
        BlockPos planSite = new BlockPos(PortSiteGameTests.PLAN_X, seaY, PortSiteGameTests.PLAN_SHORE_Z + 9);
        int dx = site.getX() - planSite.getX();
        int dz = site.getZ() - planSite.getZ();
        buildBumpyShore(helper, seaY, dx, dz);
        ChunkGenerator bumpy = PortSiteGameTests.bumpyGenerator(level, (x, z) -> PortSiteGameTests.bumpySurface(seaY, x, z) + 1);
        PortStructure.Terrain terrain = (x, z) -> z < PortSiteGameTests.PLAN_SHORE_Z ? seaY - 4 : PortSiteGameTests.bumpySurface(seaY, x, z) + 1;
        List<StructurePiece> pieces = PortSiteGameTests.plan(helper, level, structure, bumpy, planSite, seaY, terrain, seed).orElse(null);
        helper.assertTrue(pieces != null, key.location() + " seed " + seed + ": the bumpy shore gives a port");
        pieces.forEach(p -> {
            p.move(dx, 0, dz);
            if (p instanceof PoolElementStructurePiece pe) PlanRepair.shiftJunctions(pe, 0, dx, dz);
        });
        BoundingBox arena = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + SIZE - 1,
                origin.getY() + HEIGHT - 1, origin.getZ() + SIZE - 1);
        pieces.removeIf(p -> !insideXZ(arena, p.getBoundingBox()));
        StructureStart start = new StructureStart(structure, new ChunkPos(site), 0, new PiecesContainer(pieces));
        clearCeiling(helper);
        place(level, start);

        // every trail piece lies level: its template's row 0 stands on its bed row
        StructureTemplateManager templates = level.getStructureManager();
        int trails = 0;
        for (StructurePiece piece : pieces) {
            if (!PortSiteGameTests.isTrail(piece) || !(piece instanceof PoolElementStructurePiece p)) continue;
            trails++;
            int[] counts = bedCells(level, p, templates);
            helper.assertTrue(counts[0] >= 3 && counts[1] * 10 <= counts[0], key.location() + ": " + PortSiteGameTests.name(piece) + " at "
                    + piece.getBoundingBox() + " lies level (" + counts[1] + " of " + counts[0] + " bed cells off its row)");
        }
        helper.assertTrue(trails >= 2, key.location() + ": trail pieces placed: " + trails);

        Port port = PortRegistry.get(level.getServer()).index()
                .byId(PortService.portId(kind, PortService.centreOf(new PiecesContainer(pieces)))).orElse(null);
        helper.assertTrue(port != null, key.location() + ": the port is registered");

        BoundingBox b = start.getBoundingBox();
        BoundingBox region = new BoundingBox(b.minX() - MARGIN, Math.max(level.getMinBuildHeight(), b.minY() - MARGIN),
                b.minZ() - MARGIN, b.maxX() + MARGIN, Math.min(level.getMaxBuildHeight() - 1, b.maxY() + MARGIN), b.maxZ() + MARGIN);
        try {
            RegionSchematic.write(level, region, origin, StructureLayoutGameTests.dir().resolve(key.location().getPath() + "_bumpy_" + seed + ".schem"));
        } catch (IOException e) {
            throw new IllegalStateException("could not write the region", e);
        }
        // STR9: every one-row step between trail pieces carries half slabs, and no step is two rows high
        List<String> bareCells = new ArrayList<>();
        int[] slabs = slabSteps(level, pieces, templates, bareCells);
        helper.assertTrue(slabs[2] == 0, key.location() + ": " + slabs[2] + " one-row step cells without a slab: " + bareCells);
        helper.assertTrue(TrailSlabs.steps(pieces, templates, 2).isEmpty() && TrailSlabs.steps(pieces, templates, 3).isEmpty(),
                key.location() + ": a step of two rows or more");
        helper.assertTrue(slabs[0] == 0 || slabs[1] > 0, key.location() + ": " + slabs[0] + " one-row steps, no slab on any");
        List<String> blocked = DoorAccess.blockedDoors(level, start.getBoundingBox());
        Constants.LOG.info("STR8 {} on the bumpy snowy shore, seed {}: {} pieces, {} trail pieces, {} blocked doors; STR9: {} one-row steps, "
                + "{} slabs", key.location(), seed, pieces.size(), trails, blocked.size(), slabs[0], slabs[1]);
        helper.assertTrue(blocked.isEmpty(), key.location() + ": " + blocked.size() + " blocked doors: " + blocked);
        helper.succeed();
    }

    /**
     * STR9 on placed blocks: {steps, slabs, bare}: the one-row joints between trail pieces ({@link TrailSlabs#steps}), the
     * slabs on their lower rows, and the cells of a joint a walker would have to jump (full paving with air above it and
     * a full block beyond with room above: a step the slab rule should have halved).
     */
    static int[] slabSteps(ServerLevel level, List<StructurePiece> pieces, StructureTemplateManager templates, List<String> bareCells) {
        int steps = 0;
        int slabs = 0;
        int bare = 0;
        for (TrailSlabs.Step step : TrailSlabs.steps(pieces, templates, 1)) {
            steps++;
            for (BlockPos cell : step.cells()) {
                if (level.getBlockState(cell.atY(step.row() + 1)).getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
                    slabs++;
                } else if (TrailSlabs.slabAt(level, cell, step.row(), step.up(), PortKind.SEAFARER_VILLAGE) != null) {
                    bare++;
                    bareCells.add(cell.atY(step.row()).toShortString() + " " + level.getBlockState(cell.atY(step.row())).getBlock()
                            + " toward " + step.up());
                }
            }
        }
        return new int[] {steps, slabs, bare};
    }

    /**
     * The bumpy shore of {@link PortSiteGameTests#bumpySurface} in the arena (plan column = arena column minus
     * ({@code dx}, {@code dz})): stone under the sea and water up to its surface; on land stone, dirt, a snowy grass
     * block on top and a snow layer of one or two.
     */
    private static void buildBumpyShore(GameTestHelper helper, int seaY, int dx, int dz) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, true);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int ax = origin.getX() + x;
                int az = origin.getZ() + z;
                int surface = PortSiteGameTests.bumpySurface(seaY, ax - dx, az - dz);
                boolean sea = az - dz < PortSiteGameTests.PLAN_SHORE_Z;
                boolean rim = x == 0 || x == SIZE - 1 || z == 0;
                for (int y = origin.getY() + 1; y <= Math.max(seaY, surface) + 1; y++) {
                    BlockState state;
                    if (sea) state = y <= seaY - 5 || rim && y <= seaY ? stone : y <= seaY ? water : null;
                    else if (y < surface - 2) state = stone;
                    else if (y < surface) state = dirt;
                    else if (y == surface) state = grass;
                    else state = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + ((ax * 31 + az * 17) & 1));
                    if (state != null) level.setBlock(new BlockPos(ax, y, az), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean insideXZ(BoundingBox area, BoundingBox box) {
        return box.minX() >= area.minX() && box.maxX() <= area.maxX() && box.minZ() >= area.minZ() && box.maxZ() <= area.maxZ();
    }

    private static void clearCeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= SIZE; x++) {
            for (int z = -1; z <= SIZE; z++) {
                for (int y = HEIGHT - 1; y <= HEIGHT + 2; y++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    if (level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Places every chunk of {@code start}, as {@code /place structure} does (its {@code afterPlace} included). */
    private static void place(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(),
                level.getChunkSource().getGenerator(), level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
    }

    /**
     * {count, off}: the template's row 0 cells of a levelled trail piece (jigsaws and cave air aside) and how many of
     * them do not hold the template's block on the piece's bed row ({@code box.minY() - 1}).
     */
    private static int[] bedCells(ServerLevel level, PoolElementStructurePiece piece, StructureTemplateManager templates) {
        StructureTemplate template = DoorClearing.template(piece, templates).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
        net.minecraft.nbt.CompoundTag saved = template.save(new net.minecraft.nbt.CompoundTag());
        net.minecraft.nbt.ListTag palette = saved.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag blocks = saved.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        BlockPos at = piece.getPosition().below();
        int count = 0;
        int off = 0;
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.nbt.CompoundTag block = blocks.getCompound(i);
            net.minecraft.nbt.ListTag pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            if (pos.getInt(1) != 0) continue;
            String name = palette.getCompound(block.getInt("state")).getString("Name");
            if (name.equals("minecraft:jigsaw") || name.equals("minecraft:cave_air") || name.equals("minecraft:air")) continue;
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, new BlockPos(pos.getInt(0), 0, pos.getInt(2))).offset(at);
            count++;
            String here = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(world).getBlock()).toString();
            if (!here.equals(name)) off++;
        }
        return new int[] {count, off};
    }
}
