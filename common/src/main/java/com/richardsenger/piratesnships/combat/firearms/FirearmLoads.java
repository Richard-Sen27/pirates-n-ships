package com.richardsenger.piratesnships.combat.firearms;

import com.richardsenger.piratesnships.combat.content.CombatContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The registered {@link FirearmLoad}s and the default lead ball (docs/design.md §8.1). */
public final class FirearmLoads {

    private static final List<FirearmLoad> LOADS = new CopyOnWriteArrayList<>();

    private FirearmLoads() {
    }

    /** Adds a load that takes precedence over the lead ball when it is offered. Call from {@code registerContent()}. */
    public static void register(FirearmLoad load) {
        if (!LOADS.contains(load)) LOADS.add(load);
    }

    /** What pressing use on the unloaded {@code gun} in {@code gunHand} loads. */
    public static FirearmLoad forLoading(LivingEntity shooter, InteractionHand gunHand, ItemStack gun, FirearmKind kind) {
        for (FirearmLoad load : LOADS) {
            if (load.offered(shooter, gunHand, gun, kind)) return load;
        }
        return LEAD_BALL;
    }

    /** What the loaded {@code gun} holds. */
    public static FirearmLoad loadedIn(ItemStack gun) {
        for (FirearmLoad load : LOADS) {
            if (load.isIn(gun)) return load;
        }
        return LEAD_BALL;
    }

    /** One lead shot and (if {@code firearms.consume_gunpowder}) one gunpowder; fires a {@link LeadBallEntity}. */
    public static final FirearmLoad LEAD_BALL = new FirearmLoad() {
        @Override
        public boolean offered(LivingEntity shooter, InteractionHand gunHand, ItemStack gun, FirearmKind kind) {
            return true;
        }

        @Override
        public boolean canLoad(LivingEntity shooter, InteractionHand gunHand, ItemStack gun) {
            return !(shooter instanceof Player player) || FirearmService.canLoad(player);
        }

        @Override
        public boolean load(Level level, LivingEntity shooter, InteractionHand gunHand, ItemStack gun) {
            if (shooter instanceof Player player) {
                if (!FirearmService.canLoad(player)) return false;
                if (!player.hasInfiniteMaterials()) {
                    FirearmService.consumeOne(player.getInventory(), CombatContent.LEAD_SHOT.get());
                    if (FirearmsConfig.CONSUME_GUNPOWDER.get()) FirearmService.consumeOne(player.getInventory(), Items.GUNPOWDER);
                }
            }
            return true;
        }

        @Override
        public boolean isIn(ItemStack gun) {
            return FirearmContent.isLoaded(gun);
        }

        @Override
        public void launch(ServerLevel level, LivingEntity shooter, ItemStack gun, FirearmKind kind, float xRot, float yRot) {
            FirearmType type = FirearmsConfig.type(kind);
            LeadBallEntity ball = new LeadBallEntity(level, shooter, type.damage(), FirearmsConfig.BALL_LIFETIME_TICKS.get());
            ball.shootFromRotation(shooter, xRot, yRot, 0.0f, type.muzzleVelocity(), 0.0f);
            level.addFreshEntity(ball);
        }
    };
}
