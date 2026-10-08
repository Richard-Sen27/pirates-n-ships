package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible place a crew member lies on in its hammock (HM1): a sibling of the station seat
 * ({@code station.seat.StationSeat}, docs/design.md §6, sable-notes §11.4). It lives inside the ship's plot, kept there
 * by {@code #sable:retain_in_sub_level} and removed with the ship by {@code #sable:destroy_with_sub_level} (tags from
 * {@code crew.content.CrewContentModule}); Sable moves its rider with the ship. It stands on the canvas where the two
 * halves meet, and removes itself when it has had no rider for a second or its bunk is gone.
 * <p>
 * SLP1: a sleeping player lies on one too ({@link #forPlayer()}, {@link PlayerSleep}), in a hammock or in the sea cot
 * on a ship (any {@link Bunk}); that seat stands at the bunk's head half, the player's sleeping position, and puts the
 * player's feet on it. Either way the bunk is taken while the seat is there ({@link ShipBunks#isFree}).
 */
public class HammockSeat extends Entity {

    private static final int MAX_IDLE_TICKS = 20;

    /** Plot position of the bunk's foot. */
    private BlockPos foot = BlockPos.ZERO;
    /** SLP1: a sleeping player's seat, at the head half. */
    private boolean forPlayer;
    private int idleTicks;

    public HammockSeat(EntityType<? extends HammockSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Spawns the seat of the hammock whose foot is at plot position {@code foot} (facing {@code facing}). */
    public static HammockSeat spawn(ServerLevel level, BlockPos foot, Direction facing) {
        HammockSeat seat = new HammockSeat(CrewContent.HAMMOCK_SEAT.get(), level);
        seat.foot = foot.immutable();
        Vec3 p = spot(foot, facing);
        seat.moveTo(p.x, p.y, p.z, facing.toYRot(), 0);
        level.addFreshEntity(seat);
        return seat;
    }

    /**
     * SLP1: spawns the seat of a sleeping player in the bunk whose foot is at {@code foot}, at {@code spot} (plot or
     * world position of the player's feet, {@link PlayerSleep#lyingSpot}).
     */
    public static HammockSeat spawnForPlayer(ServerLevel level, BlockPos foot, Vec3 spot, Direction facing) {
        HammockSeat seat = new HammockSeat(CrewContent.HAMMOCK_SEAT.get(), level);
        seat.foot = foot.immutable();
        seat.forPlayer = true;
        seat.moveTo(spot.x, spot.y, spot.z, facing.toYRot(), 0);
        level.addFreshEntity(seat);
        return seat;
    }

    /**
     * Height of the canvas top where the two halves meet, in pixels: the ART1d model sags from {@code CANVAS_TOP} at
     * the ends to this in the middle. The crew rig's {@code sleep} animation drops the hips onto it from here.
     */
    public static final double SEAM_CANVAS_TOP = 4;

    /** Plot position of the seat: on the canvas, where foot and head meet. */
    public static Vec3 spot(BlockPos foot, Direction facing) {
        return Vec3.atBottomCenterOf(foot).add(facing.getStepX() * 0.5, SEAM_CANVAS_TOP / 16.0, facing.getStepZ() * 0.5);
    }

    /** All seats of the hammock whose foot is at {@code foot}. */
    public static List<HammockSeat> at(ServerLevel level, BlockPos foot) {
        return level.getEntitiesOfClass(HammockSeat.class, new AABB(foot).inflate(2), s -> s.foot.equals(foot) && !s.isRemoved());
    }

    public BlockPos foot() {
        return foot;
    }

    /** Whether this is a sleeping player's seat (SLP1) rather than a crew member's. */
    public boolean forPlayer() {
        return forPlayer;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        idleTicks = isVehicle() ? 0 : idleTicks + 1;
        boolean bunk = Bunk.isFoot(level.getBlockState(foot));
        if (idleTicks > MAX_IDLE_TICKS || !bunk) {
            ejectPassengers();
            discard();
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
        if (forPlayer && passenger instanceof Player && level() instanceof ServerLevel level) {
            return PlayerSleep.standUp(level, foot, passenger.getYRot()); // beside the bunk, as from a bed
        }
        // the default (seat position) would drop the passenger into the far-away plot coordinates
        return worldPosition().add(0, 0.05, 0);
    }

    /** SLP1: a sleeping player's feet lie on the seat (the default would seat it on top, raised by its riding offset). */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (forPlayer && passenger instanceof Player) {
            move.accept(passenger, getX(), getY(), getZ());
        } else {
            super.positionRider(passenger, move);
        }
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
        NbtUtils.readBlockPos(tag, "foot").ifPresent(p -> foot = p);
        forPlayer = tag.getBoolean("for_player");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("foot", NbtUtils.writeBlockPos(foot));
        if (forPlayer) tag.putBoolean("for_player", true);
    }
}
