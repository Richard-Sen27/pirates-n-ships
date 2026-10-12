package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.mob.village.ResidentPosts.Bed;
import com.richardsenger.piratesnships.mob.village.ResidentPosts.Post;
import com.richardsenger.piratesnships.mob.village.VillageRoutine.Activity;
import com.richardsenger.piratesnships.mob.village.VillageRoutine.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * A villager's day (LIFE1): every tick it asks {@link VillageRoutine#step} what its role does now and goes there. The
 * crew's station work is the pattern: walk to the post, then turn to the work and show the {@code work} pose until the
 * schedule moves on.
 *
 * <ul>
 *   <li>{@link Activity#WORK}: to its station of the step's index, facing it, {@code work} pose;</li>
 *   <li>{@link Activity#HOME}: to its first station, idle;</li>
 *   <li>{@link Activity#SIT}: to the first free seat of its plan, onto a {@link BenchSeat} ({@code sit}); none free:
 *       home;</li>
 *   <li>{@link Activity#SLEEP}: to its bed, lying on it along the bed, head on the pillow ({@code sleep}); no bed:
 *       home;</li>
 *   <li>{@link Activity#LAMP_ROUND}: a lantern in hand, from lamp post to lamp post ({@link LampRoute}), a pause with
 *       the {@code work} pose at each, then home;</li>
 *   <li>{@link Activity#WANDER} (and {@code mobs.villagers.routines} off): the goal stands aside and the villager strolls
 *       as a sailor does.</li>
 * </ul>
 * A place it cannot reach within {@code mobs.villagers.stuck_ticks} (a bed up a ladder, a crowd in the way) it steps to
 * directly. Panic and the flight from danger have the higher priority: they stop this goal, which gets the villager up
 * from its bed or seat first.
 */
public class ResidentRoutineGoal extends Goal {

    static final double SPEED = 0.6;
    /** Horizontal blocks from a post within which a villager has arrived. */
    static final double ARRIVE = 1.0;
    static final double ARRIVE_BED = 1.8;
    static final double ARRIVE_LAMP = 2.2;
    /** Ticks at each lamp post. */
    static final int LAMP_PAUSE = 60;
    /** Ticks between path requests while walking. */
    static final int REPATH = 40;

    private final VillageResident mob;
    private @Nullable Step step;
    private boolean arrived;
    private int stuck;
    private int repath;
    private @Nullable Post seat;
    private int lampIndex;
    private int lampPause;

    public ResidentRoutineGoal(VillageResident mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    /** What the routine has the villager do now ({@link Activity#WANDER} with the routines off). */
    Step now() {
        return VillageRoutine.step(mob.role(), mob.level().getDayTime(), VillagerConfig.ROUTINES.get());
    }

    @Override
    public boolean canUse() {
        return mob.isAlive() && now().activity() != Activity.WANDER;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        step = null;
    }

    @Override
    public void stop() {
        leave();
        step = null;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Step now = now();
        if (!now.equals(step)) {
            leave();
            step = now;
            begin(level);
        }
        ResidentPlan plan = mob.plan();
        switch (step.activity()) {
            case WORK -> station(plan.station(step.station()), true);
            case HOME -> station(plan.home(), false);
            case SIT -> sit(level, plan);
            case SLEEP -> sleep(plan);
            case LAMP_ROUND -> lampRound(plan);
            case WANDER -> {
            }
        }
    }

    // --- the steps ----------------------------------------------------------------------------------------------

    private void begin(ServerLevel level) {
        arrived = false;
        stuck = 0;
        repath = 0;
        seat = null;
        lampIndex = 0;
        lampPause = 0;
        if (step.activity() == Activity.SIT) seat = freeSeat(level, mob.plan());
        if (step.activity() == Activity.LAMP_ROUND) {
            if (mob.lamps() == null) mob.setLamps(Residents.lampsNear(level, mob));
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LANTERN));
        }
    }

    /** Gets up from bed or seat, ends the work pose, lets go of its facing and puts the lantern away. */
    private void leave() {
        if (mob.getVehicle() instanceof BenchSeat) mob.stopRiding();
        mob.showPose(VillageResident.POSE_NONE);
        mob.holdYaw(Float.NaN);
        if (mob.getMainHandItem().is(Items.LANTERN)) mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        arrived = false;
    }

    private void station(Post post, boolean work) {
        if (arrived) {
            if (horizontalDistance(post.pos()) > ARRIVE + 1.5) {
                // pushed away: walk back
                arrived = false;
                mob.showPose(VillageResident.POSE_NONE);
                mob.holdYaw(Float.NaN);
            }
            return;
        }
        if (!walk(post.pos(), ARRIVE)) return;
        arrived = true;
        mob.getNavigation().stop();
        mob.holdYaw(post.facing().toYRot());
        if (work) mob.showPose(VillageResident.POSE_WORK);
    }

    private void sit(ServerLevel level, ResidentPlan plan) {
        if (seat == null) {
            station(plan.home(), false);
            return;
        }
        if (arrived) return;
        if (!walk(seat.pos(), ARRIVE)) return;
        if (BenchSeat.taken(level, seat.pos())) {
            // somebody was quicker: the next free seat, else home
            seat = freeSeat(level, plan);
            stuck = 0;
            return;
        }
        mob.getNavigation().stop();
        BenchSeat s = BenchSeat.spawn(level, seat);
        if (!mob.startRiding(s, true)) {
            s.discard();
            seat = null;
            return;
        }
        arrived = true;
        mob.holdYaw(seat.facing().toYRot());
    }

    private void sleep(ResidentPlan plan) {
        if (plan.bed().isEmpty()) {
            station(plan.home(), false);
            return;
        }
        Bed bed = plan.bed().get();
        if (arrived) {
            // keep lying: a push or a reload must not leave it asleep beside the bed
            if (mob.position().distanceToSqr(lyingSpot(bed)) > 0.25) lieDown(bed);
            return;
        }
        if (!walk(bed.foot(), ARRIVE_BED)) return;
        arrived = true;
        lieDown(bed);
    }

    private void lieDown(Bed bed) {
        mob.getNavigation().stop();
        Vec3 at = lyingSpot(bed);
        mob.moveTo(at.x, at.y, at.z, mob.getYRot(), 0);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.showPose(VillageResident.POSE_SLEEP);
        // as in a hammock: facing the foot, the head over the head half
        mob.holdYaw(bed.facing().getOpposite().toYRot());
    }

    /** Where a sleeper lies on {@code bed}: on the mattress where its two halves meet. */
    public static Vec3 lyingSpot(Bed bed) {
        Direction f = bed.facing();
        return new Vec3(bed.foot().getX() + 0.5 + f.getStepX() * 0.5, bed.foot().getY() + 9.0 / 16.0,
                bed.foot().getZ() + 0.5 + f.getStepZ() * 0.5);
    }

    private void lampRound(ResidentPlan plan) {
        List<BlockPos> lamps = mob.lamps();
        if (lamps == null || lampIndex >= lamps.size()) {
            if (mob.getMainHandItem().is(Items.LANTERN) && lamps != null && lampIndex >= lamps.size()) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // the round is done
            }
            station(plan.home(), false);
            return;
        }
        BlockPos lamp = lamps.get(lampIndex);
        if (lampPause > 0) {
            if (--lampPause == 0) {
                lampIndex++;
                arrived = false;
                stuck = 0;
                repath = 0;
                mob.showPose(VillageResident.POSE_NONE);
                mob.holdYaw(Float.NaN);
            }
            return;
        }
        if (!walk(lamp, ARRIVE_LAMP)) return;
        mob.getNavigation().stop();
        lampPause = LAMP_PAUSE;
        mob.showPose(VillageResident.POSE_WORK);
        double dx = lamp.getX() + 0.5 - mob.getX(), dz = lamp.getZ() + 0.5 - mob.getZ();
        mob.holdYaw((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
    }

    // --- walking ------------------------------------------------------------------------------------------------

    /**
     * Walks towards {@code target}; true once within {@code reach} blocks horizontally (and a storey). After
     * {@code mobs.villagers.stuck_ticks} without arriving it steps onto the target (ladders, crowds).
     */
    private boolean walk(BlockPos target, double reach) {
        if (horizontalDistance(target) <= reach && Math.abs(mob.getY() - target.getY()) < 2.5) return true;
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
            repath = REPATH;
        }
        int limit = VillagerConfig.STUCK_TICKS.get();
        if (limit > 0 && ++stuck > limit) {
            mob.getNavigation().stop();
            mob.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, mob.getYRot(), mob.getXRot());
            stuck = 0;
            return true;
        }
        return false;
    }

    private double horizontalDistance(BlockPos p) {
        double dx = p.getX() + 0.5 - mob.getX(), dz = p.getZ() + 0.5 - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static @Nullable Post freeSeat(ServerLevel level, ResidentPlan plan) {
        for (Post s : plan.seats()) {
            if (!BenchSeat.taken(level, s.pos())) return s;
        }
        return null;
    }

    /** The step the goal is in (tests). */
    @Nullable Step current() {
        return step;
    }
}
