package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.worldsim.raid.RaidAnnouncer;
import com.richardsenger.piratesnships.worldsim.raid.RaidRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * BELL1: ringing the ship's bell records the ring start and the strike direction in {@link ShipsBellBlockEntity} and
 * sends them to the client (a fresh copy loaded from the update tag reads them), the ring times out after
 * {@code ship_decor.bell_ring_ticks} (also when rung again while ringing), and WS5's raid alarm rings it through the
 * same call. The swing itself is drawn on the client ({@code ShipsBellRenderer}, math in {@link ShipsBellSwing}).
 */
public final class ShipsBellGameTests {

    private ShipsBellGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipsBellGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void ringSyncsStartAndStrikeAndTimesOut(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos.below(), Blocks.OAK_FENCE);
        helper.setBlock(pos, ShipDecor.SHIPS_BELL.get().defaultBlockState().setValue(ShipsBellBlock.FACING, Direction.NORTH));
        ShipsBellBlockEntity bell = bell(helper, pos);
        helper.assertTrue(bell.ringStart() == ShipsBellBlockEntity.NEVER && bell.strike() == null, "a new bell has rung");
        long[] rungAt = new long[1];
        helper.startSequence()
                .thenExecute(() -> {
                    // struck on its south face (the back of a north-facing bell): the strike pushes it north
                    use(helper, player, pos, Direction.SOUTH);
                    rungAt[0] = helper.getLevel().getGameTime();
                    helper.assertBlockProperty(pos, ShipsBellBlock.RINGING, true);
                    helper.assertTrue(bell.ringStart() == rungAt[0], "ring start " + bell.ringStart() + ", rung at " + rungAt[0]);
                    helper.assertTrue(bell.strike() == Direction.NORTH, "strike " + bell.strike());
                    helper.assertTrue(ShipsBellSwing.direction(Direction.NORTH, bell.strike()) == -1.0, "struck from the back swings it forward first");
                    assertClientReads(helper, bell, rungAt[0], Direction.NORTH);
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(pos, ShipsBellBlock.RINGING, false))
                .thenExecute(() -> {
                    long rang = helper.getLevel().getGameTime() - rungAt[0];
                    int ticks = DecorConfig.BELL_RING_TICKS.get();
                    helper.assertTrue(rang >= ticks - 1 && rang <= ticks + 2, "rang " + rang + " ticks, expected " + ticks);
                    double since = bell.sinceRing(helper.getLevel().getGameTime());
                    helper.assertTrue(ShipsBellSwing.bellDegrees(since, ticks, 20.0, 1.0) == 0.0, "the bell still swings after the ring");
                    // the clapper settles a moment after the bell
                    helper.assertFalse(ShipsBellSwing.swinging(since + ShipsBellSwing.CLAPPER_LAG_TICKS, ticks), "still swinging after the ring");
                })
                .thenSucceed();
    }

    /** A wall bell struck on its front swings back first; ringing it again restarts the time and holds RINGING. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void ringingAgainRestartsTheRing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos.south(), Blocks.STONE);
        helper.setBlock(pos, ShipDecor.SHIPS_BELL.get().defaultBlockState().setValue(ShipsBellBlock.FACE, AttachFace.WALL)
                .setValue(ShipsBellBlock.FACING, Direction.NORTH));
        ShipsBellBlockEntity bell = bell(helper, pos);
        int ticks = DecorConfig.BELL_RING_TICKS.get();
        int again = Math.max(1, ticks / 2);
        long[] rungAt = new long[2];
        helper.startSequence()
                .thenExecute(() -> {
                    use(helper, player, pos, Direction.NORTH);
                    rungAt[0] = helper.getLevel().getGameTime();
                    helper.assertTrue(bell.strike() == Direction.SOUTH, "strike " + bell.strike());
                    helper.assertTrue(ShipsBellSwing.direction(Direction.NORTH, bell.strike()) == 1.0, "struck from the front swings it back first");
                })
                .thenIdle(again)
                .thenExecute(() -> {
                    // struck on its east side: a sideways push, half a swing
                    use(helper, player, pos, Direction.EAST);
                    rungAt[1] = helper.getLevel().getGameTime();
                    helper.assertTrue(rungAt[1] > rungAt[0] && bell.ringStart() == rungAt[1], "the second ring did not restart the time");
                    helper.assertTrue(bell.strike() == Direction.WEST, "strike " + bell.strike());
                    helper.assertTrue(Math.abs(ShipsBellSwing.direction(Direction.NORTH, bell.strike())) == ShipsBellSwing.SIDE_STRIKE_SCALE, "side strike");
                    assertClientReads(helper, bell, rungAt[1], Direction.WEST);
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(pos, ShipsBellBlock.RINGING, false))
                .thenExecute(() -> {
                    long rang = helper.getLevel().getGameTime() - rungAt[1];
                    helper.assertTrue(rang >= ticks - 1 && rang <= ticks + 2, "rang " + rang + " ticks after the second ring, expected " + ticks);
                })
                .thenSucceed();
    }

    /** WS5: a sighted raid rings the ship's bells in the settlement through the same ring, with no strike direction. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void raidAlarmRingsTheBell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        helper.setBlock(pos.below(), Blocks.OAK_FENCE);
        helper.setBlock(pos, ShipDecor.SHIPS_BELL.get().defaultBlockState().setValue(ShipsBellBlock.FACING, Direction.EAST));
        ShipsBellBlockEntity bell = bell(helper, pos);
        BlockPos abs = helper.absolutePos(pos);
        ResourceLocation port = Constants.id("bell1_test_port_" + abs.asLong());
        BoundingBox area = BoundingBox.fromCorners(abs.offset(-3, -2, -3), abs.offset(3, 2, 3));
        List<BlockPos> rung = RaidAnnouncer.sighted(helper.getLevel(), port, area);
        RaidAnnouncer.ended(helper.getLevel(), port, area, RaidRules.Outcome.NONE);
        helper.assertTrue(rung.contains(abs), "bells found " + rung);
        helper.assertTrue(bell.ringStart() == helper.getLevel().getGameTime(), "the alarm did not ring the bell");
        helper.assertTrue(bell.strike() == null, "an alarm has no strike: " + bell.strike());
        helper.assertTrue(ShipsBellSwing.direction(Direction.EAST, bell.strike()) == 1.0, "an alarm rings it as from the front");
        helper.assertBlockProperty(pos, ShipsBellBlock.RINGING, true);
        assertClientReads(helper, bell, bell.ringStart(), null);
        helper.succeed();
    }

    /** What the client gets: the block entity's update packet, loaded into a fresh copy like the client's. */
    private static void assertClientReads(GameTestHelper helper, ShipsBellBlockEntity bell, long start, @Nullable Direction strike) {
        ClientboundBlockEntityDataPacket packet = (ClientboundBlockEntityDataPacket) bell.getUpdatePacket();
        helper.assertTrue(packet != null, "no update packet");
        CompoundTag tag = packet.getTag();
        ShipsBellBlockEntity copy = new ShipsBellBlockEntity(bell.getBlockPos(), bell.getBlockState());
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(copy.ringStart() == start, "client ring start " + copy.ringStart() + ", expected " + start);
        helper.assertTrue(copy.strike() == strike, "client strike " + copy.strike() + ", expected " + strike);
    }

    private static ShipsBellBlockEntity bell(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof ShipsBellBlockEntity be) return be;
        throw new net.minecraft.gametest.framework.GameTestAssertException("no ship's bell block entity at " + pos);
    }

    /** A right click with an empty hand on the {@code face} side of {@code pos}. */
    private static void use(GameTestHelper helper, Player player, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(abs);
        Vec3 at = Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.3));
        state.useWithoutItem(helper.getLevel(), player, new BlockHitResult(at, face, abs, false));
    }
}
