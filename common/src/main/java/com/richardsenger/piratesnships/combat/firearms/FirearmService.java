package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of loading and firing (docs/design.md §8.1). Called by {@link FirearmItem}; works for any
 * {@link LivingEntity} shooter so NPC gunners can use it later. All decisions are in {@link FirearmRules}.
 */
public final class FirearmService {

    /** What a pull of the trigger did. */
    public enum Shot { FIRED, MISFIRE }

    private FirearmService() {
    }

    // ---- loading ----------------------------------------------------------------------------------------------

    /** True when the player carries what loading takes (or is in creative mode). */
    public static boolean canLoad(Player player) {
        return FirearmRules.canLoad(player.hasInfiniteMaterials(), count(player.getInventory(), CombatContent.LEAD_SHOT.get()),
                count(player.getInventory(), Items.GUNPOWDER), FirearmsConfig.CONSUME_GUNPOWDER.get());
    }

    /**
     * Finishes loading: takes one lead shot (and one gunpowder) unless the shooter has infinite materials, and marks
     * the gun loaded. Returns false (and changes nothing) when the ammunition is gone by now.
     */
    public static boolean completeLoading(Level level, LivingEntity shooter, ItemStack gun) {
        if (FirearmContent.isLoaded(gun)) return false;
        if (shooter instanceof Player player) {
            if (!canLoad(player)) return false;
            if (!player.hasInfiniteMaterials()) {
                consumeOne(player.getInventory(), CombatContent.LEAD_SHOT.get());
                if (FirearmsConfig.CONSUME_GUNPOWDER.get()) consumeOne(player.getInventory(), Items.GUNPOWDER);
            }
        }
        FirearmContent.setLoaded(gun, true);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    static int count(Inventory inventory, Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    static void consumeOne(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                inventory.setChanged();
                return;
            }
        }
    }

    // ---- firing -----------------------------------------------------------------------------------------------

    /** True when the shooter stands in the rain (at the feet or the top of the head, like vanilla's own check). */
    public static boolean inRain(LivingEntity shooter) {
        Level level = shooter.level();
        BlockPos feet = shooter.blockPosition();
        return level.isRainingAt(feet)
                || level.isRainingAt(BlockPos.containing(shooter.getX(), shooter.getBoundingBox().maxY, shooter.getZ()));
    }

    /**
     * Pulls the trigger of a loaded gun. A misfire (rain, {@link FirearmRules#misfires}) clicks and leaves the gun
     * loaded; a shot spawns one {@link LeadBallEntity}, plays the shot, puffs smoke at the muzzle, applies recoil
     * and unloads the gun. Either way the cooldown starts.
     */
    public static Shot fire(ServerLevel level, LivingEntity shooter, ItemStack gun, FirearmKind kind) {
        return fire(level, shooter, gun, kind, 0);
    }

    /**
     * Like {@link #fire(ServerLevel, LivingEntity, ItemStack, FirearmKind)} after aiming for {@code aimedTicks}: once
     * the aim has been held {@code firearms.aim.aim_steady_ticks}, the spread is multiplied by
     * {@code aimed_spread_factor} ({@link FirearmRules#aimedSpread}).
     */
    public static Shot fire(ServerLevel level, LivingEntity shooter, ItemStack gun, FirearmKind kind, int aimedTicks) {
        FirearmType type = FirearmsConfig.type(kind);
        double spread = FirearmRules.aimedSpread(type.spreadDegrees(), aimedTicks, FirearmsConfig.AIM_STEADY_TICKS.get(),
                FirearmsConfig.AIMED_SPREAD_FACTOR.get());
        RandomSource random = shooter.getRandom();
        startCooldown(shooter, gun);
        if (FirearmRules.misfires(inRain(shooter), type.misfireChanceInRain(), random.nextDouble())) {
            playEmpty(level, shooter, type.soundPitch());
            return Shot.MISFIRE;
        }

        LeadBallEntity ball = new LeadBallEntity(level, shooter, type.damage(), FirearmsConfig.BALL_LIFETIME_TICKS.get());
        float[] rot = FirearmRules.spreadRotation(shooter.getXRot(), shooter.getYRot(), spread,
                random.nextDouble(), random.nextDouble());
        ball.shootFromRotation(shooter, rot[0], rot[1], 0.0f, type.muzzleVelocity(), 0.0f);
        level.addFreshEntity(ball);
        FirearmContent.setLoaded(gun, false);

        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), CombatSounds.PISTOL_SHOT.get(),
                SoundSource.PLAYERS, 1.5f, type.soundPitch() * (0.95f + random.nextFloat() * 0.1f));
        smoke(level, shooter);
        recoil(shooter, kind);
        return Shot.FIRED;
    }

    /** The dry click of an unloaded gun or a misfire. */
    public static void playEmpty(Level level, LivingEntity shooter, float pitch) {
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), CombatSounds.PISTOL_EMPTY.get(),
                SoundSource.PLAYERS, 1.0f, pitch);
    }

    static void startCooldown(LivingEntity shooter, ItemStack gun) {
        int ticks = FirearmsConfig.COOLDOWN_TICKS.get();
        if (ticks > 0 && shooter instanceof Player player) {
            player.getCooldowns().addCooldown(gun.getItem(), ticks);
        }
    }

    /** A puff of smoke in front of the muzzle, drifting along the shot. */
    static void smoke(ServerLevel level, LivingEntity shooter) {
        Vec3 look = shooter.getLookAngle();
        Vec3 muzzle = shooter.getEyePosition().add(look.scale(0.9)).add(0, -0.15, 0);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.1, 0.05, 0.1, 0.005);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, look.x * 0.2, look.y * 0.2, look.z * 0.2, 0.03);
    }

    /**
     * Kicks the view up and pushes the shooter back. A networked player owns its view and movement, so it gets a
     * {@link RecoilPayload}; any other shooter is changed right here.
     */
    static void recoil(LivingEntity shooter, FirearmKind kind) {
        RecoilPayload payload = new RecoilPayload(FirearmsConfig.recoilPitch(kind), (float) FirearmsConfig.recoilPush(kind));
        if (shooter instanceof ServerPlayer player) {
            Services.NETWORK.sendToPlayer(player, payload);
        } else if (shooter instanceof Player player) {
            RecoilPayload.apply(payload, player);
        } else {
            RecoilPayload.pushBack(shooter, payload.push());
        }
    }
}
