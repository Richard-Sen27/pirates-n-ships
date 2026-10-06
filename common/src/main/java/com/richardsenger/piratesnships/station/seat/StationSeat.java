package com.richardsenger.piratesnships.station.seat;

import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationBlock;
import com.richardsenger.piratesnships.station.StationContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The invisible seat at a station (docs/design.md §6, sable-notes §11.4). It lives <em>inside the ship's plot</em>
 * (plot coordinates), where Sable keeps it because the type is in {@code #sable:retain_in_sub_level}, and Sable removes
 * it with the ship ({@code #sable:destroy_with_sub_level}, on disassembly and removal for good). Both tags come from
 * {@code StationModule#gatherData}. Its passenger (the crew member) stays in world space: Sable's
 * {@code mixin/entity/entity_rotations_and_riding/EntityMixin#sable$onPositionRider} moves riders of plot entities to
 * the transformed world position every tick, so the passenger moves with the ship. Saved with the plot chunk, the
 * passenger inside its NBT (vanilla passenger saving).
 * <p>
 * The seat removes itself when it has no passenger for a second or when its station block is gone.
 */
public class StationSeat extends Entity {

    private static final int MAX_IDLE_TICKS = 20;

    private BlockPos station = BlockPos.ZERO;
    private int idleTicks;

    public StationSeat(EntityType<? extends StationSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Spawns a seat for the station at plot position {@code station}, standing at plot position {@code spot}. */
    public static StationSeat spawn(ServerLevel level, BlockPos station, BlockPos spot) {
        StationSeat seat = new StationSeat(StationContent.STATION_SEAT.get(), level);
        seat.station = station.immutable();
        seat.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(seat);
        return seat;
    }

    /** All seats of the station at plot position {@code station}. */
    public static List<StationSeat> at(ServerLevel level, BlockPos station) {
        return level.getEntitiesOfClass(StationSeat.class, new AABB(station).inflate(2), s -> s.station.equals(station) && !s.isRemoved());
    }

    /** A seat of this station without passenger, or null. */
    public static @Nullable StationSeat free(ServerLevel level, BlockPos station) {
        for (StationSeat s : at(level, station)) {
            if (!s.isVehicle()) return s;
        }
        return null;
    }

    /** Removes every seat of the station; passengers get off at the seat's world position. */
    public static void removeAt(ServerLevel level, BlockPos station) {
        for (StationSeat s : at(level, station)) {
            s.ejectPassengers();
            s.discard();
        }
    }

    public BlockPos station() {
        return station;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        idleTicks = isVehicle() ? 0 : idleTicks + 1;
        if (idleTicks > MAX_IDLE_TICKS || !(level.getBlockState(station).getBlock() instanceof StationBlock)) {
            ejectPassengers();
            discard();
        }
    }

    /** World position of the seat: the plot position transformed by the ship's pose (the seat itself is in the plot). */
    public Vec3 worldPosition() {
        if (level() instanceof ServerLevel level) {
            ShipBody ship = SableShips.containing(level, blockPosition());
            if (ship != null) {
                return ship.toWorld(position());
            }
        }
        return position();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // the default (seat position) would drop the passenger into the far-away plot coordinates
        return worldPosition().add(0, 0.01, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "station").ifPresent(p -> station = p);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("station", NbtUtils.writeBlockPos(station));
    }
}
