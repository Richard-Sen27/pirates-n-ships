package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible place a crew member takes at a meal (CRW2, docs/design.md §7.4): beside a pantry or water barrel, a
 * sibling of the station seat and the hammock seat (sable-notes §11.4). It lives inside the ship's plot, kept there by
 * {@code #sable:retain_in_sub_level} and removed with the ship by {@code #sable:destroy_with_sub_level} (tags from
 * {@code crew.content.CrewContentModule}); Sable moves its rider with the ship. The rider stands on the seat's cell
 * and faces the provisions block.
 * <p>
 * The seat ends the meal itself: at its end time ({@code meal_ticks} after it started, saved) or when its provisions
 * block is gone it lets the rider off and removes itself; once the rider is off for any other reason (an order seats it
 * at a station, nightfall puts it into a hammock) it removes itself on its next tick.
 */
public class MealSeat extends Entity {

    /** Plot position of the pantry or water barrel the rider eats at. */
    private BlockPos provisions = BlockPos.ZERO;
    /** Game time the meal ends. */
    private long until;
    private boolean hadRider;

    public MealSeat(EntityType<? extends MealSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Spawns a seat on plot cell {@code spot} beside the provisions block at {@code provisions}, until game time {@code until}. */
    public static MealSeat spawn(ServerLevel level, BlockPos provisions, BlockPos spot, long until) {
        MealSeat seat = new MealSeat(CrewContent.MEAL_SEAT.get(), level);
        seat.provisions = provisions.immutable();
        seat.until = until;
        seat.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, MealRules.facingYaw(spot, provisions, null), 0);
        level.addFreshEntity(seat);
        return seat;
    }

    /** The meal seats standing on plot cell {@code spot}. */
    public static List<MealSeat> on(ServerLevel level, BlockPos spot) {
        return level.getEntitiesOfClass(MealSeat.class, new AABB(spot), s -> s.blockPosition().equals(spot) && !s.isRemoved());
    }

    /** The meal seats around the provisions block at {@code provisions}. */
    public static List<MealSeat> at(ServerLevel level, BlockPos provisions) {
        return level.getEntitiesOfClass(MealSeat.class, new AABB(provisions).inflate(2), s -> s.provisions.equals(provisions) && !s.isRemoved());
    }

    public BlockPos provisions() {
        return provisions;
    }

    public long until() {
        return until;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (isVehicle()) {
            hadRider = true;
        } else if (hadRider || tickCount > 1) {
            discard(); // the rider got up (an order, nightfall) or never sat down
            return;
        }
        if (level.getGameTime() >= until || !(level.getBlockEntity(provisions) instanceof ProvisionContainer)) {
            ejectPassengers();
            discard();
            return;
        }
        // HM2's pattern: keep the rider facing the provisions, turned with the ship
        ShipBody ship = SableShips.containing(level, blockPosition());
        float yaw = MealRules.facingYaw(blockPosition(), provisions, ship == null ? null : ship.orientation());
        for (Entity rider : getPassengers()) {
            float y = rider.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - rider.getYRot());
            rider.setYRot(y);
            rider.setYHeadRot(y);
            if (rider instanceof LivingEntity living) living.setYBodyRot(y);
        }
    }

    /** World position of the seat: the plot position transformed by the ship's pose. */
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
        NbtUtils.readBlockPos(tag, "provisions").ifPresent(p -> provisions = p);
        until = tag.getLong("until");
        hadRider = true; // the rider is loaded with the seat: without one it is a leftover
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("provisions", NbtUtils.writeBlockPos(provisions));
        tag.putLong("until", until);
    }
}
