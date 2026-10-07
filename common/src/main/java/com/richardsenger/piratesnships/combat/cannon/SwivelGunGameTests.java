package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests;
import com.richardsenger.piratesnships.ship.hull.runtime.DryHullGameTests.Fixture;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.order.WhistleOrder;
import com.richardsenger.piratesnships.station.order.WhistleOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * The swivel gun in a real server (docs/design.md §8.2, P2): it mounts on a fence or a full block and not in mid-air,
 * falls off when its mount goes, turns to the aiming player's view, fires along its aim when the player lets go, keeps
 * planks whole with the default {@code blocks_per_hit} of 0, and fires on the whistle's "Fire!" when a crew member mans
 * it. Calls go through {@link SwivelService}, the same calls the block, the release payload and the station make. A mock
 * player is not in the level, so the gun lets go of it on the next tick: each aim test starts and releases in one tick.
 */
public final class SwivelGunGameTests {

    private SwivelGunGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SwivelGunGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A swivel gun at {@code rel} on a stone block; returns its absolute position. */
    private static BlockPos swivelOnStone(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel, CannonContent.SWIVEL_GUN.get());
        return h.absolutePos(rel);
    }

    private static SwivelGunBlockEntity be(GameTestHelper h, BlockPos pos) {
        if (h.getLevel().getBlockEntity(pos) instanceof SwivelGunBlockEntity be) return be;
        throw new GameTestAssertException("no swivel gun block entity at " + pos);
    }

    private static CannonLoad load(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockState(pos).getValue(SwivelGunBlock.LOAD);
    }

    /** Powder and the configured ammo, with a creative player (no items used). */
    private static void loadFully(GameTestHelper h, BlockPos pos) {
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ServerLevel level = h.getLevel();
        h.assertTrue(SwivelService.load(level, pos, creative, new ItemStack(Items.GUNPOWDER)).outcome() == SwivelService.Outcome.POWDER_IN,
                "powder was refused");
        h.assertTrue(SwivelService.load(level, pos, creative, new ItemStack(SwivelService.ammoItem())).outcome()
                == SwivelService.Outcome.SHOT_IN, "the shot was refused");
    }

    /** A mock player looking along view yaw / pitch (Minecraft's convention: pitch negative = up). */
    private static Player looking(GameTestHelper h, float yaw, float pitch) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setYRot(yaw);
        p.setXRot(pitch);
        return p;
    }

    private static void assertNear(GameTestHelper h, Vec3 actual, Vec3 expected, double tolerance, String what) {
        h.assertTrue(actual.distanceTo(expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    // ------------------------------------------------------------------ mounting

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void mountsOnAFenceOrAFullBlockButNotInMidAir(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_FENCE);
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        h.setBlock(new BlockPos(6, 1, 2), Blocks.COBBLESTONE_WALL);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(CannonContent.SWIVEL_GUN.get(), 5);

        h.assertTrue(CannonGameTests.place(h, player, stack, new BlockPos(2, 2, 2)), "refused on a fence");
        h.assertTrue(CannonGameTests.place(h, player, stack, new BlockPos(6, 2, 2)), "refused on a wall");
        h.assertTrue(CannonGameTests.place(h, player, stack, new BlockPos(4, 2, 4)), "refused on a full block");
        h.assertFalse(CannonGameTests.place(h, player, stack, new BlockPos(6, 4, 6)), "placed in mid-air");
        h.assertBlockPresent(CannonContent.SWIVEL_GUN.get(), new BlockPos(2, 2, 2));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 4, 6));
        h.assertTrue(stack.getCount() == 2, "expected 3 placed, " + (5 - stack.getCount()) + " used");

        level.destroyBlock(h.absolutePos(new BlockPos(2, 1, 2)), false);
        h.assertBlockPresent(Blocks.AIR, new BlockPos(2, 2, 2)); // the gun falls off its mount
        h.succeed();
    }

    // ------------------------------------------------------------------ aiming and firing

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void aimingTurnsTheGunToThePlayersView(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = swivelOnStone(h, new BlockPos(4, 2, 4));
        SwivelGunBlockEntity be = be(h, pos);

        Player west = looking(h, 90, -20); // west, 20° up
        h.assertTrue(SwivelService.startAim(level, pos, west).outcome() == SwivelService.Outcome.AIMING_UNLOADED, "aiming refused");
        h.assertTrue(west.getUUID().equals(be.operator()), "the player is not the gun's operator");
        h.assertTrue(Math.abs(be.yaw() - 90) < 1e-3 && Math.abs(be.elevation() - 20) < 1e-3,
                "expected yaw 90 / elevation 20, got " + be.yaw() + " / " + be.elevation());
        h.assertTrue(SwivelService.release(level, pos, west).outcome() == SwivelService.Outcome.NOT_LOADED,
                "an unloaded gun did something on release");
        h.assertTrue(be.operator() == null, "the gun still has an operator after the release");

        Player steep = looking(h, -135, -80); // north-east, almost straight up
        SwivelService.startAim(level, pos, steep);
        h.assertTrue(Math.abs(be.yaw() + 135) < 1e-3, "yaw " + be.yaw());
        h.assertTrue(Math.abs(be.elevation() - CannonConfig.SWIVEL_MAX_ELEVATION.get()) < 1e-3,
                "the elevation is not clamped to the maximum: " + be.elevation());
        h.assertTrue(SwivelService.release(level, pos, west) == null, "another player's release was taken");
        h.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120)
    public static void releasingFiresAShotAlongTheAim(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = swivelOnStone(h, new BlockPos(1, 2, 4));
        Player player = looking(h, -90, 0); // east, level
        h.assertTrue(SwivelService.release(level, pos, player) == null, "a release without aiming was taken");
        loadFully(h, pos);

        SwivelService.startAim(level, pos, player);
        SwivelService.Use use = SwivelService.release(level, pos, player);
        h.assertTrue(use != null && use.outcome() == SwivelService.Outcome.FIRED && use.ball() != null,
                "no shot on release: " + (use == null ? null : use.outcome()));
        CannonballEntity ball = use.ball();
        Vec3 at = ball.position();
        Vec3 v = ball.getDeltaMovement();
        h.assertTrue(ball.getOwner() == player, "the aiming player does not own the shot");
        h.assertTrue(Math.abs(ball.damage() - CannonConfig.swivelDamage()) < 1e-4, "damage " + ball.damage());
        h.assertTrue(ball.getItem().is(SwivelService.ammoItem()), "the shot is not drawn as the ammo");
        ball.discard();
        assertNear(h, at, SwivelRules.pivot(pos.getX(), pos.getY(), pos.getZ()).add(SwivelRules.MUZZLE_LENGTH, 0, 0), 1e-6, "muzzle");
        assertNear(h, v, new Vec3(CannonConfig.SWIVEL_VELOCITY.get(), 0, 0), 1e-6, "shot velocity");
        h.assertTrue(CannonConfig.SWIVEL_VELOCITY.get() < CannonConfig.MUZZLE_VELOCITY.get(), "the swivel is not the weaker gun");
        h.assertTrue(load(h, pos) == CannonLoad.EMPTY, "the gun is still loaded");

        ItemStack powder = new ItemStack(Items.GUNPOWDER);
        h.assertTrue(SwivelService.load(level, pos, player, powder).outcome() == SwivelService.Outcome.RELOADING,
                "powder went into a hot barrel");
        h.runAfterDelay(CannonConfig.SWIVEL_RELOAD_TICKS.get() + 1, () -> {
            h.assertTrue(SwivelService.load(level, pos, player, powder).outcome() == SwivelService.Outcome.POWDER_IN,
                    "no powder after the reload time");
            h.succeed();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 60)
    public static void withNoBlocksPerHitTheShotLeavesThePlanksWhole(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(CannonConfig.SWIVEL_BLOCKS_PER_HIT.get() == 0, "the default swivel blocks_per_hit is not 0");
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(5, y, 4), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(6, y, 4), Blocks.STONE);
        }
        BlockPos pos = swivelOnStone(h, new BlockPos(2, 2, 4));
        loadFully(h, pos);
        Player player = looking(h, -90, 0);
        SwivelService.startAim(level, pos, player);
        SwivelService.release(level, pos, player);
        h.runAfterDelay(10, () -> {
            h.assertTrue(h.getEntities(CannonContent.CANNONBALL.get()).isEmpty(), "the shot did not stop at the plank");
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(5, 2, 4));
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ crew

    /**
     * A swivel gun on the deck of a floating ship, loaded, aimed west at a stone backstop and manned by a crew member:
     * the whistle's "Fire!" reaches that crew member (the swivel is a station of its own kind that takes the cannon's
     * fire order) and the gun fires after the fuse.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200)
    public static void whistleFireFiresALoadedSwivelMannedByCrew(GameTestHelper h) {
        DryHullGameTests.basin(h, 0, 23, true);
        BlockPos helm = DryHullGameTests.hull(h, 9, false);
        h.setBlock(new BlockPos(10, 9, 10), CannonContent.SWIVEL_GUN.get()); // on the deck planks
        for (int z = 1; z < 23; z++) {
            for (int y = 9; y <= 11; y++) h.setBlock(new BlockPos(2, y, z), Blocks.STONE); // backstop for the shot
        }
        Fixture f = DryHullGameTests.assemble(h, helm);
        ServerLevel level = h.getLevel();
        BlockPos gun = f.hold(-1, 0, -1);
        h.assertTrue(level.getBlockState(gun).is(CannonContent.SWIVEL_GUN.get()), "the swivel gun is not in the plot");
        be(h, gun).setAim(new SwivelRules.Aim(90, 0)); // west, in the ship's frame
        loadFully(h, gun);

        CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(11, 10, 11));
        CrewStations.AssignResult r = CrewStations.assign(level, crew, gun);
        h.assertTrue(r == CrewStations.AssignResult.ASSIGNED, "assign: " + r);
        Player captain = h.makeMockPlayer(GameType.SURVIVAL);
        captain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StationContent.CAPTAINS_WHISTLE.get()));
        Vec3 deck = f.ship().toWorld(Vec3.atBottomCenterOf(f.helmPlot()).add(1, 0, 1));
        captain.moveTo(deck.x, deck.y, deck.z);

        WhistleOrders.Result order = WhistleOrders.handle(captain, WhistleOrder.FIRE.id());
        h.assertTrue(order.outcome() == WhistleOrders.Outcome.ISSUED && order.crew() == 1, "whistle fire: " + order);
        h.runAfterDelay(1, () -> h.succeedWhen(() -> {
            h.assertTrue(load(h, gun) == CannonLoad.EMPTY, "the crew member has not fired the swivel gun yet");
            h.getEntities(CannonContent.CANNONBALL.get()).forEach(CannonballEntity::discard);
            crew.discard();
        }));
    }
}
