package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Server: breath below the flood water inside one ship's hull (FLD1b, docs/design.md §4.4). Each hull tick, after the
 * entities ticked ({@code LEVEL_TICK_END}), every living entity in the ship's bounds whose eye is under a compartment's
 * flood level ({@link FloodBreath#floodedCompartmentAt}) and not in the world's water loses air as in the sea and
 * drowns once it is spent. Vanilla's own refill (it sees a dry eye) is undone by carrying the air on from what this
 * set last tick ({@link FloodBreath#step}); when the head comes up, the entity drops out and vanilla refills as usual.
 * One per {@link HullRuntime}; toggle {@code dry_hull.flood_breath}.
 *
 * <p>Plain server logic instead of a mixin on {@code LivingEntity#baseTick}: the hull runtime already knows the
 * compartments and levels, and both loaders keep their own breath code (NeoForge's {@code LivingBreatheEvent} path)
 * untouched. Copied from vanilla: the Respiration roll of the protected {@code decreaseAirSupply}
 * ({@link FloodBreath#consumes}). Left out: the 8 bubble particles at the drowning hit (vanilla adds them on both
 * sides; a server-sent bubble outside the world's water pops at once) and the dismount from vehicles that cannot go
 * under water.
 */
final class FloodBreathing {

    /** Per entity under the flood water: the air set last tick and the ticks it has been under. */
    private static final class Under {
        int lastAir = FloodBreath.NO_LAST;
        int ticks;
        boolean seen;
    }

    private final Map<UUID, Under> under = new HashMap<>();

    /** One tick for every living entity in the ship's bounds. Cheap when no compartment holds water. */
    void tick(ServerLevel level, ShipBody ship, FloodSimulation sim) {
        if (!DryHullConfig.ENABLED.get() || !DryHullConfig.FLOOD_BREATH.get() || !anyWater(sim)) {
            under.clear();
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, ship.worldBounds(), LivingEntity::isAlive)) {
            Vec3 eye = ship.toPlot(new Vec3(e.getX(), e.getEyeY(), e.getZ()));
            boolean eyeUnder = FloodBreath.floodedCompartmentAt(sim, eye.x, eye.y, eye.z) >= 0;
            if (!eyeUnder) {
                continue;
            }
            Under u = under.computeIfAbsent(e.getUUID(), k -> new Under());
            u.seen = true;
            boolean inWorldWater = e.isEyeInFluid(FluidTags.WATER);
            if (FloodBreath.drains(true, inWorldWater, canBreathe(e, u.ticks))) {
                FloodBreath.Step step = FloodBreath.step(e.getAirSupply(), u.lastAir, FloodBreath.consumes(oxygenBonus(e), e.getRandom().nextDouble()));
                e.setAirSupply(step.air());
                u.lastAir = step.air();
                if (step.drown()) {
                    e.hurt(e.damageSources().drown(), FloodBreath.DROWN_DAMAGE);
                }
            } else {
                // vanilla handles the world's water; breathing entities keep vanilla's refill
                u.lastAir = FloodBreath.NO_LAST;
            }
            u.ticks++;
        }
        under.values().removeIf(u -> !u.seen);
        under.values().forEach(u -> u.seen = false);
    }

    private static boolean anyWater(FloodSimulation sim) {
        for (int c = 0; c < sim.analysis().compartments().size(); c++) {
            if (sim.volume(c) >= FloodBreath.MIN_VOLUME) {
                return true;
            }
        }
        return false;
    }

    private static boolean canBreathe(LivingEntity e, int ticksUnder) {
        boolean waterBreathing = MobEffectUtil.hasWaterBreathing(e);
        MobEffectInstance wb = e.getEffect(MobEffects.WATER_BREATHING);
        // the turtle helmet's effect: hidden, not ambient, at most its 10 seconds (Player#turtleHelmetTick)
        boolean helmetOnly = e.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET) && wb != null && !wb.isVisible()
                && !wb.isAmbient() && wb.getDuration() <= FloodBreath.TURTLE_HELMET_TICKS && !e.hasEffect(MobEffects.CONDUIT_POWER);
        boolean invulnerable = e instanceof Player p && (p.getAbilities().invulnerable || p.isCreative() || p.isSpectator());
        @SuppressWarnings("deprecation") // NeoForge prefers canDrownInFluidType, which is loader-only; vanilla's tag check
        boolean breathes = e.canBreatheUnderwater();
        return FloodBreath.canBreathe(breathes, waterBreathing, helmetOnly, ticksUnder, invulnerable);
    }

    /** {@code LivingEntity#decreaseAirSupply}'s attribute read: the Respiration oxygen bonus, 0 without the attribute. */
    private static double oxygenBonus(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.OXYGEN_BONUS);
        return a == null ? 0.0 : a.getValue();
    }

    /** Entities under the flood water right now (tests). */
    int underCount() {
        return under.size();
    }
}
