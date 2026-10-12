package com.richardsenger.piratesnships.core.save;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.boarding.BoardingContent;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlock;
import com.richardsenger.piratesnships.combat.boarding.BoardingPlankBlockEntity;
import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.oars.OarBenchBlock;
import com.richardsenger.piratesnships.ship.decor.NameBoardBlock;
import com.richardsenger.piratesnships.ship.decor.NameplateBlockEntity;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.rigging.ChainplateBlock;
import com.richardsenger.piratesnships.ship.rigging.MastHoopBlock;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetCellBlockEntity;
import com.richardsenger.piratesnships.ship.rigging.RatlineNetItem;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The guard of the save-compatibility note (SAV1, {@code docs/release/save-compatibility.md}): the oldest block states and
 * block-entity tags the code still reads, written straight into the chunk as a chunk load reads them, each converting as
 * the note says.
 *
 * <p>{@link #writeSaved} parses the palette entry with {@link BlockState#CODEC} (what a chunk section's palette uses:
 * properties the block no longer has are dropped, missing ones take the block's default) and sets it into the section
 * with no placement, no neighbour update and no scheduled tick; the block entity, when the old save had one, comes from
 * its tag through {@link BlockEntity#loadStatic} like {@code LevelChunk} does. A block whose old save had no block entity
 * gets none here, and the game creates a fresh one on first access as it does after a real load.
 */
public final class SaveCompatGameTests {

    private SaveCompatGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SaveCompatGameTests.class);
    }

    // ------------------------------------------------------------------ writing old saves

    /** A palette entry: {@code Name} and the given {@code key, value} property pairs, as a chunk section stores it. */
    static CompoundTag paletteEntry(String id, String... properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", Constants.MOD_ID + ":" + id);
        if (properties.length > 0) {
            CompoundTag props = new CompoundTag();
            for (int i = 0; i + 1 < properties.length; i += 2) props.putString(properties[i], properties[i + 1]);
            tag.put("Properties", props);
        }
        return tag;
    }

    /** A saved block entity tag with its {@code id}. */
    static CompoundTag blockEntity(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", Constants.MOD_ID + ":" + id);
        return tag;
    }

    /**
     * Writes the saved block at {@code abs} (absolute) as a chunk load does (see the class comment) and returns the state
     * the palette entry decoded to.
     */
    static BlockState writeSaved(GameTestHelper h, BlockPos abs, CompoundTag paletteEntry, @Nullable CompoundTag blockEntityTag) {
        ServerLevel level = h.getLevel();
        BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, paletteEntry).getOrThrow(
                e -> new GameTestAssertException("palette entry " + paletteEntry + " does not decode: " + e));
        level.removeBlockEntity(abs);
        LevelChunk chunk = level.getChunkAt(abs);
        chunk.getSection(chunk.getSectionIndex(abs.getY())).setBlockState(abs.getX() & 15, abs.getY() & 15, abs.getZ() & 15, state);
        if (blockEntityTag != null) {
            BlockEntity be = BlockEntity.loadStatic(abs.immutable(), state, blockEntityTag, level.registryAccess());
            if (be == null) throw new GameTestAssertException("the saved block entity " + blockEntityTag + " does not load");
            level.setBlockEntity(be);
        }
        return state;
    }

    // ------------------------------------------------------------------ BRD1 plank run

    /**
     * A BRD1 plank run (three level cells with {@code segment} 0..2 and BRD1's {@code tip}, a block entity on segment 0
     * only, without {@code on_ship}) converts on its first tick (BRD4 {@code relay}): the same three blocks, segment 0
     * holds a three-column level run, every cell a flat board of it, no plank dropped. (A BRD1 run always lay between two
     * ships; off a ship its ends check breaks the converted run at the next check, so the assertions are made on the
     * first tick after the conversion and the run is taken up quietly.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void aBrd1PlankRunLoadsAsALevelBoard(GameTestHelper h) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 3; x <= 5; x++) cells.add(h.absolutePos(new BlockPos(x, 3, 4)));
        for (int k = 0; k < cells.size(); k++) {
            CompoundTag be = null;
            if (k == 0) {
                be = blockEntity("boarding_plank");
                be.putDouble("far_x", cells.get(2).getX() + 1.5);
                be.putDouble("far_y", cells.get(2).getY() + 0.5);
                be.putDouble("far_z", cells.get(2).getZ() + 0.5);
                be.put("far_block", NbtUtils.writeBlockPos(cells.get(2).east().below()));
                be.putInt("length", 3);
                be.putString("landing", "LEVEL");
            }
            BlockState s = writeSaved(h, cells.get(k), paletteEntry("boarding_plank", "facing", "east", "segment",
                    Integer.toString(k), "tip", Boolean.toString(k == 2)), be);
            h.assertTrue(s.is(BoardingContent.PLANK.get()) && s.getValue(BoardingPlankBlock.SEGMENT) == k
                            && s.getValue(BoardingPlankBlock.FACING) == Direction.EAST && !s.getValue(Waterlogging.WATERLOGGED),
                    "BRD1 cell " + k + " decoded to " + s);
        }
        h.onEachTick(() -> {
            if (!(h.getLevel().getBlockEntity(cells.get(0)) instanceof BoardingPlankBlockEntity base) || !base.isBase()) {
                return;
            }
            h.assertTrue(base.cells().equals(cells) && base.columns() == 3 && base.rise() == 0,
                    "expected a level run over the three cells, got " + base.cells() + ", " + base.columns() + " columns rising " + base.rise());
            for (BlockPos c : cells) {
                h.assertTrue(h.getLevel().getBlockState(c).is(BoardingContent.PLANK.get()), "cell " + c + " is gone");
                h.assertTrue(h.getLevel().getBlockEntity(c) instanceof BoardingPlankBlockEntity cell && cells.get(0).equals(cell.base())
                        && cell.near() == 0.0 && cell.far() == 0.0, "cell " + c + " is not a flat board of the run");
            }
            h.assertTrue(items(h, BoardingContent.PLANK_ITEM.get().getDefaultInstance()) == 0, "a plank dropped in the conversion");
            for (BlockPos c : cells) h.getLevel().setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ RL2 ratline net

    /**
     * An RL2 net (a chainplate and a hoop each holding the single {@code net_other}/{@code net_a} link, cells naming fitting
     * A only and without RAIL5's {@code boarding} state) loads as a one-strand net (RL4 reads RL2's keys): both fittings
     * hold it, every cell stands and answers a climb direction, nothing breaks over 20 ticks; the fitting saves RL4's
     * {@code nets} key afterwards; breaking a cell still takes the whole net down and drops one net.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 80)
    public static void anRl2NetLoadsAsOneStrand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(6, y, 4), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(5, 5, 4), RiggingContent.MAST_HOOP.get().defaultBlockState().setValue(MastHoopBlock.FACING, Direction.WEST));
        h.setBlock(new BlockPos(1, 1, 4), RiggingContent.CHAINPLATE.get().defaultBlockState()
                .setValue(ChainplateBlock.FACE, AttachFace.FLOOR).setValue(ChainplateBlock.FACING, Direction.EAST));
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(5, 5, 4));
        // string it as today, then write every block back in RL2's shape
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        RatlineNetItem.use(level, stack, a, null);
        RatlineNetItem.use(level, stack, b, null);
        RatlineNets.Net net = RatlineNets.netOf(level, a);
        h.assertTrue(net != null, "the reference net was not strung");
        List<BlockPos> cells = RatlineNets.standingCells(level, net);
        h.assertTrue(!cells.isEmpty(), "the reference net has no cells");
        for (BlockPos fitting : List.of(a, b)) {
            CompoundTag now = level.getBlockEntity(fitting).saveWithFullMetadata(level.registryAccess());
            int[] links = now.getIntArray("nets");
            h.assertTrue(links.length == 4, "a fitting of a lone net holds " + links.length / 4 + " links");
            CompoundTag rl2 = blockEntity("net_fitting");
            rl2.put("net_other", new IntArrayTag(new int[] {links[0], links[1], links[2]}));
            rl2.putBoolean("net_a", (links[3] & 1) != 0);
            writeSaved(h, fitting, NbtUtils.writeBlockState(level.getBlockState(fitting)), rl2);
        }
        for (BlockPos c : cells) {
            CompoundTag now = level.getBlockEntity(c).saveWithFullMetadata(level.registryAccess());
            CompoundTag rl2 = blockEntity("ratline_net_cell");
            rl2.put("net_a", new IntArrayTag(now.getIntArray("net_a")));
            CompoundTag state = NbtUtils.writeBlockState(level.getBlockState(c));
            state.getCompound("Properties").remove("boarding");
            writeSaved(h, c, state, rl2);
        }
        h.runAfterDelay(20, () -> {
            RatlineNets.Net loaded = RatlineNets.netOf(level, a);
            h.assertTrue(loaded != null && loaded.a().equals(a) && loaded.b().equals(b), "fitting A does not hold the RL2 net: " + loaded);
            h.assertTrue(RatlineNets.netOf(level, b) != null, "the hoop does not hold the RL2 net");
            h.assertTrue(RatlineNets.standingCells(level, loaded).equals(cells), "the cells changed: " + RatlineNets.standingCells(level, loaded));
            for (BlockPos c : cells) {
                h.assertTrue(level.getBlockEntity(c) instanceof RatlineNetCellBlockEntity cell && a.equals(cell.fittingA())
                        && cell.climbDirection() != null, "cell " + c + " has no climb line");
            }
            CompoundTag saved = level.getBlockEntity(b).saveWithoutMetadata(level.registryAccess());
            h.assertTrue(saved.getIntArray("nets").length == 4 && !saved.contains("net_other"), "the hoop does not save RL4's key: " + saved);
            level.destroyBlock(cells.get(cells.size() / 2), true);
            h.assertTrue(RatlineNets.standingCells(level, loaded).isEmpty(), "breaking a cell left cells standing");
            h.assertTrue(RatlineNets.netOf(level, a) == null && RatlineNets.netOf(level, b) == null, "a fitting still holds the net");
        });
        h.runAfterDelay(23, () -> {
            int dropped = items(h, RiggingContent.NET_ITEM.get().getDefaultInstance());
            h.assertTrue(dropped == 1, "expected one dropped net, found " + dropped);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ G8 nameplate

    /**
     * Three G8 nameplates side by side (the state of a ladder: {@code facing}, {@code waterlogged}; a block entity with
     * only {@code text}) load as name boards (NAME2 kept the id): the new {@code left}/{@code right} joins start false,
     * the boards' first tick links the row with the leftmost (east, facing north) as its master.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void g8NameplatesLoadAsAJoinedNameBoard(GameTestHelper h) {
        for (int x = 1; x <= 7; x++) for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 3), Blocks.OAK_PLANKS);
        List<BlockPos> row = List.of(h.absolutePos(new BlockPos(5, 2, 2)), h.absolutePos(new BlockPos(4, 2, 2)),
                h.absolutePos(new BlockPos(3, 2, 2)));
        for (BlockPos p : row) {
            CompoundTag be = blockEntity("nameplate");
            be.putString("text", "Black Gull");
            BlockState s = writeSaved(h, p, paletteEntry("nameplate", "facing", "north", "waterlogged", "false"), be);
            h.assertTrue(s.is(ShipDecor.NAMEPLATE.get()) && s.getValue(NameBoardBlock.FACING) == Direction.NORTH
                    && !s.getValue(NameBoardBlock.LEFT) && !s.getValue(NameBoardBlock.RIGHT), "a G8 plate decoded to " + s);
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < row.size(); i++) {
                BlockPos p = row.get(i);
                if (!(h.getLevel().getBlockEntity(p) instanceof NameplateBlockEntity be)) throw new GameTestAssertException("no board at " + p);
                if (be.rowIndex() != i) throw new GameTestAssertException("board " + p + " has index " + be.rowIndex() + ", expected " + i);
                if (i == 0 && be.rowLength() != row.size()) throw new GameTestAssertException("the master's row is " + be.rowLength() + " long");
                BlockState s = h.getLevel().getBlockState(p);
                if (s.getValue(NameBoardBlock.LEFT) != (i > 0) || s.getValue(NameBoardBlock.RIGHT) != (i < row.size() - 1)) {
                    throw new GameTestAssertException("board " + p + " joins wrongly: " + s);
                }
                // off a ship a board shows nothing, as the G8 plate did
                if (!be.text().isEmpty()) throw new GameTestAssertException("board " + p + " off a ship shows '" + be.text() + "'");
            }
        });
    }

    // ------------------------------------------------------------------ OAR1 bench

    /**
     * An OAR1 oak oar bench saved before it became waterloggable ({@code facing} only) loads with its facing and
     * {@code waterlogged} false, holds no water and is still there after 20 ticks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void anOldOarBenchLoadsDry(GameTestHelper h) {
        for (int x = 2; x <= 6; x++) for (int z = 2; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
        BlockPos p = h.absolutePos(new BlockPos(4, 2, 4));
        BlockState s = writeSaved(h, p, paletteEntry("oak_oar_bench", "facing", "east"), null);
        Block bench = BuiltInRegistries.BLOCK.get(Constants.id("oak_oar_bench"));
        h.assertTrue(bench instanceof OarBenchBlock && s.is(bench) && s.getValue(OarBenchBlock.FACING) == Direction.EAST
                && !s.getValue(Waterlogging.WATERLOGGED) && s.getFluidState().isEmpty(), "the old bench decoded to " + s);
        h.runAfterDelay(20, () -> {
            BlockState now = h.getLevel().getBlockState(p);
            h.assertTrue(now.is(bench) && now.getValue(OarBenchBlock.FACING) == Direction.EAST && now.getFluidState().isEmpty(),
                    "the bench changed: " + now);
            h.succeed();
        });
    }

    /**
     * NAME3: a port registry saved before NAME3 (port records without {@code name}, as the WG1/WG2/SW1 codec wrote
     * them) loads, and every port gets its proper name from the world seed in the saved order, the same on every load,
     * unique, of its kind; the registry is dirty so the names are saved from then on. The id stays the key.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 20)
    public static void aPreName3PortRecordLoadsAndGetsAName(GameTestHelper h) {
        var server = h.getLevel().getServer();
        long seed = server.overworld().getSeed();
        net.minecraft.nbt.ListTag ports = new net.minecraft.nbt.ListTag();
        ports.add(oldPort("village_1192_1530", "seafarer_village", 1192, 1530));
        ports.add(oldPort("navy_outpost_-5240_2455", "navy_outpost", -5240, 2455));
        ports.add(oldPort("pirate_island_-3931_-9000", "pirate_island", -3931, -9000));
        CompoundTag saved = new CompoundTag();
        saved.put("ports", ports);

        var a = com.richardsenger.piratesnships.world.port.PortRegistry.load(saved.copy(), server.registryAccess());
        h.assertValueEqual(a.index().size(), 3, "all three old records load");
        h.assertTrue(a.index().all().stream().noneMatch(com.richardsenger.piratesnships.world.port.Port::named), "no names in the old save");
        a.seed(seed);
        var b = com.richardsenger.piratesnships.world.port.PortRegistry.load(saved.copy(), server.registryAccess());
        b.seed(seed);
        List<String> names = a.index().all().stream().map(com.richardsenger.piratesnships.world.port.Port::name).toList();
        h.assertValueEqual(b.index().all().stream().map(com.richardsenger.piratesnships.world.port.Port::name).toList(), names, "the same names on every load");
        h.assertTrue(names.stream().noneMatch(String::isEmpty), "every port named: " + names);
        h.assertValueEqual(new java.util.HashSet<>(names).size(), 3, "three names: " + names);
        h.assertTrue(names.get(1).startsWith("Fort ") || names.get(1).endsWith(" Battery"), "the outpost: " + names.get(1));
        h.assertValueEqual(names.get(0), com.richardsenger.piratesnships.world.port.PortNames.name(seed,
                com.richardsenger.piratesnships.trade.market.PortKind.SEAFARER_VILLAGE, 1192, 1530, k -> false), "the first by the rule");
        h.assertTrue(a.isDirty(), "the names are saved from now on");
        h.assertTrue(a.index().byId(Constants.id("navy_outpost_-5240_2455")).isPresent(), "the id stays the key");
        CompoundTag again = a.save(new CompoundTag(), server.registryAccess());
        h.assertValueEqual(again.getList("ports", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(2).getString("name"), names.get(2),
                "the name is written");
        h.succeed();
    }

    /** A port record as WG1 saved it: no {@code name} (and no treasures or orders). */
    private static CompoundTag oldPort(String path, String kind, int x, int z) {
        CompoundTag t = new CompoundTag();
        t.putString("id", Constants.MOD_ID + ":" + path);
        t.putString("kind", kind);
        t.putString("dimension", "minecraft:overworld");
        t.put("centre", new IntArrayTag(new int[]{x, 64, z}));
        t.put("box", new IntArrayTag(new int[]{x - 30, 40, z - 30, x + 30, 90, z + 30}));
        t.putString("climate", "temperate");
        return t;
    }

    // ------------------------------------------------------------------ helpers

    private static int items(GameTestHelper h, ItemStack like) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2),
                        e -> e.isAlive() && ItemStack.isSameItem(e.getItem(), like))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }
}
