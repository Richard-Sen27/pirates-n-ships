package com.richardsenger.piratesnships.sailing.oars;

import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewPose;
import com.richardsenger.piratesnships.crew.npc.PosedSeat;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The invisible place of a rower at the rail (OAR1, docs/design.md §5.3b): no block, a seat in the ship's plot on the
 * spot's cell, a sibling of the station, meal and hammock seats (sable-notes §11.4: kept in the plot by
 * {@code #sable:retain_in_sub_level}, removed with the ship by {@code #sable:destroy_with_sub_level}; Sable moves the
 * rider with the ship). The rider stands, faces aft and shows {@link CrewPose#ROW_STANDING} ({@link PosedSeat}).
 *
 * <p>The seat removes itself when it has no rider for a second, or when its ship no longer rows (shipped oars, a
 * reload: {@link Rowing}'s state lives in memory, so after a restart the rail rowers stand up and the Row order, kept
 * with the ship, sends them out again); a rider it lets go puts its oar away ({@link Rowing#stowOar}).
 */
public class OarSeat extends Entity implements PosedSeat {

    private static final int MAX_IDLE_TICKS = 20;

    /** The side the oar points to (plot). */
    private Direction outboard = Direction.NORTH;
    private int idleTicks;

    public OarSeat(EntityType<? extends OarSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Spawns a seat on plot cell {@code spot} with its oar toward {@code outboard}. */
    public static OarSeat spawn(ServerLevel level, BlockPos spot, Direction outboard) {
        OarSeat seat = new OarSeat(OarsContent.OAR_SEAT.get(), level);
        seat.outboard = outboard;
        seat.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(seat);
        return seat;
    }

    /** The oar seats on plot cell {@code spot}. */
    public static List<OarSeat> on(ServerLevel level, BlockPos spot) {
        return level.getEntitiesOfClass(OarSeat.class, new AABB(spot), s -> s.blockPosition().equals(spot) && !s.isRemoved());
    }

    public Direction outboard() {
        return outboard;
    }

    private @Nullable ShipBody ship(ServerLevel level) {
        return SableShips.containing(level, blockPosition());
    }

    @Override
    public StationPoses.@Nullable Shown shown(ServerLevel level) {
        ShipBody ship = ship(level);
        Direction aft = ship == null ? null : Rowing.aft(level, ship.id());
        return aft == null ? null : new StationPoses.Shown(CrewPose.ROW_STANDING, aft);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        idleTicks = isVehicle() ? 0 : idleTicks + 1;
        ShipBody ship = ship(level);
        if (idleTicks > MAX_IDLE_TICKS || ship == null || !Rowing.isRowing(ship.id())) {
            for (Entity rider : getPassengers()) {
                if (rider instanceof CrewMember crew) Rowing.stowOar(crew);
            }
            ejectPassengers();
            discard();
        }
    }

    /** World position of the seat: the plot position transformed by the ship's pose. */
    public Vec3 worldPosition() {
        if (level() instanceof ServerLevel level) {
            ShipBody ship = ship(level);
            if (ship != null) {
                return ship.toWorld(position());
            }
        }
        return position();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // the default (seat position) would drop the passenger into the far-away plot coordinates
        return worldPosition().add(0, 0.05, 0);
    }

    @Override
    public boolean dismountsUnderwater() {
        return false;
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
        Direction d = Direction.byName(tag.getString("outboard"));
        outboard = d != null && d.getAxis().isHorizontal() ? d : Direction.NORTH;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("outboard", outboard.getName());
    }
}
