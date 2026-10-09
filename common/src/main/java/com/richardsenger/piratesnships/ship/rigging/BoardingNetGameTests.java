package com.richardsenger.piratesnships.ship.rigging;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.ship.decor.railing.RailingBlock;
import com.richardsenger.piratesnships.ship.decor.railing.RailingContent;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlineNets.Net;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Boarding nets (RAIL5, docs/design.md §4.8 "Railing extras") in a real server: a ratline net strung between two
 * chainplates on top of a rail run hangs along the rail as a boarding net (its cells in the row above the rail, facing
 * outboard); the refusals (two edges, neighbours, too long; nets off makes it a flat net again); a zombie coming over
 * the rail from outboard is held for {@code rigging.boarding_net_hold} ticks, then climbs over; on an assembled ship
 * the ship's own crew member crosses at once while a zombie beside it is held, and the net survived the assembly; a
 * cutlass cuts it in {@code rigging.boarding_net_cut_hits} hits while it cannot be mined.
 */
public final class BoardingNetGameTests {

    private static final String BATCH = "pirates_n_ships_config_boarding_net_";
    /** Ticks after assembly before anyone is put on the rail. */
    private static final int SETTLE = 40;
    /** The push a test gives a boarder towards inboard every tick [blocks/tick]. */
    private static final double PUSH = 0.12;

    private BoardingNetGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BoardingNetGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** An oak railing railed on {@code edge} at the absolute position {@code abs}, posts worked out. */
    static void railing(ServerLevel level, BlockPos abs, Direction edge) {
        BlockState s = RailingContent.RAILINGS.get(ShipWood.OAK).defaultBlockState().setValue(RailingBlock.edge(edge), true);
        level.setBlock(abs, s, 3);
        level.setBlock(abs, RailingBlock.withPosts(level.getBlockState(abs), level, abs), 3);
    }

    /** A run of railings from x {@code x0} to {@code x1} at relative y, z, railed on {@code edge}. */
    private static void railRun(GameTestHelper h, int x0, int x1, int y, int z, Direction edge) {
        for (int x = x0; x <= x1; x++) {
            railing(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), edge);
        }
        for (int x = x0; x <= x1; x++) {
            BlockPos abs = h.absolutePos(new BlockPos(x, y, z));
            h.getLevel().setBlock(abs, RailingBlock.withPosts(h.getLevel().getBlockState(abs), h.getLevel(), abs), 3);
        }
    }

    private static void plate(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, RatlineNetGameTests.chainplate(AttachFace.FLOOR, Direction.EAST));
    }

    private static String string(ServerLevel level, BlockPos a, BlockPos b) {
        ItemStack stack = new ItemStack(RiggingContent.NET_ITEM.get());
        String first = key(RatlineNetItem.use(level, stack, a, null));
        if (!first.equals(RatlineNetItem.KEY_MARKED)) {
            return first;
        }
        return key(RatlineNetItem.use(level, stack, b, null));
    }

    /** The land rig: a stone floor, a rail run at y 1, z 4 from x 0 to 8 railed north (outboard -z), chainplates on x 1 and 6. */
    private static void landRig(GameTestHelper h) {
        floor(h, 9);
        railRun(h, 0, 8, 1, 4, Direction.NORTH);
        plate(h, new BlockPos(1, 2, 4));
        plate(h, new BlockPos(6, 2, 4));
    }

    private static String stringLand(GameTestHelper h) {
        return string(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 4)), h.absolutePos(new BlockPos(6, 2, 4)));
    }

    /** What is wrong with the boarding net cells at {@code cells} (null when every one is a boarding cell facing {@code out}). */
    private static String cellProblem(ServerLevel level, List<BlockPos> cells, Direction out) {
        for (BlockPos p : cells) {
            BlockState s = level.getBlockState(p);
            if (!s.is(RiggingContent.NET_CELL.get())) {
                return p + " is " + s;
            }
            if (!s.getValue(RatlineNetCellBlock.BOARDING) || s.getValue(RatlineNetCellBlock.FACING) != out) {
                return p + " is not a boarding cell facing " + out + ": " + s;
            }
        }
        return null;
    }

    private static int droppedNets(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2),
                        e -> e.isAlive() && e.getItem().is(RiggingContent.NET_ITEM.get()))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * A mob at {@code world} whose own goals neither move nor turn it (a mob without AI would not even fall), so only the
     * test's push moves it.
     */
    private static <T extends Mob> T spawn(GameTestHelper h, EntityType<T> type, Vec3 world) {
        T mob = type.create(h.getLevel());
        if (mob == null) throw new AssertionError("no " + type);
        mob.moveTo(world.x, world.y, world.z, 180f, 0f);
        still(mob);
        mob.setPersistenceRequired();
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void still(Mob mob) {
        for (Goal.Flag f : Goal.Flag.values()) {
            mob.goalSelector.disableControlFlag(f);
            mob.targetSelector.disableControlFlag(f);
        }
    }

    // ------------------------------------------------------------------ stringing

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void stringsABoardingNetAlongTheRail(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        String r = stringLand(h);
        h.assertTrue(r.equals(RatlineNetItem.KEY_BOARDING_STRUNG), "not strung as a boarding net: " + r);
        Net net = RatlineNets.netOf(level, h.absolutePos(new BlockPos(1, 2, 4)));
        h.assertTrue(net != null && BoardingNets.isBoarding(level, net), "no boarding net held: " + net);
        h.assertTrue(BoardingNets.outboard(level, net) == Direction.NORTH, "outboard " + BoardingNets.outboard(level, net));
        List<BlockPos> cells = List.of(2, 3, 4, 5).stream().map(x -> h.absolutePos(new BlockPos(x, 2, 4))).toList();
        String problem = cellProblem(level, cells, Direction.NORTH);
        h.assertTrue(problem == null, problem);
        h.assertTrue(RatlineNets.standingCells(level, net).size() == 4, "cells " + RatlineNets.standingCells(level, net));
        // the climbing assist leaves a boarding net alone: it is climbed over, not along
        h.assertTrue(RatlineNetAssist.netAt(level, cells.get(1)) == null, "the assist would carry a climber along the rail");
        // nothing but a cutlass may take it down: no block placed into it, no mining
        BlockState cell = level.getBlockState(cells.get(0));
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(cell.getDestroyProgress(p, level, cells.get(0)) == 0f, "a boarding net can be mined");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_24)
    public static void aBoardingNetRunsAlongOneRailEdgeWithinItsLength(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        floor(h, 24);
        // two edges: north at x 1, south at x 5
        railing(level, h.absolutePos(new BlockPos(1, 1, 2)), Direction.NORTH);
        railing(level, h.absolutePos(new BlockPos(5, 1, 2)), Direction.SOUTH);
        plate(h, new BlockPos(1, 2, 2));
        plate(h, new BlockPos(5, 2, 2));
        String r = string(level, h.absolutePos(new BlockPos(1, 2, 2)), h.absolutePos(new BlockPos(5, 2, 2)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_NOT_ALONG), "two edges: " + r);
        // a row along x on east edges does not run along them
        railing(level, h.absolutePos(new BlockPos(1, 1, 6)), Direction.EAST);
        railing(level, h.absolutePos(new BlockPos(5, 1, 6)), Direction.EAST);
        plate(h, new BlockPos(1, 2, 6));
        plate(h, new BlockPos(5, 2, 6));
        r = string(level, h.absolutePos(new BlockPos(1, 2, 6)), h.absolutePos(new BlockPos(5, 2, 6)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_NOT_ALONG), "across the edge: " + r);
        // neighbours
        railRun(h, 1, 2, 1, 10, Direction.NORTH);
        plate(h, new BlockPos(1, 2, 10));
        plate(h, new BlockPos(2, 2, 10));
        r = string(level, h.absolutePos(new BlockPos(1, 2, 10)), h.absolutePos(new BlockPos(2, 2, 10)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_TOO_SHORT), "neighbours: " + r);
        // nine blocks apart, eight allowed
        railRun(h, 1, 10, 1, 14, Direction.NORTH);
        plate(h, new BlockPos(1, 2, 14));
        plate(h, new BlockPos(10, 2, 14));
        r = string(level, h.absolutePos(new BlockPos(1, 2, 14)), h.absolutePos(new BlockPos(10, 2, 14)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_TOO_LONG), "nine blocks: " + r);
        // eight are fine, along z on a west edge too
        for (int z = 14; z <= 22; z++) {
            railing(level, h.absolutePos(new BlockPos(20, 1, z)), Direction.WEST);
        }
        plate(h, new BlockPos(20, 2, 14));
        plate(h, new BlockPos(20, 2, 22));
        r = string(level, h.absolutePos(new BlockPos(20, 2, 14)), h.absolutePos(new BlockPos(20, 2, 22)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_BOARDING_STRUNG), "eight blocks along z: " + r);
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(20, 2, 18))).getValue(RatlineNetCellBlock.FACING) == Direction.WEST,
                "the west net does not face west");
        // two chainplates on plain stone at one height stay a flat ratline net
        plate(h, new BlockPos(1, 1, 20));
        plate(h, new BlockPos(5, 1, 20));
        r = string(level, h.absolutePos(new BlockPos(1, 1, 20)), h.absolutePos(new BlockPos(5, 1, 20)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_TOO_FLAT), "on stone: " + r);
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "off")
    public static void boardingNetsOffLeavesTheFlatRule(GameTestHelper h) {
        ConfigOverrides.during(h, RiggingConfig.BOARDING_NETS, false);
        landRig(h);
        String r = stringLand(h);
        h.assertTrue(r.equals(RatlineNetItem.KEY_TOO_FLAT), "with boarding nets off: " + r);
        h.succeed();
    }

    // ------------------------------------------------------------------ holding

    /**
     * A zombie (no AI) on the rail top outboard of the net at x 3, pushed inboard every tick: held behind the wall while
     * the hold runs, then over the rail and down on the deck.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void aForeignMobIsHeldThenClimbsOver(GameTestHelper h) {
        landRig(h);
        h.assertTrue(stringLand(h).equals(RatlineNetItem.KEY_BOARDING_STRUNG), "not strung");
        int hold = RiggingConfig.BOARDING_NET_HOLD.get();
        Vec3 start = h.absoluteVec(new Vec3(3.5, 2.6, 3.8));
        Mob z = spawn(h, EntityType.HUSK, start);
        double wall = h.absoluteVec(new Vec3(0, 0, 4.0 + BoardingNetRules.WALL - 0.3)).z;
        double inboard = h.absoluteVec(new Vec3(0, 0, 5.0)).z;
        boolean[] seenHeld = {false};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (!z.isAlive()) {
                return;
            }
            z.setDeltaMovement(0, z.getDeltaMovement().y, PUSH);
            if (t >= 3 && t <= hold - 2) {
                h.assertTrue(z.getZ() <= wall + 0.03, "tick " + t + ": the zombie got through the net: z " + (z.getZ() - wall) + " past the wall");
                if (BoardingNets.holds(h.getLevel(), z)) {
                    seenHeld[0] = true;
                }
            }
        });
        h.runAtTickTime(hold + 50, () -> {
            h.assertTrue(seenHeld[0], "the net never held the zombie");
            h.assertTrue(z.getZ() > inboard, "the zombie did not climb over after the hold: " + (z.getZ() - inboard) + " short");
            z.discard();
            h.succeed();
        });
    }

    /**
     * On an assembled hull afloat: the net strung before assembly is still a boarding net in the plot; the ship's own
     * crew member and a zombie stand on the rail top outboard of it and are pushed inboard; the crew member crosses at
     * once, the zombie is held, then crosses too.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void onAShipTheCrewPassesAndAZombieIsHeld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        DryHullGameTests.basin(h, 0, 23, true);
        SailingGameTestsShips.openSky(h, 24);
        BlockPos helmRel = DryHullGameTests.hull(h, 9, false);
        railRun(h, 9, 13, 9, 9, Direction.NORTH);
        plate(h, new BlockPos(9, 10, 9));
        plate(h, new BlockPos(13, 10, 9));
        String r = string(level, h.absolutePos(new BlockPos(9, 10, 9)), h.absolutePos(new BlockPos(13, 10, 9)));
        h.assertTrue(r.equals(RatlineNetItem.KEY_BOARDING_STRUNG), "not strung on land: " + r);
        Fixture f = DryHullGameTests.assemble(h, helmRel);
        ShipBody ship = f.ship();
        java.util.function.Function<BlockPos, BlockPos> plot = rel -> f.helmPlot().offset(rel.subtract(helmRel));
        List<BlockPos> cells = List.of(10, 11, 12).stream().map(x -> plot.apply(new BlockPos(x, 10, 9))).toList();
        String problem = cellProblem(level, cells, Direction.NORTH);
        h.assertTrue(problem == null, "after assembly: " + problem);
        Net net = RatlineNets.netOf(level, plot.apply(new BlockPos(9, 10, 9)));
        h.assertTrue(net != null && BoardingNets.isBoarding(level, net), "the boarding net did not survive assembly: " + net);
        h.assertTrue(droppedNets(h) == 0, "a net dropped in the assembly");
        int hold = RiggingConfig.BOARDING_NET_HOLD.get();
        Object[] who = new Object[2];
        h.runAtTickTime(SETTLE, () -> {
            Vec3 zAt = ship.toWorld(Vec3.atLowerCornerOf(plot.apply(new BlockPos(10, 9, 9))).add(0.5, 1.6, -0.2));
            Vec3 cAt = ship.toWorld(Vec3.atLowerCornerOf(plot.apply(new BlockPos(12, 9, 9))).add(0.5, 1.6, -0.2));
            who[0] = spawn(h, EntityType.HUSK, zAt);
            CrewMember crew = StationContent.CREW_MEMBER.get().create(level);
            if (crew == null) throw new AssertionError("no crew member");
            crew.moveTo(cAt.x, cAt.y, cAt.z, 180f, 0f);
            still(crew);
            level.addFreshEntity(crew);
            who[1] = crew;
        });
        // plot z of the net cells' outboard face is cells.get(0).getZ(); inboard past the cell is + 1
        double face = cells.get(0).getZ();
        boolean[] crewHeld = {false};
        boolean[] zombieHeld = {false};
        long[] tracked = {-1};
        int push = SETTLE + 10;
        h.onEachTick(() -> {
            long t = h.getTick();
            if (who[0] == null) {
                return;
            }
            Mob zombie = (Mob) who[0];
            Mob crew = (Mob) who[1];
            // the crew member belongs to the ship once it stands on it (Sable tracks it after landing on the rail top)
            ShipBody on = ShipEntities.standingOrRiding(crew);
            if (tracked[0] < 0 && on != null && on.id().equals(ship.id())) {
                tracked[0] = t;
            }
            if (t < push) {
                return;
            }
            h.assertTrue(tracked[0] >= 0, "the crew member never stood on the ship's rail");
            Vec3 in = RatlineNetAssist.rotate(ship.orientation(), new Vec3(0, 0, PUSH));
            for (Object o : who) {
                Mob m = (Mob) o;
                m.setDeltaMovement(in.x, m.getDeltaMovement().y, in.z);
            }
            if (BoardingNets.holds(level, crew)) crewHeld[0] = true;
            if (t <= SETTLE + hold - 4) {
                double zz = ship.toPlot(zombie.position()).z;
                h.assertTrue(zz <= face + BoardingNetRules.WALL - 0.27, "tick " + t + ": the zombie got through: plot z " + (zz - face));
                if (BoardingNets.holds(level, zombie)) zombieHeld[0] = true;
            }
        });
        h.runAtTickTime(push + 30, () -> {
            Mob crew = (Mob) who[1];
            double cz = ship.toPlot(crew.position()).z;
            h.assertTrue(!crewHeld[0], "the net held the ship's own crew member");
            h.assertTrue(cz > face + 1.0, "the crew member did not cross the net: plot z " + (cz - face));
        });
        h.runAtTickTime(SETTLE + hold + 60, () -> {
            Mob zombie = (Mob) who[0];
            h.assertTrue(zombieHeld[0], "the net never held the zombie");
            double zz = ship.toPlot(zombie.position()).z;
            h.assertTrue(zz > face + 1.0, "the zombie did not climb over after the hold: plot z " + (zz - face));
            for (Object o : who) ((Mob) o).discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ cutting

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aCutlassCutsTheNet(GameTestHelper h) {
        landRig(h);
        ServerLevel level = h.getLevel();
        h.assertTrue(stringLand(h).equals(RatlineNetItem.KEY_BOARDING_STRUNG), "not strung");
        BlockPos cell = h.absolutePos(new BlockPos(3, 2, 4));
        Player hand = h.makeMockPlayer(GameType.SURVIVAL);
        level.getBlockState(cell).attack(level, cell, hand);
        level.getBlockState(cell).attack(level, cell, hand);
        level.getBlockState(cell).attack(level, cell, hand);
        h.assertTrue(level.getBlockState(cell).is(RiggingContent.NET_CELL.get()), "bare hands cut the net");
        Player cutter = h.makeMockPlayer(GameType.SURVIVAL);
        cutter.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CombatContent.CUTLASS.get()));
        int hits = RiggingConfig.BOARDING_NET_CUT_HITS.get();
        for (int i = 1; i < hits; i++) {
            level.getBlockState(cell).attack(level, cell, cutter);
            h.assertTrue(level.getBlockState(cell).is(RiggingContent.NET_CELL.get()), "cut after " + i + " hits");
        }
        level.getBlockState(cell).attack(level, cell, cutter);
        for (int x = 2; x <= 5; x++) {
            h.assertTrue(!level.getBlockState(h.absolutePos(new BlockPos(x, 2, 4))).is(RiggingContent.NET_CELL.get()),
                    "cell " + x + " stands after " + hits + " hits");
        }
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(1, 2, 4))).is(RiggingContent.CHAINPLATE.get())
                && level.getBlockState(h.absolutePos(new BlockPos(6, 2, 4))).is(RiggingContent.CHAINPLATE.get()), "a chainplate went");
        Vec3 a = h.absoluteVec(new Vec3(1.5, 2.5, 4.5));
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2),
                e -> e.isAlive() && e.getItem().is(RiggingContent.NET_ITEM.get()));
        h.assertTrue(droppedNets(h) == 1 && drops.get(0).position().distanceTo(a) < 1.5,
                "expected one net at A, got " + droppedNets(h) + " at " + drops.stream().map(e -> e.position().subtract(a)).toList());
        h.succeed();
    }
}
