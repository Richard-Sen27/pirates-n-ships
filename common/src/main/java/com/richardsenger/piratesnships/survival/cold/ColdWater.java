package com.richardsenger.piratesnships.survival.cold;

import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.survival.SurvivalConfig;
import com.richardsenger.piratesnships.survival.SurvivalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The cold water hooks (rules in {@link ColdWaterRules}): after every server level tick, each living entity in the
 * water of a {@code #pirates_n_ships:cold_water} biome gains freezing meter; and finishing a drink in
 * {@code #pirates_n_ships:warming} grants the warm effect.
 */
public final class ColdWater {

    private ColdWater() {
    }

    /** {@code LEVEL_TICK_END}: every entity of the level has had its tick (and vanilla's thaw) this tick. */
    public static void onLevelTick(ServerLevel level) {
        boolean enabled = SurvivalConfig.COLD_WATER_ENABLED.get();
        if (!enabled) return;
        int perTick = SurvivalConfig.FREEZE_TICKS_PER_TICK.get();
        for (Entity entity : level.getAllEntities()) {
            // cheap filters first: most entities are not living or not in water
            if (!(entity instanceof LivingEntity living) || !living.isInWater()) continue;
            BlockPos pos = living.blockPosition();
            // an entity that didn't tick didn't thaw either: leave it alone
            if (!level.isPositionEntityTicking(pos)) continue;
            if (ColdWaterRules.judge(true, subject(level, living, pos)) == ColdWaterRules.Outcome.FREEZES) {
                living.setTicksFrozen(ColdWaterRules.nextTicksFrozen(living.getTicksFrozen(), living.getTicksRequiredToFreeze(), perTick));
            }
        }
    }

    /** The rule's view of {@code living} at {@code pos} (exposed for GameTests). */
    public static ColdWaterRules.Subject subject(ServerLevel level, LivingEntity living, BlockPos pos) {
        boolean creative = living instanceof Player p && (p.isCreative() || p.isSpectator());
        return new ColdWaterRules.Subject(
                living.isAlive() && !living.isDeadOrDying(),
                living.isInWater(),
                isColdWater(level, pos),
                creative,
                living.canFreeze(),
                isAquatic(living),
                living.isPassenger(),
                living.hasEffect(SurvivalContent.WARM.holder()),
                () -> ShipEntities.standingOrRiding(living) != null);
    }

    /** Whether the biome at {@code pos} is in {@code #pirates_n_ships:cold_water}. */
    public static boolean isColdWater(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(SurvivalContent.COLD_WATER);
    }

    /** Water creatures (fish, squid, dolphins, our sharks) and mobs that breathe under water (drowned) don't freeze. */
    static boolean isAquatic(LivingEntity living) {
        return living instanceof WaterAnimal || living.getType().is(EntityTypeTags.AQUATIC) || living.canBreatheUnderwater();
    }

    /** {@code ITEM_USE_FINISH}: a finished warming drink grants the warm effect (server side, while cold water is on). */
    public static void onItemUseFinish(LivingEntity entity, ItemStack used, ItemStack result) {
        if (entity.level().isClientSide() || !used.is(SurvivalContent.WARMING)) return;
        if (!SurvivalConfig.COLD_WATER_ENABLED.get()) return;
        int ticks = SurvivalConfig.WARM_EFFECT_TICKS.get();
        if (ticks <= 0) return;
        entity.addEffect(new MobEffectInstance(SurvivalContent.WARM.holder(), ticks, 0, false, true, true));
    }
}
