package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.BoomBlock;
import com.richardsenger.piratesnships.sailing.block.BoomBlockEntity;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.ForceContribution;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import com.richardsenger.piratesnships.sailing.force.ShipFrame;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.station.StationConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * SAIL5 GameTests: the boom of a triangular sail (docs/design.md §5.2; rules {@link BoomRules}, server side
 * {@link BoomTrim}). The test hull of {@link SailingGameTestsShips} with a mainsail: the head cleat high on the mast's
 * aft face, the boom on the same face at y 10 (the yard rule's column, above the helm), a boomkin of planks over the
 * stern and the tack cleat on it, 5 blocks aft of the boom. The bow is south, so port is east: a wind from the east is
 * on the port beam, and the boom's positive sheet swings it to port.
 */
public final class BoomGameTests {

    private BoomGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BoomGameTests.class);
    }

    /** The beam reach wind of the fore-and-aft tests [blocks/s]. */
    private static final double BEAM_WIND = 3.0;

    /**
     * Hull at x in [x0, x0+4], z in [z0, z0+4] (bow south) with a mainsail on a boom: the fence mast at (x0+2, z0+2) from
     * y 9 to 14, the head cleat at y 14 and the boom at y 10 on its north (aft) face, planks at y 8 from z0−1 to z0−4
     * and the tack cleat on them at z0−4, rigged to the head. Drop 4, the tack 5 aft: area 10. Returns the helm.
     */
    static BlockPos boomedHull(GameTestHelper h, int x0, int z0, SailTrim trim) {
        BlockPos helm = SailingGameTestsShips.bareHull(h, x0, z0);
        SailingGameTestsShips.ballast(h, x0, z0);
        int mx = x0 + 2, mz = z0 + 2;
        for (int y = 9; y <= 14; y++) {
            h.setBlock(new BlockPos(mx, y, mz), Blocks.OAK_FENCE);
        }
        for (int z = z0 - 4; z <= z0 - 1; z++) {
            h.setBlock(new BlockPos(mx, 8, z), Blocks.OAK_PLANKS);
        }
        BlockPos head = new BlockPos(mx, 14, mz - 1);
        BlockPos tack = new BlockPos(mx, 9, z0 - 4);
        h.setBlock(head, SailingGameTestsShips.cleat(AttachFace.WALL, Direction.NORTH).setValue(
                com.richardsenger.piratesnships.sailing.block.CleatBlock.TRIM, trim));
        h.setBlock(new BlockPos(mx, 10, mz - 1), boom(Direction.NORTH, BoomRules.CENTRE));
        h.setBlock(tack, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        TriangularSails.rig(h.getLevel(), h.absolutePos(head), h.absolutePos(tack));
        return helm;
    }

    static BlockState boom(Direction facing, int sheet) {
        return SailingBlocks.BOOM.get().defaultBlockState().setValue(BoomBlock.FACING, facing).setValue(BoomBlock.SHEET, sheet);
    }

    /** The forward force of the ship's boomed (or plain triangular) sail in the last evaluation [N], NaN if none. */
    private static double sailDrive(SailingRuntime rt, String type) {
        ForceBreakdown fb = rt.lastBreakdown();
        if (fb == null) {
            return Double.NaN;
        }
        for (ForceContribution c : fb.contributions()) {
            if (c.source().startsWith("sail[") && c.source().endsWith(":" + type)) {
                return c.force().dot(ShipFrame.FORWARD);
            }
        }
        return Double.NaN;
    }

    private static BlockPos boomOf(SailingRuntime rt) {
        TriangularSail s = rt.boomedSails().get(0).sail();
        return new BlockPos(s.clew().x(), s.clew().y(), s.clew().z());
    }

    private static int sheet(GameTestHelper h, BlockPos plotBoom) {
        BlockState s = h.getLevel().getBlockState(plotBoom);
        return s.getBlock() instanceof BoomBlock ? s.getValue(BoomBlock.SHEET) : -1;
    }

    private static @Nullable TriangleCloth cloth(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockEntity(pos) instanceof CleatBlockEntity be ? be.cloth() : null;
    }

    /**
     * Afloat with the boom held by hand (auto-trim off): sheeted 45° to leeward (starboard) on the port beam it drives;
     * the wind brought round to the starboard beam swings the boom across (a tack) and it drives on the other tack; the
     * boom on the windward side (aback) gives no drive; the jib that a clew cleat makes of the same rig is unchanged.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 260, batch = "pirates_n_ships_config_sailing_boom_beam")
    public static void aBoomedSailDrivesOnBothTacksAndNotAback(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.BOOM_AUTO_TRIM, false);
        SailingGameTestsShips.fixWind(h, 90.0, BEAM_WIND); // from the east: the port beam
        SailingGameTestsShips.basin(h, true);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, boomedHull(h, 17, 17, SailTrim.FULL));
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.sailCount() == 1 && rt.boomedSails().size() == 1, "expected one boomed sail: " + rt.sailPositions());
        BlockPos head = rt.sailPositions().get(0);
        h.assertTrue(rt.typeAt(head) == SailTypes.BOOMED, "type " + rt.typeAt(head));
        h.assertTrue(rt.areaAt(head) == 10.0, "area " + rt.areaAt(head) + ", expected 10 (drop 4, tack 5 aft)");
        TriangleCloth expected = new TriangleCloth(0, -5, -5, 4, true);
        h.assertTrue(expected.equals(cloth(h, head)), "head cloth " + cloth(h, head) + ", expected " + expected);
        BlockPos boom = boomOf(rt);
        h.assertTrue(h.getLevel().getBlockEntity(boom) instanceof BoomBlockEntity be && be.arm() != null
                && be.arm()[0] == 0 && be.arm()[1] == -5, "the boom's arm does not reach the tack");
        // to leeward (starboard, west): a negative sheet on this aft boom
        h.getLevel().setBlock(boom, h.getLevel().getBlockState(boom).setValue(BoomBlock.SHEET, BoomRules.CENTRE - 3), Block.UPDATE_ALL);
        h.assertTrue(rt.sheetAt(head) == BoomRules.CENTRE - 3, "the runtime did not hear the sheet: " + rt.sheetAt(head));
        double[] drive = new double[3];
        h.runAfterDelay(30, () -> {
            drive[0] = sailDrive(rt, "boomed");
            h.assertTrue(drive[0] > 0.05, "no drive on the port tack: " + drive[0]);
            SailingGameTestsShips.fixWind(h, 270.0, BEAM_WIND); // from the west: the starboard beam
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(sheet(h, boom) == BoomRules.CENTRE + 3, "the wind did not swing the boom across: " + sheet(h, boom));
            drive[1] = sailDrive(rt, "boomed");
            h.assertTrue(drive[1] > 0.05, "no drive on the starboard tack: " + drive[1]);
            // aback: the runtime's boom on the windward side (starboard) while the block stays to leeward, so the wind
            // does not swing it back; the force must vanish
            rt.setSheet(boom, BoomRules.CENTRE - 3);
        });
        h.runAfterDelay(80, () -> {
            drive[2] = sailDrive(rt, "boomed");
            h.assertTrue(Math.abs(drive[2]) < 1.0e-9, "an aback boom drives: " + drive[2]);
            // the jib: a clew cleat in the boom's place
            h.getLevel().setBlock(boom, SailingGameTestsShips.cleat(AttachFace.WALL, Direction.NORTH), Block.UPDATE_ALL);
            h.assertTrue(rt.typeAt(head) == SailTypes.FORE_AND_AFT && rt.boomedSails().isEmpty(), "the jib is " + rt.typeAt(head));
            TriangleCloth jib = cloth(h, head);
            h.assertTrue(jib != null && !jib.boomed(), "the jib's cloth " + jib);
        });
        h.runAfterDelay(100, () -> {
            double jib = sailDrive(rt, "fore_and_aft");
            h.assertTrue(jib > 0.05, "the jib does not drive on the beam: " + jib);
            SailingGameTestsShips.clearWind(h);
            com.richardsenger.piratesnships.Constants.LOG.info("[boom test] drive port tack {}, starboard tack {}, aback {}, jib {}",
                    drive[0], drive[1], drive[2], jib);
            h.succeed();
        });
    }

    /**
     * On the ground (the ship still, so the apparent wind is the true wind), auto-trim one step per 2 ticks: from
     * centred the crew sheets the boom to 45° to leeward on the port beam within 20 ticks, all the way out on the port
     * quarter, and when the wind crosses the stern to the starboard quarter the boom gybes across in one go.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200, batch = "pirates_n_ships_config_sailing_boom_trim")
    public static void autoTrimSheetsTheBoomAndItGybesWhenTheWindCrossesTheStern(GameTestHelper h) {
        ConfigOverrides.during(h, StationConfig.TICKS_PER_TRIM_STEP, 2);
        SailingGameTestsShips.fixWind(h, 90.0, BEAM_WIND);
        SailingGameTestsShips.basin(h, false);
        SailingGameTestsShips.Fixture f = SailingGameTestsShips.assemble(h, boomedHull(h, 17, 17, SailTrim.FULL));
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.boomedSails().size() == 1, "no boomed sail");
        BlockPos boom = boomOf(rt);
        List<Integer> afterGybe = new ArrayList<>();
        long[] shiftAt = {Long.MIN_VALUE};
        h.onEachTick(() -> {
            if (shiftAt[0] != Long.MIN_VALUE) {
                afterGybe.add(sheet(h, boom));
            }
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(sheet(h, boom) == BoomRules.CENTRE - 3, "not sheeted to 45 to starboard on the port beam: " + sheet(h, boom));
            SailingGameTestsShips.fixWind(h, 45.0, BEAM_WIND); // from the north-east: the port quarter
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(sheet(h, boom) == 0, "not all the way out to starboard on the port quarter: " + sheet(h, boom));
            SailingGameTestsShips.fixWind(h, 315.0, BEAM_WIND); // from the north-west: the starboard quarter
            shiftAt[0] = h.getTick();
        });
        h.runAfterDelay(46, () -> {
            h.assertTrue(sheet(h, boom) == BoomRules.STEPS - 1, "the boom did not gybe to port: " + sheet(h, boom));
            for (int s : afterGybe) {
                h.assertTrue(s == 0 || s == BoomRules.STEPS - 1, "the gybe went through " + s + ": " + afterGybe);
            }
            SailingGameTestsShips.clearWind(h);
            h.succeed();
        });
    }

    /**
     * On land, with a mainsail rigged on a log mast: the head's cloth is boomed and the boom gets its arm; a hand on the
     * boom's east side sheets it one step clockwise (its end toward the east), on the west side back and beyond, and
     * sneaking centres it; the steps stop at 60 each way.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60)
    public static void aHandSheetsTheBoomAndSneakingCentresIt(GameTestHelper h) {
        for (int x = 1; x <= 12; x++) for (int z = 0; z <= 12; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int y = 2; y <= 8; y++) h.setBlock(new BlockPos(5, y, 7), Blocks.OAK_LOG);
        BlockPos headRel = new BlockPos(5, 8, 6), boomRel = new BlockPos(5, 3, 6), tackRel = new BlockPos(5, 2, 1);
        h.setBlock(headRel, SailingGameTestsShips.cleat(AttachFace.WALL, Direction.NORTH));
        h.setBlock(boomRel, boom(Direction.NORTH, BoomRules.CENTRE));
        h.setBlock(tackRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        BlockPos head = h.absolutePos(headRel), boom = h.absolutePos(boomRel), tack = h.absolutePos(tackRel);
        TriangularSails.rig(h.getLevel(), head, tack);
        TriangleCloth expected = new TriangleCloth(0, -6, -5, 5, true);
        h.assertTrue(expected.equals(cloth(h, head)), "head cloth " + cloth(h, head) + ", expected " + expected);
        h.assertTrue(h.getLevel().getBlockEntity(boom) instanceof BoomBlockEntity be && be.arm() != null
                && be.arm()[0] == 0 && be.arm()[1] == -5, "no arm to the tack");

        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 centre = Vec3.atCenterOf(boom);
        BlockHitResult east = new BlockHitResult(centre.add(0.3, 0.1, -0.2), Direction.EAST, boom, false);
        BlockHitResult west = new BlockHitResult(centre.add(-0.3, 0.1, -0.2), Direction.WEST, boom, false);
        use(h, boom, player, east);
        h.assertTrue(sheet(h, boom) == BoomRules.CENTRE + 1, "east did not sheet clockwise: " + sheet(h, boom));
        use(h, boom, player, west);
        use(h, boom, player, west);
        h.assertTrue(sheet(h, boom) == BoomRules.CENTRE - 1, "west did not sheet back: " + sheet(h, boom));
        player.setShiftKeyDown(true);
        use(h, boom, player, west);
        h.assertTrue(sheet(h, boom) == BoomRules.CENTRE, "sneaking did not centre: " + sheet(h, boom));
        player.setShiftKeyDown(false);
        for (int i = 0; i < 6; i++) {
            use(h, boom, player, west);
        }
        h.assertTrue(sheet(h, boom) == 0, "the boom went past 60: " + sheet(h, boom));
        h.assertTrue(BoomBlock.sheetByHand(h.getLevel(), boom, 99) == BoomRules.STEPS - 1, "sheetByHand does not clamp");
        h.assertTrue(BoomBlock.sheetByHand(h.getLevel(), head, 3) == -1, "a cleat was sheeted");
        // the boom taken away leaves no sail (no cleat below the head)
        h.getLevel().destroyBlock(boom, false);
        h.assertTrue(cloth(h, head) == null, "the cloth outlived its boom: " + cloth(h, head));
        h.succeed();
    }

    private static void use(GameTestHelper h, BlockPos boom, Player player, BlockHitResult hit) {
        h.getLevel().getBlockState(boom).useWithoutItem(h.getLevel(), player, hit);
    }
}
