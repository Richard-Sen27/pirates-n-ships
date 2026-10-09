package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.cannon.CannonStation.CannonOrder;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.rope.RopeLines;
import com.richardsenger.piratesnships.sailing.sail.ClothTears;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.ship.rigging.RatlinesBlock;
import com.richardsenger.piratesnships.ship.rigging.RiggingContent;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Chain shot and grapeshot (CAN3, docs/design.md §8.2) in a real server: loading and firing each kind, chain shot tearing
 * the cloth of a ship's square sail within {@code cannons.chain_shot.cloth_radius} (and the sail drawing less), cutting a
 * rope and breaking ratlines, and breaching no hull below the waterline; grapeshot hitting the mobs on a platform and
 * sparing the people on the firing ship's deck; the crew loading the preferred shot from a mixed locker; the toggles.
 * Shots are spawned as the gun spawns them where a test needs an exact line. Tests that change config run in batches
 * of their own ({@link ConfigOverrides}).
 */
public final class CannonShotGameTests {

    private static final String CONFIG = "pirates_n_ships_config_cannon_shot_";

    private CannonShotGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(CannonShotGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static Player creative(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.CREATIVE);
        p.getAbilities().instabuild = true;
        return p;
    }

    /** Loads gunpowder and {@code shot} with a creative player (no items used). */
    private static void load(GameTestHelper h, BlockPos master, Item shot) {
        ServerLevel level = h.getLevel();
        Player p = creative(h);
        CannonService.Outcome powder = CannonService.load(level, master, p, new ItemStack(Items.GUNPOWDER)).outcome();
        h.assertTrue(powder == CannonService.Outcome.POWDER_IN, "powder: " + powder);
        CannonService.Outcome in = CannonService.load(level, master, p, new ItemStack(shot)).outcome();
        h.assertTrue(in == CannonService.Outcome.BALL_IN, "shot: " + in);
    }

    private static List<CannonballEntity> shots(GameTestHelper h) {
        return h.getEntities(CannonContent.CANNONBALL.get());
    }

    private static void clearShots(GameTestHelper h) {
        shots(h).forEach(CannonballEntity::discard);
    }

    /** A chain shot spawned at the world point {@code from} flying toward {@code to} at the chain shot's start speed. */
    private static CannonballEntity chainShot(GameTestHelper h, Vec3 from, Vec3 to) {
        ServerLevel level = h.getLevel();
        Vec3 v = to.subtract(from).normalize().scale(CannonConfig.muzzleVelocity(ShotKind.CHAIN));
        CannonballEntity shot = new CannonballEntity(level, from, v, CannonConfig.entityDamage(ShotKind.CHAIN), 60);
        shot.setKind(ShotKind.CHAIN);
        level.addFreshEntity(shot);
        return shot;
    }

    /** A 5×4×5 plank hull at x 17..21, z 17..21 (deck y 8) with its helm at (19, 9, 18) facing north. */
    private static BlockPos sailHull(GameTestHelper h) {
        for (int x = 17; x <= 21; x++) {
            for (int z = 17; z <= 21; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == 17 || x == 21 || z == 17 || z == 21;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(19, 9, 18);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return helm;
    }

    /**
     * A ship resting in a dry basin with one full square sail of two 7-wide yards 3 apart (21 cells of cloth) in column
     * (19, 20) of the hull: the head is the upper yard's middle block at y 12.
     */
    private record SailShip(SailingGameTestsShips.Fixture f, BlockPos head) {
        ShipBody ship() {
            return f.ship();
        }

        SailingRuntime runtime() {
            return f.runtime();
        }
    }

    private static SailShip sailShip(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false);
        BlockPos helm = sailHull(h);
        SailingGameTestsShips.rig(h, 19, 20, 3, 3, SailTrim.FULL);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, helm);
        h.assertTrue(f.runtime().sailCount() == 1, "expected one sail, got " + f.runtime().sailCount());
        BlockPos head = f.runtime().sailPositions().get(0);
        h.assertTrue(Math.abs(f.runtime().areaAt(head) - 21.0) < 1e-9, "area " + f.runtime().areaAt(head) + ", expected 21");
        return new SailShip(f, head);
    }

    private static ClothTears tears(GameTestHelper h, BlockPos head) {
        if (h.getLevel().getBlockEntity(head) instanceof YardBlockEntity be) return be.tears();
        throw new GameTestAssertException("no yard at " + head);
    }

    /**
     * Fires a chain shot through the sail's cloth at cloth point (u 2, v 1.5), from 2 blocks before the sail (it drops a
     * few hundredths of a block on the way, so it passes just below v 1.5).
     */
    private static void shootThroughTheSail(GameTestHelper h, SailShip s) {
        Vec3 target = Vec3.atCenterOf(s.head()).add(2, -1.5, 0); // the yards run along x: u is x, v down
        chainShot(h, s.ship().toWorld(target.add(0, 0, -2)), s.ship().toWorld(target.add(0, 0, 8)));
    }

    // ------------------------------------------------------------------ loading and firing

    /**
     * Chain shot and grapeshot load like a cannonball and are kept in the block state; chain shot leaves as one shot at
     * the chain shot's start speed, grapeshot as {@code pellets} pellets in the cone at the grapeshot's start speed; the
     * fired gun is empty with the ball kind again. A broken gun gives its loaded chain shot back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void chainShotAndGrapeshotLoadAndFireAsTheirOwnShots(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = CannonGameTests.cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        load(h, pos, CannonContent.CHAIN_SHOT.get());
        BlockState s = level.getBlockState(pos);
        h.assertTrue(s.getValue(CannonBlock.LOAD) == CannonLoad.LOADED && s.getValue(CannonBlock.SHOT) == ShotKind.CHAIN,
                "loaded " + s.getValue(CannonBlock.LOAD) + " / " + s.getValue(CannonBlock.SHOT));
        CannonService.Use use = CannonService.fire(level, pos, null);
        h.assertTrue(use.outcome() == CannonService.Outcome.FIRED, "chain shot: " + use.outcome());
        List<CannonballEntity> fired = shots(h);
        h.assertTrue(fired.size() == 1 && fired.get(0).kind() == ShotKind.CHAIN, "fired " + fired.size() + " shots");
        double speed = fired.get(0).getDeltaMovement().length();
        h.assertTrue(Math.abs(speed - CannonConfig.muzzleVelocity(ShotKind.CHAIN)) < 1e-6, "chain shot speed " + speed);
        h.assertTrue(fired.get(0).getItem().is(CannonContent.CHAIN_SHOT.get()), "the chain shot is drawn as " + fired.get(0).getItem());
        s = level.getBlockState(pos);
        h.assertTrue(s.getValue(CannonBlock.LOAD) == CannonLoad.EMPTY && s.getValue(CannonBlock.SHOT) == ShotKind.BALL,
                "after the shot " + s.getValue(CannonBlock.LOAD) + " / " + s.getValue(CannonBlock.SHOT));
        clearShots(h);

        level.getBlockEntity(pos, CannonContent.CANNON_ENTITY.get()).orElseThrow().setReloadUntil(0);
        load(h, pos, CannonContent.GRAPESHOT.get());
        CannonService.fire(level, pos, null);
        List<CannonballEntity> pellets = shots(h);
        int n = CannonConfig.GRAPE_PELLETS.get();
        h.assertTrue(pellets.size() == n, "expected " + n + " pellets, got " + pellets.size());
        CannonService.Barrel barrel = CannonService.barrel(level, pos);
        h.assertTrue(barrel != null, "no barrel");
        Vec3 axis = barrel.direction();
        for (CannonballEntity p : pellets) {
            h.assertTrue(p.kind() == ShotKind.GRAPE, "a pellet is " + p.kind());
            Vec3 v = p.getDeltaMovement();
            h.assertTrue(Math.abs(v.length() - CannonConfig.muzzleVelocity(ShotKind.GRAPE)) < 1e-6, "pellet speed " + v.length());
            double off = ShotRules.angleDegrees(v, axis);
            h.assertTrue(off <= CannonConfig.GRAPE_SPREAD_DEGREES.get() + 1e-3, "a pellet flies " + off + "° off the barrel");
            h.assertTrue(Math.abs(p.damage() - CannonConfig.entityDamage(ShotKind.GRAPE)) < 1e-6, "pellet damage " + p.damage());
        }
        clearShots(h);

        level.getBlockEntity(pos, CannonContent.CANNON_ENTITY.get()).orElseThrow().setReloadUntil(0);
        load(h, pos, CannonContent.CHAIN_SHOT.get());
        level.destroyBlock(pos, true);
        h.runAfterDelay(2, () -> {
            boolean back = h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                    .anyMatch(st -> st.is(CannonContent.CHAIN_SHOT.get()));
            h.assertTrue(back, "the broken gun did not give its chain shot back");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ chain shot

    /**
     * Chain shot through a ship's full square sail tears the cloth cells within the radius of where it passed (7 to 9 of
     * the 21, columns 1..3) and nothing further; the sail's area drops to its whole share at once; the shot flies on.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120)
    public static void chainShotTearsTheClothWithinTheRadiusAndTheSailDrawsLess(GameTestHelper h) {
        SailShip s = sailShip(h);
        h.runAfterDelay(5, () -> shootThroughTheSail(h, s));
        h.runAfterDelay(6, () -> h.succeedWhen(() -> {
            ClothTears t = tears(h, s.head());
            // within 1.5 of (2, ~1.5): the three cells of column 2 and rows 0..2, and their neighbours in columns 1, 3
            h.assertTrue(t.size() >= 7 && t.size() <= 9, "torn cells: " + t);
            for (int c = 1; c <= 3; c++) h.assertTrue(t.torn(c, 1), "the cell (" + c + ", 1) at the hole is whole: " + t);
            for (int p : t.packed()) {
                int c = ClothTears.column(p);
                h.assertTrue(c >= 1 && c <= 3, "a cell outside the radius was torn: " + t);
            }
            double area = s.runtime().areaAt(s.head());
            h.assertTrue(Math.abs(area - (21 - t.size())) < 1e-6, "the torn sail's area is " + area + ", expected " + (21 - t.size()));
            h.assertTrue(s.runtime().trimAt(s.head()) == SailTrim.FULL, "the trim changed");
            clearShots(h);
        }));
    }

    /** {@code cannons.chain_shot.rigging_damage} off: the same shot tears nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG + "no_rigging_damage")
    public static void withoutRiggingDamageChainShotTearsNoCloth(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CHAIN_RIGGING_DAMAGE, false);
        SailShip s = sailShip(h);
        h.runAfterDelay(5, () -> shootThroughTheSail(h, s));
        h.runAfterDelay(30, () -> {
            h.assertTrue(tears(h, s.head()).isEmpty(), "torn: " + tears(h, s.head()));
            h.assertTrue(Math.abs(s.runtime().areaAt(s.head()) - 21.0) < 1e-9, "area " + s.runtime().areaAt(s.head()));
            clearShots(h);
            h.succeed();
        });
    }

    /** Torn cloth mends one cell per {@code mend_ticks} from the yard down, and the sail draws its full area again. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = CONFIG + "mend")
    public static void tornClothMendsCellByCell(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CHAIN_MEND_TICKS, 5);
        SailShip s = sailShip(h);
        if (!(h.getLevel().getBlockEntity(s.head()) instanceof YardBlockEntity be)) throw new GameTestAssertException("no head");
        be.setTears(ClothTears.of(new int[]{ClothTears.pack(0, 0), ClothTears.pack(0, 1)}));
        h.assertTrue(s.runtime().areaAt(s.head()) < 21.0 - 1.9, "the torn sail draws " + s.runtime().areaAt(s.head()));
        h.succeedWhen(() -> {
            h.assertTrue(be.tears().isEmpty(), "still torn: " + be.tears());
            h.assertTrue(Math.abs(s.runtime().areaAt(s.head()) - 21.0) < 1e-9, "area " + s.runtime().areaAt(s.head()));
        });
    }

    /**
     * Chain shot at the hull of a floating ship below the waterline: the plank stays, no breach, and the shot is gone
     * (a ball there floods the hold: {@code CannonGameTests#ballBelowTheWaterlineHolesTheHullAndItFloods}).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void chainShotBreachesNoHull(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        Fixture f = DryHullGameTests.assemble(h, DryHullGameTests.hull(h, 9, false));
        ServerLevel level = h.getLevel();
        BlockPos wall = f.hold(-2, -3, 0);
        h.runAfterDelay(20, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "no wall at " + wall);
            Vec3 target = f.ship().toWorld(Vec3.atCenterOf(wall));
            chainShot(h, target.add(-2.0, 0, 0), target);
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "chain shot broke the hull plank");
            h.assertTrue(f.runtime().breaches().isEmpty(), "breaches " + f.runtime().breaches());
            h.assertTrue(shots(h).isEmpty(), "the chain shot is still flying");
            h.succeed();
        });
    }

    /**
     * On land, chain shot passing between two cleats roped together cuts the rope, and where it strikes a stone wall it
     * breaks the ratlines hanging within the radius in front of it, but not the stone.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 80)
    public static void chainShotCutsARopeAndBreaksRatlines(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos aRel = new BlockPos(4, 2, 10), bRel = new BlockPos(14, 2, 10);
        h.setBlock(aRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(bRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        BlockPos a = h.absolutePos(aRel), b = h.absolutePos(bRel);
        h.assertTrue(RopeLines.rig(level, a, b) && RopeLines.joined(level, a, b), "the rope was not rigged");
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(9, y, 16), Blocks.STONE);
        BlockPos ratlinesRel = new BlockPos(10, 2, 15);
        h.setBlock(ratlinesRel, RiggingContent.RATLINES.get().defaultBlockState().setValue(RatlinesBlock.KIND, com.richardsenger.piratesnships.ship.rigging.RatlinesRules.Kind.WALL)
                .setValue(RatlinesBlock.FACING, Direction.NORTH));
        h.assertTrue(level.getBlockState(h.absolutePos(ratlinesRel)).getBlock() instanceof RatlinesBlock, "no ratlines");
        chainShot(h, h.absoluteVec(new Vec3(9.5, 2.6, 4.0)), h.absoluteVec(new Vec3(9.5, 2.6, 16.0)));
        h.succeedWhen(() -> {
            h.assertTrue(!RopeLines.joined(level, a, b) && RopeLines.partners(level, a).isEmpty(), "the rope is still there");
            h.assertFalse(level.getBlockState(h.absolutePos(ratlinesRel)).getBlock() instanceof RatlinesBlock, "the ratlines stand");
            h.assertTrue(shots(h).isEmpty(), "the chain shot is still flying");
            h.assertBlockPresent(Blocks.STONE, new BlockPos(9, 2, 16));
        });
    }

    /** {@code cannons.chain_shot.enabled} off: the gun refuses chain shot after the powder, and keeps the item. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG + "chain_disabled")
    public static void disabledChainShotIsRefused(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CHAIN_ENABLED, false);
        refused(h, CannonContent.CHAIN_SHOT.get());
    }

    /** {@code cannons.grapeshot.enabled} off: the gun refuses grapeshot after the powder, and keeps the item. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG + "grape_disabled")
    public static void disabledGrapeshotIsRefused(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.GRAPE_ENABLED, false);
        refused(h, CannonContent.GRAPESHOT.get());
    }

    private static void refused(GameTestHelper h, Item shot) {
        ServerLevel level = h.getLevel();
        BlockPos pos = CannonGameTests.cannon(h, new BlockPos(1, 1, 4), Direction.EAST);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(CannonService.load(level, pos, p, new ItemStack(Items.GUNPOWDER)).outcome() == CannonService.Outcome.POWDER_IN,
                "powder was refused");
        ItemStack stack = new ItemStack(shot, 2);
        CannonService.Outcome o = CannonService.load(level, pos, p, stack).outcome();
        h.assertTrue(o == CannonService.Outcome.SHOT_DISABLED, "outcome " + o);
        h.assertTrue(stack.getCount() == 2 && level.getBlockState(pos).getValue(CannonBlock.LOAD) == CannonLoad.POWDER,
                "a refused shot changed the gun or the stack");
        h.assertTrue(CannonService.load(level, pos, p, new ItemStack(CombatContent.CANNONBALL.get())).outcome()
                == CannonService.Outcome.BALL_IN, "the cannonball was refused too");
        h.succeed();
    }

    // ------------------------------------------------------------------ grapeshot

    /**
     * A cannon on a floating ship's deck fires grapeshot east at an iron golem standing on a stone platform 6 blocks
     * off: the golem takes one pellet's damage, a villager standing on the firing ship's deck right in front of the
     * muzzle takes none, and the planks behind the golem stand.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 120)
    public static void grapeshotHitsTheMobsOnATargetDeckAndSparesTheFiringCrew(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        CannonGameTests.cannon(h, new BlockPos(10, 9, 10), Direction.EAST); // rear at x 9, muzzle at x 12
        for (int x = 17; x <= 19; x++) {
            for (int z = 8; z <= 13; z++) {
                for (int y = 2; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        for (int y = 9; y <= 12; y++) for (int z = 8; z <= 13; z++) h.setBlock(new BlockPos(21, y, z), Blocks.OAK_PLANKS);
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos master = f.hold(-1, 0, -1);
        h.assertTrue(CannonBlock.isMaster(level.getBlockState(master)), "the cannon is not in the plot");
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.hold(1, 0, -1)).add(0.3, 0, 0));
        Villager crew = h.spawnWithNoFreeWill(EntityType.VILLAGER, h.relativeVec(deck));
        IronGolem target = h.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(18.0, 9, 10.5));
        float crewHealth = crew.getHealth(), targetHealth = target.getHealth();
        load(h, master, CannonContent.GRAPESHOT.get());
        h.runAfterDelay(20, () -> {
            h.assertTrue(CannonService.fire(level, master, null).outcome() == CannonService.Outcome.FIRED, "no shot");
        });
        h.runAfterDelay(21, () -> h.succeedWhen(() -> {
            h.assertTrue(shots(h).isEmpty(), "pellets still flying");
            h.assertTrue(target.getHealth() < targetHealth, "the golem was not hit");
            float pellet = CannonConfig.entityDamage(ShotKind.GRAPE);
            h.assertTrue(Math.abs(target.getHealth() - (targetHealth - pellet)) < 0.01f,
                    "expected health " + (targetHealth - pellet) + " (one pellet in the hurt time), got " + target.getHealth());
            h.assertTrue(crew.getHealth() == crewHealth, "the firing ship's crew was hit: " + crew.getHealth());
            for (int y = 9; y <= 12; y++) h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(21, y, 10));
            crew.discard();
            target.discard();
        }));
    }

    // ------------------------------------------------------------------ crew loading

    private record CrewShip(Fixture f, BlockPos cannon, BlockPos chest, CrewMember crew) {
        StationRef station() {
            return new StationRef(f.ship().id(), cannon);
        }
    }

    /** The crew fixture of {@code CannonCrewGameTests}: a cannon facing west, a chest two blocks from it, a crew member. */
    private static CrewShip crewShip(GameTestHelper h, ItemStack... locker) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        CannonGameTests.cannon(h, new BlockPos(10, 9, 10), Direction.WEST);
        h.setBlock(new BlockPos(12, 9, 10), Blocks.CHEST);
        Fixture f = DryHullGameTests.assemble(h, helm);
        BlockPos cannon = f.hold(-1, 0, -1), chest = f.hold(1, 0, -1);
        if (!(h.getLevel().getBlockEntity(chest) instanceof Container c)) throw new GameTestAssertException("no chest");
        c.clearContent();
        for (int i = 0; i < locker.length; i++) c.setItem(i, locker[i]);
        CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(11, 10, 11));
        CrewStations.AssignResult r = CrewStations.assign(h.getLevel(), crew, cannon);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        return new CrewShip(f, cannon, chest, crew);
    }

    private static int count(GameTestHelper h, CrewShip s, Item item) {
        if (!(h.getLevel().getBlockEntity(s.chest()) instanceof Container c)) throw new GameTestAssertException("no chest");
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static ItemStack[] mixedLocker() {
        return new ItemStack[]{new ItemStack(Items.GUNPOWDER, 3), new ItemStack(CombatContent.CANNONBALL.get(), 2),
                new ItemStack(CannonContent.CHAIN_SHOT.get(), 2), new ItemStack(CannonContent.GRAPESHOT.get(), 2)};
    }

    /** Waits for the crew's load and checks the shot it put in and the one item taken from the chest. */
    private static void assertLoads(GameTestHelper h, CrewShip s, ShotKind kind, Item item, int before) {
        h.succeedWhen(() -> {
            BlockState st = h.getLevel().getBlockState(s.cannon());
            h.assertTrue(st.getValue(CannonBlock.LOAD) == CannonLoad.LOADED, "not loaded yet: " + st.getValue(CannonBlock.LOAD));
            h.assertTrue(st.getValue(CannonBlock.SHOT) == kind, "loaded " + st.getValue(CannonBlock.SHOT) + ", expected " + kind);
            h.assertTrue(count(h, s, item) == before - 1, "the chest holds " + count(h, s, item) + " of " + item);
            s.crew().discard();
        });
    }

    /** With {@code cannons.crew.load_preference} CHAIN, "Load!" takes chain shot from a locker holding all three. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = CONFIG + "crew_prefers_chain")
    public static void crewLoadsThePreferredShotFromAMixedLocker(GameTestHelper h) {
        ConfigOverrides.during(h, CannonConfig.CREW_LOAD_PREFERENCE, ShotKind.CHAIN);
        CrewShip s = crewShip(h, mixedLocker());
        h.assertTrue(Stations.order(h.getLevel(), s.station(), CannonOrder.LOAD) == Stations.OrderResult.STARTED, "no load");
        assertLoads(h, s, ShotKind.CHAIN, CannonContent.CHAIN_SHOT.get(), 2);
    }

    /** By default the crew takes the ball from a mixed locker. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void crewLoadsBallsFromAMixedLockerByDefault(GameTestHelper h) {
        CrewShip s = crewShip(h, mixedLocker());
        h.assertTrue(Stations.order(h.getLevel(), s.station(), CannonOrder.LOAD) == Stations.OrderResult.STARTED, "no load");
        assertLoads(h, s, ShotKind.BALL, CombatContent.CANNONBALL.get(), 2);
    }

    /** A locker with only grapeshot: the crew loads grapeshot rather than nothing. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void crewLoadsWhateverShotTheLockerHolds(GameTestHelper h) {
        CrewShip s = crewShip(h, new ItemStack(Items.GUNPOWDER, 2), new ItemStack(CannonContent.GRAPESHOT.get(), 2));
        h.assertTrue(Stations.order(h.getLevel(), s.station(), CannonOrder.LOAD) == Stations.OrderResult.STARTED, "no load");
        assertLoads(h, s, ShotKind.GRAPE, CannonContent.GRAPESHOT.get(), 2);
    }
}
