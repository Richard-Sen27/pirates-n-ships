package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.hazards.HazardsConfig;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.kraken.KrakenTentacles.Job;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The kraken (work package K1a, docs/design.md §12): a rare boss of the deep ocean. A water animal on its own GeckoLib
 * rig with a 3.5 × 3.5 body and ten {@link KrakenPart} hit boxes (eight tentacles, two eyes) that follow it every tick.
 * Behaviour is the pure {@link KrakenBrain} (lurk, surface, attack, retreat) and {@link KrakenTentacles} (jobs, health
 * pools, cut and regrow); this class turns them into movement, grips on Sable ships (forces through
 * {@link KrakenShipForces}), broken masts, deck swipes and held swimmers. Health 300, armour 8, a boss bar. It leaves
 * the world after a retreat, when no player has been within {@link #DESPAWN_RANGE} blocks for {@link #DESPAWN_TICKS},
 * and when {@code hazards.kraken.enabled} is off.
 */
public class Kraken extends WaterAnimal implements GeoEntity {

    public static final double MAX_HEALTH = 300.0;
    public static final double ARMOR = 8.0;
    public static final double DESPAWN_RANGE = 128.0;
    public static final int DESPAWN_TICKS = 600;

    /** Blocks under the surface at which the body's bottom sits while attacking (its eyes just break the surface). */
    static final double ATTACK_DEPTH = 3.0;
    /** Blocks under the surface the body's bottom keeps while lurking (at most; never below the sea floor). */
    static final double LURK_DEPTH = 14.0;
    /** Blocks between the ship's side and the body's centre at the attack spot (beyond its half width). */
    static final double STANDOFF = 1.75 + 1.0;
    /**
     * Rest length of a tentacle in blocks, root (its {@link #anchor}) to tip: three 20 px segments of the model
     * ({@code KrakenModel} stretches the drawn arm so its tip ends at the part's centre; at rest the stretch is 1).
     */
    public static final double TENTACLE_LENGTH = 60.0 / 16.0;
    /** A free tentacle's lean while the kraken is up (surfacing, attacking): degrees out from straight up. */
    static final double REST_LEAN_UP = 50.0;
    /** A free tentacle's lean while it hangs (lurking, retreating, cut): degrees out from straight down. */
    static final double REST_LEAN_DOWN = 25.0;
    /** A tentacle's tip is "at" its target within this many blocks. */
    static final double CONTACT = 1.6;
    static final double TENTACLE_SPEED = 0.7;
    static final double DRAG_SPEED = 0.12;
    /** Blocks around a sweeping tentacle within which a swipe knocks people off the deck. */
    static final double SWIPE_RADIUS = 3.0;
    /** Ticks between a mast tentacle's arrival and its first blow. */
    static final int MAST_WINDUP = 20;
    /** Ticks a released swimmer is left alone. */
    static final int SWIMMER_COOLDOWN = 100;
    /** Ticks between target searches and between refreshes of the grab spots, masts and deck. */
    static final int SCAN_INTERVAL = 10;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Kraken.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRABS = SynchedEntityData.defineId(Kraken.class, EntityDataSerializers.INT);

    public static final String CONTROLLER_BODY = "body";
    public static final String CONTROLLER_GRAB = "grab";
    public static final String ANIM_IDLE = "idle";
    public static final String ANIM_SURFACE = "surface";
    public static final String ANIM_GRAB = "grab";
    public static final String ANIM_SUBMERGE = "submerge";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM_IDLE);
    private static final RawAnimation SURFACE = RawAnimation.begin().thenPlay(ANIM_SURFACE).thenLoop(ANIM_IDLE);
    private static final RawAnimation SUBMERGE = RawAnimation.begin().thenPlayAndHold(ANIM_SUBMERGE);
    private static final RawAnimation GRAB = RawAnimation.begin().thenPlay(ANIM_GRAB);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    private final KrakenBrain brain = new KrakenBrain();
    private final KrakenTentacles tentacles = new KrakenTentacles(KrakenConfig.TENTACLE_HEALTH.get());

    // --- parts: server-side owners, client-side registered views --------------------------------------------------
    private final KrakenPart[] parts = new KrakenPart[KrakenPart.PARTS];
    private final KrakenPart[] clientParts = new KrakenPart[KrakenPart.PARTS];

    // --- targets (server) -------------------------------------------------------------------------------------------
    private @Nullable UUID targetShip;
    private @Nullable UUID targetSwimmer;
    private @Nullable Vec3 lastTargetPos;
    private @Nullable Vec3 attackSpot;
    private List<BlockPos> spots = List.of();
    private List<BlockPos> masts = List.of();
    private List<LivingEntity> deck = List.of();
    private List<LivingEntity> swimmers = List.of();
    private final Map<UUID, Long> swimmerCooldown = new HashMap<>();

    // --- per-tentacle runtime (server) ------------------------------------------------------------------------------
    private final Vec3[] goal = new Vec3[KrakenTentacles.COUNT];
    private final BlockPos[] spot = new BlockPos[KrakenTentacles.COUNT];
    private final UUID[] victim = new UUID[KrakenTentacles.COUNT];
    private final long[] contactAt = new long[KrakenTentacles.COUNT];
    private final int[] holdTicks = new int[KrakenTentacles.COUNT];
    private final double[] holdDepth = new double[KrakenTentacles.COUNT];
    private long lastMastStrike = Long.MIN_VALUE / 2;
    private long lastSwipe = Long.MIN_VALUE / 2;
    private int farTicks;
    private int grabs;
    private int mastsBroken;
    private int swipes;

    public Kraken(EntityType<? extends Kraken> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setNoGravity(true);
        java.util.Arrays.fill(contactAt, -1L);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, KrakenBrain.State.LURK.ordinal());
        builder.define(DATA_GRABS, 0);
    }

    @Override
    protected void registerGoals() {
        // driven by KrakenBrain in customServerAiStep
    }

    // =============================================================================================== accessors

    /** The brain's state (synced: valid on the client too). */
    public KrakenBrain.State state() {
        int i = entityData.get(DATA_STATE);
        KrakenBrain.State[] all = KrakenBrain.State.values();
        return all[Mth.clamp(i, 0, all.length - 1)];
    }

    public KrakenBrain brain() {
        return brain;
    }

    public KrakenTentacles tentacles() {
        return tentacles;
    }

    /** The server-side part {@code index}, or null before the first tick. */
    public @Nullable KrakenPart part(int index) {
        return parts[index];
    }

    /** Whether tentacle {@code i} is at its target (gripping, beating, sweeping or holding). */
    public boolean inContact(int i) {
        return contactAt[i] >= 0;
    }

    /** Whether a tentacle holds {@code e}. */
    public boolean holding(Entity e) {
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            if (tentacles.job(i) == Job.HOLD_SWIMMER && inContact(i) && e.getUUID().equals(victim[i])) return true;
        }
        return false;
    }

    /** Tentacles gripping the hull right now. */
    public int gripping() {
        int n = 0;
        for (int i = 0; i < KrakenTentacles.COUNT; i++) if (tentacles.job(i) == Job.GRAB_HULL && inContact(i)) n++;
        return n;
    }

    /** Mast blocks broken so far. */
    public int mastBlocksBroken() {
        return mastsBroken;
    }

    /** Deck swipes so far. */
    public int swipes() {
        return swipes;
    }

    public @Nullable UUID targetShip() {
        return targetShip;
    }

    // =============================================================================================== damage

    /**
     * A hit on one of the parts: an eye passes {@code eye_damage_multiplier} times the damage to the body; a tentacle
     * takes the damage into its own pool (cut when empty: it lets go, retreats and grows back after
     * {@code tentacle_regrow_ticks}) and passes {@code tentacle_body_share} of it to the body.
     */
    public boolean hurtPart(KrakenPart part, DamageSource source, float amount) {
        if (level().isClientSide || isDeadOrDying()) return false;
        KrakenRules.HitZone zone = part.zone();
        float body = (float) KrakenRules.bodyDamage(zone, amount, KrakenConfig.EYE_DAMAGE_MULTIPLIER.get(),
                KrakenConfig.TENTACLE_BODY_SHARE.get());
        if (zone == KrakenRules.HitZone.TENTACLE) {
            int i = part.index();
            if (tentacles.isCut(i)) return false;
            if (part.invulnerableTime > 0) return false;
            part.invulnerableTime = 10;
            boolean cut = tentacles.damage(i, amount, level().getGameTime(), KrakenConfig.TENTACLE_REGROW_TICKS.get());
            if (cut) onCut(i);
            if (body > 0) hurtBody(source, body);
            return true;
        }
        return hurtBody(source, body);
    }

    /** A part hit is a hit on the body, with the body's armour and invulnerability frames. */
    private boolean hurtBody(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    private void onCut(int i) {
        release(i);
        KrakenPart p = parts[i];
        if (p != null) {
            p.setCut(true);
            level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 1.5f, 0.5f);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.centre().y, p.getZ(), 20, 0.3, 0.8, 0.3, 0.05);
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
                || super.isInvulnerableTo(source);
    }

    // =============================================================================================== ticking

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            updateParts();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        double regrowHealth = KrakenConfig.TENTACLE_HEALTH.get();
        if (tentacles.tick(now, regrowHealth) > 0) {
            for (int i = 0; i < KrakenTentacles.COUNT; i++) if (!tentacles.isCut(i) && parts[i] != null) parts[i].setCut(false);
        }

        if (tickCount % SCAN_INTERVAL == 0 || (targetShip == null && targetSwimmer == null && tickCount % 5 == 0)) {
            acquireTarget(level);
        }
        ShipBody ship = targetShip != null ? SableShips.byId(level, targetShip) : null;
        if (targetShip != null && (ship == null || ship.isRemoved())) {
            targetShip = null;
            ship = null;
        }
        LivingEntity swimmer = targetSwimmer != null && level.getEntity(targetSwimmer) instanceof LivingEntity l && l.isAlive() ? l : null;
        if (swimmer == null) targetSwimmer = null;
        Vec3 targetPos = ship != null ? ship.worldBounds().getCenter() : swimmer != null ? swimmer.position() : null;
        if (targetPos != null) lastTargetPos = targetPos;

        KrakenBrain.State before = brain.state();
        boolean near = targetPos != null && horizontal(position(), targetPos) <= KrakenBrain.SURFACE_RANGE
                + (ship != null ? halfWidth(ship.worldBounds()) : 0);
        if (before == KrakenBrain.State.SURFACE || attackSpot == null || needsNewSpot(ship)) {
            attackSpot = targetPos == null ? null : attackSpotFor(level, ship, targetPos);
        }
        boolean atSpot = attackSpot != null && position().distanceTo(attackSpot) <= 1.5;
        KrakenBrain.State after = brain.tick(new KrakenBrain.Input(getHealth() / getMaxHealth(), targetPos != null, near, atSpot),
                KrakenConfig.brain());
        if (after != before) onStateChange(before, after);
        entityData.set(DATA_STATE, after.ordinal());

        steer(level, after, targetPos);

        List<KrakenShipForces.PointForce> forces = new ArrayList<>();
        if (after == KrakenBrain.State.ATTACK) {
            if (tickCount % SCAN_INTERVAL == 0 || spots.isEmpty() && masts.isEmpty()) scanShip(level, ship);
            if (tickCount % SCAN_INTERVAL == 0) scanSwimmers(level);
            assignJobs(level, ship);
            workTentacles(level, ship, forces, now);
        } else if (tentacles.free().size() + countCut() < KrakenTentacles.COUNT) {
            releaseAll();
        }
        KrakenShipForces.set(level, getUUID(), forces);
        if (!forces.isEmpty() && ship != null) {
            ship.addVelocity(new Vector3d(), new Vector3d()); // wakes a sleeping body (docs/sable-notes.md §9.0i)
        }

        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (brain.finished()) {
            discard();
        }
    }

    private int countCut() {
        int n = 0;
        for (int i = 0; i < KrakenTentacles.COUNT; i++) if (tentacles.isCut(i)) n++;
        return n;
    }

    private void onStateChange(KrakenBrain.State before, KrakenBrain.State after) {
        if (after != KrakenBrain.State.ATTACK) releaseAll();
        if (after == KrakenBrain.State.SURFACE) {
            playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 4.0f, 0.5f);
        } else if (after == KrakenBrain.State.RETREAT) {
            playSound(SoundEvents.ELDER_GUARDIAN_DEATH, 4.0f, 0.5f);
        }
    }

    // --- targets ----------------------------------------------------------------------------------------------------

    private void acquireTarget(ServerLevel level) {
        if (KrakenConfig.PEACEFUL.get()) {
            targetShip = null;
            targetSwimmer = null;
            return;
        }
        double range = KrakenConfig.DETECTION_RANGE.get();
        ShipBody ship = KrakenShips.nearest(level, position(), range);
        targetShip = ship != null ? ship.id() : null;
        targetSwimmer = null;
        if (ship == null) {
            LivingEntity best = null;
            double bestD = range;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), this::grabbable)) {
                double d = distanceTo(e);
                if (d <= bestD) {
                    bestD = d;
                    best = e;
                }
            }
            targetSwimmer = best != null ? best.getUUID() : null;
        }
    }

    /** Whether a tentacle may grab {@code e} out of the water ({@link KrakenRules#grabs}). */
    boolean grabbable(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        boolean inWater = e.isInWater() || e.isUnderWater();
        boolean onShip = inWater && ShipEntities.standingOrRiding(e) != null;
        return KrakenRules.grabs(new KrakenRules.Swimmer(e instanceof Kraken, e instanceof WaterAnimal, KrakenShips.exempt(e),
                inWater, onShip, e.isPassenger()));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double halfWidth(AABB box) {
        return Math.max(box.getXsize(), box.getZsize()) / 2.0;
    }

    /** In ATTACK the spot is kept unless the ship drifted out of comfortable reach or over the body. */
    private boolean needsNewSpot(@Nullable ShipBody ship) {
        if (ship == null || brain.state() != KrakenBrain.State.ATTACK) return false;
        double d = KrakenShips.horizontalDistance(ship.worldBounds(), position());
        return d > KrakenConfig.TENTACLE_REACH.get() - 3.0 || d < 0.5;
    }

    /** Next to the ship (on the kraken's side), or next to a swimmer, with the body just under the surface. */
    private Vec3 attackSpotFor(ServerLevel level, @Nullable ShipBody ship, Vec3 target) {
        Vec3 away = new Vec3(getX() - target.x, 0, getZ() - target.z);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        away = away.normalize();
        double standoff = ship != null ? halfWidth(ship.worldBounds()) + STANDOFF : 3.0;
        double x = target.x + away.x * standoff;
        double z = target.z + away.z * standoff;
        double surface = surfaceAt(level, x, z, getY());
        double floor = floorAt(level, x, z, getY());
        double y = Double.isNaN(surface) ? getY() : surface - ATTACK_DEPTH;
        if (!Double.isNaN(floor)) y = Math.max(y, floor + 0.05);
        return new Vec3(x, y, z);
    }

    // --- water column -----------------------------------------------------------------------------------------------

    /** The water surface height above (x, fromY, z), or NaN when (x, fromY, z) is not in water. */
    double surfaceAt(Level level, double x, double z, double fromY) {
        BlockPos p = BlockPos.containing(x, fromY, z);
        if (!level.getFluidState(p).is(FluidTags.WATER)) {
            // the body's bottom may be just above the water at the spot; look a few blocks down
            for (int i = 0; i < 4 && !level.getFluidState(p).is(FluidTags.WATER); i++) p = p.below();
            if (!level.getFluidState(p).is(FluidTags.WATER)) return Double.NaN;
        }
        for (int i = 0; i < 128 && level.getFluidState(p.above()).is(FluidTags.WATER); i++) p = p.above();
        return p.getY() + level.getFluidState(p).getHeight(level, p);
    }

    /** The top of the first non-water block below (x, fromY, z), or NaN when none within 128 blocks. */
    double floorAt(Level level, double x, double z, double fromY) {
        BlockPos p = BlockPos.containing(x, fromY, z);
        for (int i = 0; i < 128; i++) {
            if (!level.getFluidState(p).is(FluidTags.WATER)) return p.getY() + 1.0;
            p = p.below();
        }
        return Double.NaN;
    }

    // --- movement ---------------------------------------------------------------------------------------------------

    private void steer(ServerLevel level, KrakenBrain.State state, @Nullable Vec3 targetPos) {
        Vec3 goalPos = null;
        double speed = 0;
        switch (state) {
            case LURK -> {
                double surface = surfaceAt(level, getX(), getZ(), getY());
                double floor = floorAt(level, getX(), getZ(), getY());
                double y = Double.isNaN(surface) ? getY() : surface - LURK_DEPTH;
                if (!Double.isNaN(floor)) y = Math.max(y, floor + 0.2);
                if (targetPos != null && !KrakenConfig.PEACEFUL.get()) {
                    goalPos = new Vec3(targetPos.x, y, targetPos.z);
                    speed = 0.08;
                } else {
                    goalPos = new Vec3(getX(), y, getZ());
                    speed = 0.04;
                }
            }
            case SURFACE -> {
                goalPos = attackSpot;
                speed = 0.2;
            }
            case ATTACK -> {
                goalPos = attackSpot;
                speed = 0.12;
            }
            case RETREAT -> {
                Vec3 from = lastTargetPos != null ? lastTargetPos : position().add(1, 0, 0);
                Vec3 away = new Vec3(getX() - from.x, 0, getZ() - from.z);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                double floor = floorAt(level, getX(), getZ(), getY());
                double y = Double.isNaN(floor) ? getY() - 4 : floor + 0.2;
                goalPos = new Vec3(getX() + away.x * 8, y, getZ() + away.z * 8);
                speed = 0.12;
            }
        }
        if (goalPos == null) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        Vec3 to = goalPos.subtract(position());
        double len = to.length();
        Vec3 desired = len < 0.05 ? Vec3.ZERO : to.scale(Math.min(speed, len) / len);
        setDeltaMovement(getDeltaMovement().add(desired.subtract(getDeltaMovement()).scale(0.2)));
        Vec3 face = state == KrakenBrain.State.RETREAT || targetPos == null ? null : targetPos;
        if (face != null) {
            float yaw = (float) (Mth.atan2(face.z - getZ(), face.x - getX()) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 4f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isEffectiveAi()) {
            if (!isInWater()) setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(isInWater() ? 0.9 : 0.98));
        } else {
            super.travel(travelVector);
        }
    }

    // --- tentacle jobs ----------------------------------------------------------------------------------------------

    private void scanShip(ServerLevel level, @Nullable ShipBody ship) {
        if (ship == null) {
            spots = List.of();
            masts = List.of();
            deck = List.of();
            return;
        }
        Vec3 centre = bodyCentre();
        double reach = KrakenConfig.TENTACLE_REACH.get();
        double surface = surfaceAt(level, getX(), getZ(), getY());
        if (Double.isNaN(surface)) surface = level.getSeaLevel();
        spots = KrakenShips.grabSpots(level, ship, centre, surface, reach);
        masts = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? KrakenShips.mastBlocks(level, ship, centre, reach) : List.of();
        deck = KrakenShips.onDeck(level, ship).stream().filter(e -> e.distanceTo(this) <= reach + 2).toList();
    }

    private void scanSwimmers(ServerLevel level) {
        long now = level.getGameTime();
        swimmerCooldown.values().removeIf(until -> until <= now);
        double reach = KrakenConfig.TENTACLE_REACH.get();
        swimmers = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
                e -> grabbable(e) && !swimmerCooldown.containsKey(e.getUUID()) && e.position().distanceTo(bodyCentre()) <= reach);
    }

    private void assignJobs(ServerLevel level, @Nullable ShipBody ship) {
        if (ship == null) {
            // no ship (a swimmer target, or the ship is gone): only swimmers can be served
            spots = List.of();
            masts = List.of();
            deck = List.of();
        }
        // drop jobs whose target is gone
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            Job j = tentacles.job(i);
            boolean valid = switch (j) {
                case NONE -> true;
                case GRAB_HULL, MAST_STRIKE -> ship != null && spot[i] != null && !level.getBlockState(spot[i]).isAir();
                case DECK_SWIPE -> ship != null && victim[i] != null && level.getEntity(victim[i]) instanceof LivingEntity l
                        && l.isAlive() && deck.contains(l);
                case HOLD_SWIMMER -> victim[i] != null && level.getEntity(victim[i]) instanceof LivingEntity l && l.isAlive()
                        && !KrakenShips.exempt(l);
            };
            if (!valid) release(i);
        }
        List<Integer> free = tentacles.free();
        if (free.isEmpty()) return;
        Set<UUID> victims = new HashSet<>();
        Set<BlockPos> usedSpots = new HashSet<>();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            if (victim[i] != null && tentacles.job(i) != Job.NONE) victims.add(victim[i]);
            if (spot[i] != null && tentacles.job(i) != Job.NONE) usedSpots.add(spot[i]);
        }
        List<LivingEntity> waiting = swimmers.stream().filter(e -> !victims.contains(e.getUUID()) && e.isAlive()).toList();
        List<BlockPos> freeSpots = spots.stream().filter(p -> !usedSpots.contains(p)).toList();
        List<BlockPos> freeMasts = masts.stream().filter(p -> !usedSpots.contains(p)).toList();
        int holds = KrakenTentacles.MAX_HOLDS - tentacles.count(Job.HOLD_SWIMMER);
        KrakenTentacles.Demand demand = new KrakenTentacles.Demand(Math.min(waiting.size(), Math.max(0, holds)),
                !freeMasts.isEmpty() && tentacles.count(Job.MAST_STRIKE) == 0,
                !deck.isEmpty() && tentacles.count(Job.DECK_SWIPE) == 0,
                Math.min(freeSpots.size(), Math.max(0, KrakenConfig.MAX_GRIPS.get() - tentacles.count(Job.GRAB_HULL))));
        List<Job> plan = KrakenTentacles.plan(free.size(), demand);
        List<Integer> pool = new ArrayList<>(free);
        List<LivingEntity> waitingLeft = new ArrayList<>(waiting);
        List<BlockPos> spotsLeft = new ArrayList<>(freeSpots);
        for (Job job : plan) {
            if (job == Job.NONE || pool.isEmpty()) continue;
            Vec3 at;
            Object pick;
            switch (job) {
                case HOLD_SWIMMER -> {
                    LivingEntity e = waitingLeft.remove(0);
                    pick = e;
                    at = e.position();
                }
                case MAST_STRIKE -> {
                    BlockPos p = freeMasts.get(0);
                    pick = p;
                    at = ship.toWorld(Vec3.atCenterOf(p));
                }
                case DECK_SWIPE -> {
                    LivingEntity e = deck.get(0);
                    pick = e;
                    at = e.position();
                }
                default -> {
                    BlockPos p = nearestSpot(ship, spotsLeft, pool);
                    spotsLeft.remove(p);
                    pick = p;
                    at = ship.toWorld(Vec3.atCenterOf(p));
                }
            }
            int best = pool.get(0);
            double bestD = Double.MAX_VALUE;
            for (int i : pool) {
                double d = anchor(i).distanceTo(at);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            pool.remove(Integer.valueOf(best));
            tentacles.setJob(best, job);
            contactAt[best] = -1;
            holdTicks[best] = 0;
            if (pick instanceof BlockPos p) {
                spot[best] = p;
                victim[best] = null;
            } else {
                spot[best] = null;
                victim[best] = ((Entity) pick).getUUID();
            }
        }
    }

    private BlockPos nearestSpot(ShipBody ship, List<BlockPos> candidates, List<Integer> pool) {
        BlockPos best = candidates.get(0);
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : candidates) {
            Vec3 w = ship.toWorld(Vec3.atCenterOf(p));
            for (int i : pool) {
                double d = anchor(i).distanceTo(w);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private void workTentacles(ServerLevel level, @Nullable ShipBody ship, List<KrakenShipForces.PointForce> forces, long now) {
        double gripN = ship == null ? 0 : KrakenRules.gripNewtons(KrakenConfig.GRIP_FORCE.get(), ship.mass(),
                HazardsConfig.MAX_SHIP_MASS_EFFECT.get());
        Quaterniond toLocal = ship == null ? null : ship.orientation().conjugate();
        Vec3 centre = bodyCentre();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            Job job = tentacles.job(i);
            KrakenPart part = parts[i];
            if (job == Job.NONE || part == null) {
                goal[i] = null;
                continue;
            }
            switch (job) {
                case GRAB_HULL, MAST_STRIKE -> {
                    Vec3 w = ship.toWorld(Vec3.atCenterOf(spot[i]));
                    goal[i] = w;
                    if (!touch(i, part, w, now)) break;
                    if (job == Job.GRAB_HULL) {
                        double[] f = KrakenRules.gripVector(gripN, KrakenConfig.GRIP_SIDE_FRACTION.get(),
                                centre.x - w.x, centre.z - w.z);
                        Vector3d local = toLocal.transform(new Vector3d(f[0], f[1], f[2]));
                        forces.add(new KrakenShipForces.PointForce(ship.id(),
                                new Vector3d(spot[i].getX() + 0.5, spot[i].getY() + 0.5, spot[i].getZ() + 0.5), local));
                    } else {
                        long first = Math.max(contactAt[i] + Math.min(MAST_WINDUP, KrakenConfig.MAST_STRIKE_INTERVAL_TICKS.get()),
                                lastMastStrike + KrakenConfig.MAST_STRIKE_INTERVAL_TICKS.get());
                        if (now >= first) strikeMast(level, i, part);
                    }
                }
                case DECK_SWIPE -> {
                    Entity e = level.getEntity(victim[i]);
                    if (e == null) break;
                    goal[i] = e.position().add(0, e.getBbHeight() / 2, 0);
                    if (touch(i, part, goal[i], now) && now >= lastSwipe + KrakenConfig.SWIPE_INTERVAL_TICKS.get()) {
                        swipe(level, ship, part);
                        release(i);
                    }
                }
                case HOLD_SWIMMER -> hold(level, i, part, now);
                default -> { }
            }
        }
    }

    /** Whether the tentacle tip has reached {@code target}; records the first contact. */
    private boolean touch(int i, KrakenPart part, Vec3 target, long now) {
        if (contactAt[i] >= 0) return true;
        if (part.centre().distanceTo(target) <= CONTACT) {
            contactAt[i] = now;
            if (tentacles.job(i) == Job.GRAB_HULL || tentacles.job(i) == Job.HOLD_SWIMMER) {
                entityData.set(DATA_GRABS, ++grabs);
                level().playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0f, 0.5f);
            }
            return true;
        }
        return false;
    }

    private void strikeMast(ServerLevel level, int i, KrakenPart part) {
        BlockPos p = spot[i];
        lastMastStrike = level.getGameTime();
        if (p != null && !level.getBlockState(p).isAir() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            // the cannon's path (combat/cannon/CannonballEntity): destroyBlock on the plot position, so yards and sails
            // react as to a cannonball; Sable moves the drops to the block's world position
            level.destroyBlock(p, true, this);
            mastsBroken++;
            level.playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.6f);
        }
        release(i);
    }

    private void swipe(ServerLevel level, @Nullable ShipBody ship, KrakenPart part) {
        lastSwipe = level.getGameTime();
        swipes++;
        Vec3 tip = part.centre();
        Vec3 shipCentre = ship != null ? ship.worldBounds().getCenter() : bodyCentre();
        double knock = KrakenConfig.SWIPE_KNOCKBACK.get();
        float damage = KrakenConfig.SWIPE_DAMAGE.get().floatValue();
        level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 3, 1.0, 0.3, 1.0, 0);
        for (LivingEntity e : deck) {
            if (!e.isAlive() || e.position().distanceTo(tip) > SWIPE_RADIUS + e.getBbWidth()) continue;
            Vec3 off = new Vec3(e.getX() - shipCentre.x, 0, e.getZ() - shipCentre.z);
            if (off.lengthSqr() < 1e-4) off = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            off = off.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : off.normalize();
            if (damage > 0) e.hurt(damageSources().mobAttack(this), damage);
            e.setDeltaMovement(off.x * knock, 0.4, off.z * knock);
            e.setOnGround(false);
            e.hasImpulse = true;
            e.hurtMarked = true;
        }
    }

    private void hold(ServerLevel level, int i, KrakenPart part, long now) {
        Entity v = level.getEntity(victim[i]);
        if (!(v instanceof LivingEntity e)) {
            release(i);
            return;
        }
        Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
        if (contactAt[i] < 0) {
            goal[i] = c;
            if (touch(i, part, c, now)) holdDepth[i] = c.y;
            return;
        }
        holdTicks[i]++;
        double floor = floorAt(level, part.getX(), part.getZ(), part.centre().y);
        double surface = surfaceAt(level, part.getX(), part.getZ(), part.centre().y);
        double deepest = Double.isNaN(floor) ? holdDepth[i] - 8 : floor + 0.6;
        if (!Double.isNaN(surface)) deepest = Math.max(deepest, surface - 12);
        holdDepth[i] = Math.max(deepest, holdDepth[i] - DRAG_SPEED);
        Vec3 out = anchor(i).subtract(bodyCentre());
        out = new Vec3(out.x, 0, out.z);
        out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
        Vec3 drag = bodyCentre().add(out.scale(2.5));
        goal[i] = new Vec3(drag.x, holdDepth[i], drag.z);
        // the swimmer follows the tip (a player's client owns its movement: hurtMarked sends the velocity)
        Vec3 pull = part.centre().subtract(c);
        double len = pull.length();
        Vec3 v2 = len > 0.6 ? pull.scale(Math.min(0.6, len * 0.5) / len) : pull.scale(0.5);
        e.setDeltaMovement(v2);
        e.hasImpulse = true;
        e.hurtMarked = true;
        e.fallDistance = 0;
        if (holdTicks[i] % 20 == 0) {
            float dmg = KrakenConfig.GRAB_DAMAGE.get().floatValue();
            if (dmg > 0) e.hurt(damageSources().mobAttack(this), dmg);
        }
        if (holdTicks[i] >= KrakenConfig.GRAB_HOLD_TICKS.get()) {
            swimmerCooldown.put(e.getUUID(), now + SWIMMER_COOLDOWN);
            release(i);
        }
    }

    private void release(int i) {
        tentacles.setJob(i, Job.NONE);
        spot[i] = null;
        victim[i] = null;
        contactAt[i] = -1;
        holdTicks[i] = 0;
        goal[i] = null;
    }

    /** Every tentacle lets go. */
    public void releaseAll() {
        for (int i = 0; i < KrakenTentacles.COUNT; i++) release(i);
        tentacles.releaseAll();
    }

    // --- parts ------------------------------------------------------------------------------------------------------

    /** The centre of the body's hit box. */
    public Vec3 bodyCentre() {
        return position().add(0, getBbHeight() / 2.0, 0);
    }

    /** Where tentacle {@code i} leaves the mantle: a ring round the body's top half. */
    public Vec3 anchor(int i) {
        double a = Math.toRadians(getYRot()) + (i + 0.5) * (Math.PI * 2 / KrakenTentacles.COUNT);
        return position().add(-Math.sin(a) * 1.4, getBbHeight() * 0.35, Math.cos(a) * 1.4);
    }

    private Vec3 eye(boolean left) {
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = left ? fz : -fz, rz = left ? -fx : fx;
        return position().add(fx * 1.55 + rx * 0.7, getBbHeight() * 0.72, fz * 1.55 + rz * 0.7);
    }

    /**
     * The resting place of a free tentacle's tip: one {@link #TENTACLE_LENGTH} from its anchor, leaning out (so the
     * model draws it unstretched, clear of the head), swaying by a few degrees; hanging below the ring when cut or
     * lurking.
     */
    private Vec3 rest(int i) {
        Vec3 a = anchor(i);
        Vec3 out = a.subtract(position());
        out = new Vec3(out.x, 0, out.z).normalize();
        double sway = Math.sin(tickCount * 0.05 + i * 0.8) * 6.0;
        KrakenBrain.State s = brain.state();
        boolean hang = tentacles.isCut(i) || s == KrakenBrain.State.LURK || s == KrakenBrain.State.RETREAT;
        double lean = Math.toRadians(hang ? REST_LEAN_DOWN + sway : REST_LEAN_UP + sway);
        double up = hang ? -Math.cos(lean) : Math.cos(lean);
        return a.add(out.scale(Math.sin(lean) * TENTACLE_LENGTH)).add(0, up * TENTACLE_LENGTH, 0);
    }

    private void updateParts() {
        if (!(level() instanceof ServerLevel level)) return;
        for (int i = 0; i < KrakenPart.PARTS; i++) {
            KrakenPart p = parts[i];
            if (p == null || p.isRemoved()) {
                p = KrakenContent.PART.get().create(level);
                if (p == null) continue;
                p.bind(this, i);
                Vec3 at = i < KrakenTentacles.COUNT ? anchor(i) : eye(i == KrakenPart.EYE_LEFT);
                p.moveCentreTo(at);
                p.setCut(i < KrakenTentacles.COUNT && tentacles.isCut(i));
                parts[i] = p;
                level.addFreshEntity(p);
            }
        }
        double reach = KrakenConfig.TENTACLE_REACH.get();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            KrakenPart p = parts[i];
            if (p == null) continue;
            Vec3 target = goal[i] != null && tentacles.job(i) != Job.NONE ? goal[i] : rest(i);
            Vec3 a = anchor(i);
            Vec3 fromAnchor = target.subtract(a);
            if (fromAnchor.length() > reach) target = a.add(fromAnchor.normalize().scale(reach));
            Vec3 cur = p.centre();
            Vec3 step = target.subtract(cur);
            double len = step.length();
            double speed = tentacles.job(i) == Job.HOLD_SWIMMER && inContact(i) ? Math.max(DRAG_SPEED * 2, 0.3) : TENTACLE_SPEED;
            // keep up with the moving body: the speed limit applies on top of the body's own motion
            Vec3 next = len <= speed ? target : cur.add(step.scale(speed / len)).add(getDeltaMovement());
            p.moveCentreTo(next);
        }
        if (parts[KrakenPart.EYE_LEFT] != null) parts[KrakenPart.EYE_LEFT].moveCentreTo(eye(true));
        if (parts[KrakenPart.EYE_RIGHT] != null) parts[KrakenPart.EYE_RIGHT].moveCentreTo(eye(false));
    }

    /** Client: a part reports itself (the model aims the tentacle bones at it). */
    void clientPart(int index, KrakenPart part) {
        if (index >= 0 && index < clientParts.length) clientParts[index] = part;
    }

    /** Client: the interpolated centre of part {@code index}, or null when it is not known (yet). */
    public @Nullable Vec3 clientPartCentre(int index, float partialTick) {
        KrakenPart p = index >= 0 && index < clientParts.length ? clientParts[index] : null;
        if (p == null || p.isRemoved()) return null;
        return p.getPosition(partialTick).add(0, p.getBbHeight() / 2.0, 0);
    }

    /** Client: whether tentacle {@code index} is cut (its part says so). */
    public boolean clientTentacleCut(int index) {
        KrakenPart p = index >= 0 && index < clientParts.length ? clientParts[index] : null;
        return p != null && p.isCut();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        for (KrakenPart p : parts) if (p != null) p.discard();
        if (!level().isClientSide) {
            bossEvent.removeAllPlayers();
            KrakenShipForces.set((ServerLevel) level(), getUUID(), List.of());
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(KrakenConfig.TENTACLE_REACH.get() + 3.0);
    }

    // =============================================================================================== boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    /** The boss bar (for tests). */
    public ServerBossEvent bossEvent() {
        return bossEvent;
    }

    // =============================================================================================== world rules

    @Override
    public void checkDespawn() {
        if (!HazardsConfig.KRAKEN_ENABLED.get()) {
            discard();
            return;
        }
        boolean near = false;
        for (Player p : level().players()) {
            if (!p.isSpectator() && p.distanceToSqr(this) <= DESPAWN_RANGE * DESPAWN_RANGE) {
                near = true;
                break;
            }
        }
        farTicks = near ? 0 : farTicks + 1;
        if (farTicks > DESPAWN_TICKS) {
            discard();
        }
        noActionTime = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected void dropFromLootTable(DamageSource source, boolean causedByPlayer) {
        if (MobConfig.DROPS.get()) super.dropFromLootTable(source, causedByPlayer);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return state() == KrakenBrain.State.LURK ? null : SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0f;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f + random.nextFloat() * 0.1f;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        return super.hurt(source, amount);
    }

    // =============================================================================================== persistence

    private static final String TAG_STATE = "pirates_n_ships:kraken_state";
    private static final String TAG_STATE_TICKS = "pirates_n_ships:kraken_state_ticks";
    private static final String TAG_ATTACK_TICKS = "pirates_n_ships:kraken_attack_ticks";
    private static final String TAG_TENTACLES = "pirates_n_ships:kraken_tentacles";

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_STATE, brain.state().name());
        tag.putInt(TAG_STATE_TICKS, brain.ticksInState());
        tag.putInt(TAG_ATTACK_TICKS, brain.attackTicks());
        ListTag list = new ListTag();
        for (int i = 0; i < KrakenTentacles.COUNT; i++) {
            CompoundTag t = new CompoundTag();
            t.putDouble("pool", tentacles.pool(i));
            t.putLong("cut_until", tentacles.regrowsAt(i));
            list.add(t);
        }
        tag.put(TAG_TENTACLES, list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_STATE)) {
            brain.load(tag.getString(TAG_STATE), tag.getInt(TAG_STATE_TICKS), tag.getInt(TAG_ATTACK_TICKS));
        }
        ListTag list = tag.getList(TAG_TENTACLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), KrakenTentacles.COUNT); i++) {
            CompoundTag t = list.getCompound(i);
            tentacles.load(i, t.getDouble("pool"), t.getLong("cut_until"));
        }
        bossEvent.setName(getDisplayName());
    }

    // =============================================================================================== animation

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_GRABS.equals(key) && level().isClientSide && entityData.get(DATA_GRABS) != grabs) {
            grabs = entityData.get(DATA_GRABS);
            triggerAnim(CONTROLLER_GRAB, ANIM_GRAB);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_BODY, 10, state -> switch (state()) {
            case SURFACE -> state.setAndContinue(SURFACE);
            case RETREAT -> state.setAndContinue(SUBMERGE);
            default -> state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, CONTROLLER_GRAB, 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_GRAB, GRAB));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
