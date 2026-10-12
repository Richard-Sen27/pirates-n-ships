package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.BoomBlock;
import com.richardsenger.piratesnships.sailing.block.BoomBlockEntity;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The booms of one ship in the wind, once per game tick (SAIL5, docs/design.md §5.2; the rules are {@link BoomRules}):
 * <ol>
 *   <li><b>The wind swings a boom that is aback across</b> to the same angle on the other side ({@link BoomRules#swing}),
 *       with or without crew and auto-trim. With the wind aft of the beam that is a <b>gybe</b>: the boom slams over
 *       with a sound, and the ship takes {@code sailing.gybe_force} × its mass as a heel impulse toward the new leeward
 *       side through the sailing force group (the same way the sails' own heel goes to Sable). Forward of the beam
 *       (the ship tacked) it swings quietly.</li>
 *   <li><b>Auto-trim</b> ({@code sailing.boom_auto_trim}): the crew sheets each boom one step toward
 *       {@link BoomRules#bestStep} for the apparent wind, every {@code crew_stations.ticks_per_trim_step} (the rate of
 *       the winch's trim; with or without a crew member at the winch), except a boom a player sheeted by hand within
 *       {@code sailing.boom_hand_hold_ticks}.</li>
 * </ol>
 * The apparent wind is the true wind at the ship minus the ship's velocity, turned into the ship frame. The sheet is
 * the boom's block state: a change reaches the runtime through the block-change hook like a trim.
 */
public final class BoomTrim {

    private BoomTrim() {
    }

    public static void tick(ServerLevel level, ShipBody ship, SailingRuntime rt, long now, WindSample wind) {
        List<SailingRuntime.Boomed> booms = rt.boomedSails();
        if (booms.isEmpty()) {
            return;
        }
        Vector3d lin = new Vector3d();
        ship.velocities(lin, new Vector3d());
        Vector3d flowWorld = wind.velocity(new Vector3d()).sub(lin);
        Quaterniond shipToWorld = rt.bow().shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d flow = shipToWorld.transformInverse(flowWorld, new Vector3d()); // x port, z forward
        double minWind = SailingConfig.BOOM_MIN_WIND_ANGLE.get();
        boolean auto = SailingConfig.BOOM_AUTO_TRIM.get();
        int interval = Math.max(1, StationConfig.TICKS_PER_TRIM_STEP.get());
        for (SailingRuntime.Boomed b : booms) {
            TriangularSail sail = b.sail();
            BlockPos pos = new BlockPos(sail.clew().x(), sail.clew().y(), sail.clew().z());
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BoomBlock)) {
                continue;
            }
            // the boom's centred direction (toward the tack) in the ship frame
            Vector3d centred = rt.bow().toShip(new Vector3d(sail.tack().x() - sail.clew().x(), 0.0,
                    sail.tack().z() - sail.clew().z()), new Vector3d());
            int step = state.getValue(BoomBlock.SHEET);
            BoomRules.Swing swing = BoomRules.swing(flow.x, flow.z, centred.x, centred.z, step, minWind);
            if (swing != BoomRules.Swing.NONE) {
                int across = BoomRules.mirror(step);
                level.setBlock(pos, state.setValue(BoomBlock.SHEET, across), Block.UPDATE_ALL);
                if (swing == BoomRules.Swing.GYBE) {
                    gybe(level, ship, rt, pos, centred, across);
                }
                continue;
            }
            if (!auto || (now + Math.floorMod(pos.asLong(), interval)) % interval != 0
                    || level.getBlockEntity(pos) instanceof BoomBlockEntity be && be.heldByHand(now)) {
                continue;
            }
            int best = BoomRules.bestStep(flow.x, flow.z, centred.x, centred.z, step, minWind);
            if (best != step) {
                level.setBlock(pos, state.setValue(BoomBlock.SHEET, BoomRules.stepToward(step, best)), Block.UPDATE_ALL);
            }
        }
    }

    /** The boom slammed across to {@code step}: the bang, and the heel toward the side it went to. */
    private static void gybe(ServerLevel level, ShipBody ship, SailingRuntime rt, BlockPos pos, Vector3d centred, int step) {
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.55f);
        double force = SailingConfig.GYBE_FORCE.get();
        double[] end = BoomRules.turn(centred.x, centred.z, BoomRules.angle(step));
        int side = BoomRules.side(end[0], end[1]);
        if (force > 0.0 && side != 0) {
            // a roll about +z (forward) lifts port (+x): heeling to port (+1) is a negative roll
            rt.addGybeImpulse(-side * force * ship.mass());
        }
    }
}
