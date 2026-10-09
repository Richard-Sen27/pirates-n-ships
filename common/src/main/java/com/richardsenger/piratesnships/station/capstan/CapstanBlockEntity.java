package com.richardsenger.piratesnships.station.capstan;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.sailing.anchor.AnchorStatus;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipAnchor;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.station.StationRef;
import com.richardsenger.piratesnships.station.StationState;
import com.richardsenger.piratesnships.station.Stations;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan's block entity (CAP2, docs/design.md §6 "Capstan in motion"; the block is {@code sailing.block.CapstanBlock}).
 *
 * <p>Server, every tick ({@link #serverTick}): {@link #turning()} follows the capstan's anchor work, whoever does it
 * (CAP3, {@link CapstanTurning#of(Object, AnchorState.Phase, boolean)}): while the capstan station carries out an anchor
 * order with its occupant at the station ({@link Stations#isWorking}) it turns by the order as in CAP2 (a drop order's
 * work turns it before the anchor goes); otherwise it follows the ship's anchor when it hangs from this capstan,
 * {@link CapstanTurning#RAISING} while the chain comes in and {@link CapstanTurning#DROPPING} while it runs out, so a
 * player's use and the automatic raise turn it too; {@link CapstanTurning#NONE} while the anchor holds, hangs at the
 * chain's end or is stowed. Each change goes to clients at once as a block update, with the turning rate
 * ({@code crew_stations.capstan.turn_degrees_per_tick}), so the client draws the bars at the server's rate. With
 * {@code crew_stations.capstan.animation} off it stays {@code NONE}. When a crew member (not a player) rides the station
 * seat while its order turns the bars, the seat walks round the drum ({@link CapstanWalk}) and comes back to its spot
 * ({@link StationSeat#home()}) afterwards: the walk stays tied to the crew order (CAP2), a player or the anchor alone
 * walks nobody. The seat is an entity without collision, so blocks beside the capstan do not stop the walk. Nothing is
 * saved: a reloaded capstan picks the anchor's motion up again on its next tick.
 *
 * <p>Client: {@link #spin()} integrates the drawn angle from the synced turning; {@code client/CapstanBarsRenderer}
 * draws the bars with it.
 */
public class CapstanBlockEntity extends BlockEntity {

    static final String TAG_TURNING = "turning";
    static final String TAG_RATE = "rate";

    /** The synced turning: set by the server tick, received by clients. */
    private CapstanTurning turning = CapstanTurning.NONE;
    /** The synced turning rate in degrees per tick. */
    private double rate;
    /** Server: the walk of the seat round the drum, and the seat it belongs to. */
    private final CapstanWalk walk = new CapstanWalk();
    private @Nullable UUID walkingSeat;
    /** Server: the plot direction the walking crew member faces (along the circle), null while it does not walk. */
    private @Nullable Vec3 heading;
    /** Client: the drawn bars. */
    private final CapstanSpin spin = new CapstanSpin();

    public CapstanBlockEntity(BlockPos pos, BlockState state) {
        super(CapstanContent.CAPSTAN_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapstanBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickServer(serverLevel, pos, state);
        }
    }

    private void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        StationRef ref = Stations.at(level, pos);
        StationState<Object> st = ref == null ? null : Stations.state(ref);
        boolean animate = CapstanConfig.ANIMATION.get();
        Object order = ref != null && st != null && Stations.isWorking(level, ref) ? st.order() : null;
        CapstanTurning crew = animate ? CapstanTurning.of(order) : CapstanTurning.NONE;
        CapstanTurning now = CapstanTurning.NONE;
        if (animate) {
            ShipBody ship = SableShips.containing(this);
            SailingRuntime rt = ship == null ? null : SailingRuntimes.get(level, ship.id());
            ShipAnchor a = rt == null ? null : rt.anchor();
            boolean ours = a != null && a.capstan().equals(pos);
            AnchorStatus status = rt == null ? null : rt.anchorStatus();
            now = CapstanTurning.of(order, ours ? a.state().phase() : null, status != null && status.taut());
        }
        double r = CapstanConfig.TURN_DEGREES_PER_TICK.get();
        if (now != turning || (now != CapstanTurning.NONE && r != rate)) {
            turning = now;
            rate = r;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        walkSeat(level, pos, st, crew, r);
    }

    /** Moves the station seat one tick round the drum (while a crew member's order turns it) or back to its spot. */
    private void walkSeat(ServerLevel level, BlockPos pos, @Nullable StationState<Object> st, CapstanTurning crewTurning, double r) {
        List<StationSeat> seats = StationSeat.at(level, pos);
        StationSeat seat = seats.isEmpty() ? null : seats.get(0);
        if (seat == null || !seat.getUUID().equals(walkingSeat)) {
            walk.reset(); // a new seat starts at its spot
            walkingSeat = seat == null ? null : seat.getUUID();
        }
        heading = null;
        if (seat == null) {
            return;
        }
        Vec3 centre = Vec3.atBottomCenterOf(pos);
        Vec3 home = seat.home();
        double hx = home.x - centre.x;
        double hz = home.z - centre.z;
        boolean crew = st != null && st.occupant() != null && !st.occupant().player()
                && seat.getFirstPassenger() instanceof CrewMember;
        int sign = crew && canWalk(hx, home.y - centre.y, hz) ? crewTurning.sign() : 0;
        if (sign == 0 && walk.home()) {
            return; // at its spot and staying there: leave the seat alone
        }
        walk.tick(sign, r);
        double[] o = walk.offset(hx, hz, CapstanConfig.WALK_RADIUS.get());
        seat.setPos(centre.x + o[0], home.y, centre.z + o[1]);
        if (sign != 0) {
            double[] d = CapstanWalk.heading(o, sign);
            heading = new Vec3(d[0], 0, d[1]);
        }
    }

    /**
     * Whether a seat whose spot lies at {@code (dx, dy, dz)} from the capstan's bottom centre walks round it: a spot
     * beside the capstan on its level (the usual neighbour), not one on top of it (no room beside it). Pure.
     */
    static boolean canWalk(double dx, double dy, double dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(dy) < 0.5 && d > 0.5 && d < 2.5;
    }

    /** The synced turning (server: as last sent; client: as last received). */
    public CapstanTurning turning() {
        return turning;
    }

    /** The synced turning rate in degrees per tick. */
    public double rate() {
        return rate;
    }

    /** Server: the plot direction the walking crew member faces, or null when nobody walks the capstan round. */
    public @Nullable Vec3 walkHeading() {
        return heading;
    }

    /** Server: the walk of the seat (its angle from the spot). */
    public CapstanWalk walk() {
        return walk;
    }

    /** Client: the drawn bars' angle at {@code now} (game ticks plus partial tick), following the synced turning. */
    public double barAngle(double now) {
        int sign = turning.sign();
        if (sign != spin.sign() || (sign != 0 && rate != spin.rate())) {
            spin.change(sign, rate, now);
        }
        return spin.angle(now);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        turning = CapstanTurning.byId(tag.getByte(TAG_TURNING));
        rate = tag.getDouble(TAG_RATE);
    }

    /** What a client needs to draw the capstan: the turning and its rate. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte(TAG_TURNING, (byte) turning.ordinal());
        tag.putDouble(TAG_RATE, rate);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
