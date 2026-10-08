package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import com.richardsenger.piratesnships.survival.SurvivalConfig;
import com.richardsenger.piratesnships.survival.SurvivalContent;
import com.richardsenger.piratesnships.survival.cold.ColdWater;
import com.richardsenger.piratesnships.survival.cold.ColdWaterRules;
import net.minecraft.util.Mth;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The sea chest floating in water (docs/design.md §11 "Placed in water"). Holds the 54 slots itself and is saved
 * with the world.
 * <ul>
 *   <li>Floats boat-like ({@link FloatRules}): rises when submerged, settles at the waterline. The bob is cosmetic
 *       (renderer, client config {@code sea_chest_visuals.bob_amplitude}).</li>
 *   <li>Pushed by water currents through vanilla fluid pushing, and drifts with the wind ({@link WindService}) while
 *       floating at the surface.</li>
 *   <li>Use opens it (same menu as the block); sneak-use picks it up into the inventory; a player's hit knocks it
 *       loose as a dropped item. Both carry the contents and the custom name; the entity is emptied first, so
 *       nothing can be taken twice. {@code /kill} drops it as an item as well.</li>
 *   <li>Use with a {@link PaddleItem} seats the player on it (SC2, one rider, only while it floats); with the paddle in
 *       hand the rider's movement keys paddle it ({@link PaddleRules}) on top of drift and buoyancy, at a hunger
 *       cost, and cold water reaches the rider. Sneaking dismounts (vanilla). The rider can't open, pick up or hit
 *       the chest they sit on, and nobody can pick it up while it is occupied. The rider's keys arrive through
 *       vanilla's passenger input packet ({@code xxa}/{@code zza}); the chest has no controlling passenger, so the
 *       server moves it and clients only interpolate.</li>
 * </ul>
 * Physics run on the server only; clients interpolate the synced position like a boat's.
 */
public class SeaChestEntity extends Entity implements Container {

    private static final int WIND_SAMPLE_INTERVAL = 5;
    private static final double MAX_OPEN_DISTANCE_SQR = 8.0 * 8.0;

    private NonNullList<ItemStack> items = SeaChestContents.emptySlots();

    private double windAccelX;
    private double windAccelZ;
    private int windAge = WIND_SAMPLE_INTERVAL;
    /** Ticks of continuous paddling, for the stroke sound and swing. */
    private int strokeTicks;
    private static final int STROKE_INTERVAL = 20;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    public SeaChestEntity(EntityType<? extends SeaChestEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    /** A new floating chest at {@code at} (bottom center) holding copies of the stack's contents and its name. */
    public static SeaChestEntity fromItem(Level level, Vec3 at, float yaw, ItemStack stack) {
        SeaChestEntity chest = new SeaChestEntity(SeaChestContent.ENTITY.get(), level);
        chest.moveTo(at.x, at.y, at.z, yaw, 0f);
        chest.items = SeaChestContents.fromItem(stack);
        chest.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        return chest;
    }

    /** The chest as an item with copies of the contents and the custom name. Does not empty the entity. */
    public ItemStack toItem() {
        return SeaChestContents.toItem(SeaChestContent.ITEM.get(), items, getCustomName());
    }

    /** Takes the chest as an item and removes the entity; afterwards it holds nothing. */
    public ItemStack takeAsItem() {
        ItemStack stack = toItem();
        items = SeaChestContents.emptySlots();
        discard();
        return stack;
    }

    // --- synced data, saving ------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        items = SeaChestContents.emptySlots();
        ContainerHelper.loadAllItems(tag, items, registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, items, false, registryAccess());
    }

    // --- interaction --------------------------------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return toItem();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (isRemoved()) {
            return InteractionResult.PASS;
        }
        if (hasPassenger(player)) {
            // The rider sits on the lid: dismount (sneak) first to open it or pick it up
            player.displayClientMessage(Component.translatable(DISMOUNT_FIRST_KEY), true);
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            if (isVehicle()) {
                player.displayClientMessage(Component.translatable(MountRefusal.OCCUPIED.key()), true);
                return InteractionResult.CONSUME;
            }
            ItemStack stack = takeAsItem();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.0f);
            return InteractionResult.CONSUME;
        }
        if (player.getItemInHand(hand).getItem() instanceof PaddleItem && SeaChestConfig.paddlingOn()) {
            MountRefusal refusal = tryMount(player);
            if (refusal != MountRefusal.NONE) {
                player.displayClientMessage(Component.translatable(refusal.key()), true);
            }
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SeaChestMenu(id, inventory, this), getDisplayName()));
        return InteractionResult.CONSUME;
    }

    // --- paddling (SC2) -----------------------------------------------------------------------------------------

    /** Why a paddle doesn't seat a player on this chest. */
    public enum MountRefusal {
        NONE(""),
        /** {@code sea_chest.enabled} or {@code sea_chest.paddle_enabled} is off. */
        DISABLED("disabled"),
        /** Not in water (beached, falling). */
        NOT_FLOATING("not_floating"),
        /** Someone already sits on it. */
        OCCUPIED("occupied"),
        /** The player rides something else, or can't ride (spectator, sleeping). */
        CANNOT_RIDE("cannot_ride");

        private final String id;

        MountRefusal(String id) {
            this.id = id;
        }

        /** The action bar message's translation key. */
        public String key() {
            return KEY_PREFIX + id;
        }
    }

    static final String KEY_PREFIX = "entity." + Constants.MOD_ID + ".sea_chest.paddle.";
    static final String DISMOUNT_FIRST_KEY = KEY_PREFIX + "dismount_first";

    /**
     * Seats {@code player} on the chest if paddling is on, the chest floats and is free. The chest turns to the
     * player's heading, so forward is where they look. Server side.
     */
    public MountRefusal tryMount(Player player) {
        if (!SeaChestConfig.paddlingOn()) return MountRefusal.DISABLED;
        if (!isInWater()) return MountRefusal.NOT_FLOATING;
        if (isVehicle()) return MountRefusal.OCCUPIED;
        if (player.isPassenger() || player.isSpectator() || player.isSleeping()) return MountRefusal.CANNOT_RIDE;
        float yaw = player.getYRot();
        setYRot(yaw);
        yRotO = yaw;
        if (!player.startRiding(this)) return MountRefusal.CANNOT_RIDE;
        return MountRefusal.NONE;
    }

    /** The seated player, or null. */
    public @Nullable Player rider() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    /** The rider turns with the chest and can look at most 105 degrees to either side, like in a boat. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        float turned = Mth.wrapDegrees(getYRot() - yRotO);
        if (turned != 0f) {
            passenger.setYRot(passenger.getYRot() + turned);
            passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        }
        clampRiderRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRiderRotation(passenger);
    }

    private void clampRiderRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float off = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(off, -105.0f, 105.0f);
        passenger.yRotO += clamped - off;
        passenger.setYRot(passenger.getYRot() + clamped - off);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /**
     * One tick of the rider's paddling, server side: heading and thrust from the movement keys while a paddle is in
     * hand, the hunger of the stroke, and a stroke sound and arm swing about once a second. Returns the horizontal
     * acceleration {x, z} to add.
     */
    private double[] paddle(Player rider) {
        PaddleRules.Params p = SeaChestConfig.paddling();
        PaddleRules.Input in = PaddleRules.Input.of(rider.xxa, rider.zza);
        boolean canPaddle = SeaChestConfig.paddlingOn() && PaddleItem.inHand(rider);
        PaddleRules.Stroke s = PaddleRules.stroke(in, canPaddle, getYRot(), p, SeaChestConfig.floating());
        if (!s.paddling()) {
            strokeTicks = 0;
            return new double[]{0.0, 0.0};
        }
        setYRot(Mth.wrapDegrees(getYRot() + s.yawDelta()));
        float exhaustion = PaddleRules.exhaustion(s, p);
        if (exhaustion > 0f) rider.causeFoodExhaustion(exhaustion);
        if (strokeTicks++ % STROKE_INTERVAL == 0) {
            InteractionHand hand = PaddleItem.handOf(rider);
            if (hand != null) rider.swing(hand, true);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.6f, 0.9f + random.nextFloat() * 0.2f);
        }
        return new double[]{s.accelX(), s.accelZ()};
    }

    /**
     * Cold water reaches the rider's legs (docs/design.md §14): survival's cold-water rule exempts riders (boats), so
     * the chest charges its rider's freezing meter itself. Runs before the rider's tick, which thaws 2 like after
     * survival's own hook, so the meter follows the same course as for a swimmer.
     */
    private void chill(ServerLevel level, Player rider) {
        if (!SurvivalConfig.COLD_WATER_ENABLED.get()) return;
        boolean creative = rider.isCreative() || rider.isSpectator();
        ColdWaterRules.Subject subject = PaddleRules.riderInColdWater(rider.isAlive() && !rider.isDeadOrDying(), isInWater(),
                ColdWater.isColdWater(level, blockPosition()), creative, rider.canFreeze(), rider.hasEffect(SurvivalContent.WARM.holder()));
        if (ColdWaterRules.judge(true, subject) == ColdWaterRules.Outcome.FREEZES) {
            rider.setTicksFrozen(ColdWaterRules.nextTicksFrozen(rider.getTicksFrozen(), rider.getTicksRequiredToFreeze(),
                    SurvivalConfig.FREEZE_TICKS_PER_TICK.get()));
        }
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable(SeaChestBlockEntity.TITLE_KEY);
    }

    /** Only a player's hit knocks it loose (drops it as an item with its contents); everything else is ignored. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !(source.getEntity() instanceof Player player) || hasPassenger(player)) {
            return false;
        }
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        boolean empty = isEmpty();
        ItemStack stack = takeAsItem();
        if (!(player.getAbilities().instabuild && empty)) {
            spawnAtLocation(stack, 0.5f);
        }
        return true;
    }

    /** {@code /kill} and similar: the chest leaves its item (with the contents) behind instead of vanishing. */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && !level().isClientSide && !isEmpty()) {
            ItemStack stack = toItem();
            items = SeaChestContents.emptySlots();
            spawnAtLocation(stack, 0.5f);
        }
        super.remove(reason);
    }

    // --- motion -------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickLerp();
            return;
        }
        FloatRules.Params params = SeaChestConfig.floating();
        double height = getBbHeight();
        double submerged = isInWater() ? Math.min(getFluidHeight(FluidTags.WATER), height) : 0.0;
        Vec3 v = getDeltaMovement();
        double vx = v.x;
        double vy;
        double vz = v.z;
        double keep;
        if (submerged > 0.0) {
            vy = FloatRules.verticalVelocity(v.y, submerged, height, params);
            sampleWind(submerged < height, params);
            vx += windAccelX;
            vz += windAccelZ;
            Player rider = rider();
            if (rider != null) {
                double[] a = paddle(rider);
                vx += a[0];
                vz += a[1];
                if (level() instanceof ServerLevel server) chill(server, rider);
            }
            keep = params.waterRetention();
        } else {
            windAccelX = windAccelZ = 0.0;
            vy = onGround() ? 0.0 : (v.y - FloatRules.GRAVITY) * 0.98;
            keep = onGround() ? 0.5 : 0.98;
        }
        setDeltaMovement(vx * keep, vy, vz * keep);
        move(MoverType.SELF, getDeltaMovement());
    }

    private void sampleWind(boolean atSurface, FloatRules.Params params) {
        if (++windAge < WIND_SAMPLE_INTERVAL) {
            if (!atSurface) {
                windAccelX = windAccelZ = 0.0;
            }
            return;
        }
        windAge = 0;
        if (!atSurface || !(level() instanceof ServerLevel server)) {
            windAccelX = windAccelZ = 0.0;
            return;
        }
        WindSample wind = WindService.sample(server, position());
        double[] a = FloatRules.windAcceleration(wind.dirX() * wind.strength(), wind.dirZ() * wind.strength(),
                SeaChestConfig.DRIFT_FACTOR.get(), SeaChestConfig.MAX_DRIFT_SPEED.get(), true, params);
        windAccelX = a[0];
        windAccelZ = a[1];
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(steps, 3);
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? (float) lerpYRot : getYRot();
    }

    private void tickLerp() {
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
    }

    // --- Container ----------------------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SeaChestContents.SIZE;
    }

    @Override
    public boolean isEmpty() {
        return SeaChestContents.isEmpty(items);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return SeaChestContents.canHold(stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(this) <= MAX_OPEN_DISTANCE_SQR;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void startOpen(Player player) {
        if (!player.isSpectator()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.5f, 0.9f);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!player.isSpectator()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_CLOSE, SoundSource.NEUTRAL, 0.5f, 0.9f);
        }
    }

    /** Test access. */
    public @Nullable ItemStack firstNonEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return s;
        }
        return null;
    }
}
