package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.ClothGeometry;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.DisassemblyMath;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * G2 GameTests: the sail winch's facing (the side its crank is on), and the cloth of yard and cleat heads right after
 * a ship is disassembled with a turn (docs/design.md §5.2).
 */
public final class SailingGameTestsRigging {

    private SailingGameTestsRigging() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsRigging.class);
    }

    /**
     * A player placing a winch while looking in each of the four directions gets the crank on the side facing them:
     * looking south (yaw 0) the crank points north, and so on.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void winchCrankFacesThePlacingPlayer(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Direction[] looking = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int i = 0; i < looking.length; i++) {
            BlockPos floor = new BlockPos(1 + 2 * i, 1, 4);
            h.setBlock(floor, Blocks.STONE);
            player.setYRot(looking[i].toYRot());
            ItemStack stack = new ItemStack(SailingBlocks.SAIL_WINCH.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(floor)).add(0, 0.5, 0), Direction.UP,
                    h.absolutePos(floor), false);
            InteractionResult r = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
            h.assertTrue(r.consumesAction(), "placing the winch failed: " + r);
            BlockState s = h.getBlockState(floor.above());
            h.assertTrue(s.is(SailingBlocks.SAIL_WINCH.get()), "no winch placed, found " + s);
            Direction crank = s.getValue(SailWinchBlock.FACING);
            h.assertTrue(crank == looking[i].getOpposite(),
                    "looking " + looking[i] + " placed the crank " + crank + ", expected " + looking[i].getOpposite());
        }
        h.succeed();
    }

    /**
     * The default state (winches from before the facing existed) is the unrotated model, crank east; turning moves the
     * crank clockwise from above, four quarter turns and every mirror twice give the state back.
     */
    @ModGameTest
    public static void winchFacingRotatesAndMirrors(GameTestHelper h) {
        SailWinchBlock winch = SailingBlocks.SAIL_WINCH.get();
        h.assertTrue(winch.defaultBlockState().getValue(SailWinchBlock.FACING) == Direction.EAST, "default facing is not east");
        BlockState east = winch.defaultBlockState();
        h.assertTrue(east.rotate(Rotation.CLOCKWISE_90).getValue(SailWinchBlock.FACING) == Direction.SOUTH, "east turned clockwise is not south");
        h.assertTrue(east.rotate(Rotation.CLOCKWISE_180).getValue(SailWinchBlock.FACING) == Direction.WEST, "east turned 180° is not west");
        h.assertTrue(east.rotate(Rotation.COUNTERCLOCKWISE_90).getValue(SailWinchBlock.FACING) == Direction.NORTH, "east turned counterclockwise is not north");
        h.assertTrue(east.mirror(Mirror.FRONT_BACK).getValue(SailWinchBlock.FACING) == Direction.WEST, "east mirrored front-back is not west");
        h.assertTrue(east.mirror(Mirror.LEFT_RIGHT).getValue(SailWinchBlock.FACING) == Direction.EAST, "east mirrored left-right changed");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = east.setValue(SailWinchBlock.FACING, d);
            BlockState turned = s;
            for (int i = 0; i < 4; i++) {
                turned = turned.rotate(Rotation.CLOCKWISE_90);
            }
            h.assertTrue(turned == s, "four quarter turns changed " + d);
            for (Rotation r : Rotation.values()) {
                h.assertTrue(s.rotate(r).rotate(inverse(r)) == s, "rotation " + r + " does not round-trip " + d);
            }
            for (Mirror m : Mirror.values()) {
                h.assertTrue(s.mirror(m).mirror(m) == s, "mirror " + m + " does not round-trip " + d);
            }
        }
        h.succeed();
    }

    private static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }

    /**
     * A ship with a square sail, a triangular sail and a winch (crank north) is turned by 88° and disassembled: it lands a
     * quarter turn round, and the heads' cloth matches the turned rig on the next ticks, not only after the periodic
     * re-check (set to its maximum here, so only the first-tick check can correct it). The winch's crank turns with it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100, batch = "pirates_n_ships_config_sailing_disassembly_cloth")
    public static void turnedDisassemblyRefreshesHeadCloth(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.YARD_REFRESH_TICKS, 1200);
        SailingGameTestsShips.basin(h, false);
        BlockPos helm = SailingGameTestsShips.foreAndAftHull(h, 17, 17, SailTrim.FURLED);
        SailingGameTestsShips.rig(h, 19, 17, 1, 3, SailTrim.FURLED); // square sail over the stern wall, head at (19, 12, 17)
        h.setBlock(new BlockPos(20, 9, 19), SailingBlocks.SAIL_WINCH.get().defaultBlockState().setValue(SailWinchBlock.FACING, Direction.NORTH));
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        ShipBody ship = f.ship();
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.sailCount() == 2, "expected two sails, got " + rt.sailCount() + " at " + rt.sailPositions());
        BlockPos yardHead = null, cleatHead = null;
        for (BlockPos p : rt.sailPositions()) {
            if (h.getLevel().getBlockState(p).getBlock() instanceof YardBlock) {
                yardHead = p;
            } else if (h.getLevel().getBlockState(p).getBlock() instanceof CleatBlock) {
                cleatHead = p;
            }
        }
        h.assertTrue(yardHead != null && cleatHead != null, "missing a head: yard " + yardHead + ", cleat " + cleatHead);
        BlockPos tack = TriangularSails.partner(h.getLevel(), cleatHead);
        h.assertTrue(tack != null, "the head cleat has no stay");
        h.assertTrue(new ClothGeometry(true, 1.5f, 1.5f, 1.5f, 1.5f, 3).equals(yardCloth(h, yardHead)),
                "yard cloth before " + yardCloth(h, yardHead));
        h.assertTrue(new TriangleCloth(0, -5, 5, 5).equals(cleatCloth(h, cleatHead)), "cleat cloth before " + cleatCloth(h, cleatHead));
        BlockPos winch = ship.plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())).findFirst().orElseThrow();
        SailWinchBlock.use(h.getLevel(), winch);
        h.assertTrue(rt.trimAt(yardHead) == SailTrim.HALF && rt.trimAt(cleatHead) == SailTrim.HALF,
                "the north-facing winch did not set half sail");

        BlockPos helmPlot = ship.plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(AssemblyContent.HELM.get())).findFirst().orElseThrow();
        ship.setOrientation(new Quaterniond().rotateAxis(Math.toRadians(88), 0, 1, 0));
        BlockPos goal = BlockPos.containing(ship.toWorld(Vec3.atCenterOf(helmPlot)));
        int turns = DisassemblyMath.quarterTurns(ship.orientation());
        h.assertTrue(turns == 1, "expected one quarter turn, got " + turns);
        BlockPos yardW = DisassemblyMath.target(yardHead, helmPlot, goal, turns);
        BlockPos cleatW = DisassemblyMath.target(cleatHead, helmPlot, goal, turns);
        BlockPos tackW = DisassemblyMath.target(tack, helmPlot, goal, turns);
        BlockPos winchW = DisassemblyMath.target(winch, helmPlot, goal, turns);
        AssemblyResult r = ShipAssembler.disassemble(ship, helmPlot, null);
        h.assertTrue(r.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + r);

        ClothGeometry yardExpected = new ClothGeometry(false, 1.5f, 1.5f, 1.5f, 1.5f, 3);
        TriangleCloth cleatExpected = new TriangleCloth(tackW.getX() - cleatW.getX(), tackW.getY() - cleatW.getY(),
                tackW.getZ() - cleatW.getZ(), 5);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getLevel().getBlockState(yardW).getValue(YardBlock.AXIS) == Direction.Axis.Z, "the yard did not turn");
            h.assertTrue(yardExpected.equals(yardCloth(h, yardW)), "yard cloth after " + yardCloth(h, yardW) + ", expected " + yardExpected);
            h.assertTrue(cleatW.equals(TriangularSails.partner(h.getLevel(), tackW)), "the stay did not survive the turn");
            h.assertTrue(cleatExpected.equals(cleatCloth(h, cleatW)), "cleat cloth after " + cleatCloth(h, cleatW) + ", expected " + cleatExpected);
            h.assertTrue(h.getLevel().getBlockState(winchW).getValue(SailWinchBlock.FACING) == Direction.WEST,
                    "the winch's crank did not turn with the ship: " + h.getLevel().getBlockState(winchW));
            h.succeed();
        });
    }

    private static ClothGeometry yardCloth(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockEntity(pos) instanceof YardBlockEntity be ? be.geometry() : null;
    }

    private static TriangleCloth cleatCloth(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockEntity(pos) instanceof CleatBlockEntity be ? be.cloth() : null;
    }
}
