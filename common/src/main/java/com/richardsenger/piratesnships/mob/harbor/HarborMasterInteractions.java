package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.market.PortKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Talking to a harbor master opens his desk (PRT1a, docs/design.md §10.3), a {@code CommonEvents.ENTITY_INTERACT}
 * listener. The gesture: the main hand, not sneaking, the harbor master alive and not a prisoner (a name tag, a lead
 * or a spawn egg keeps its own use). He picks the bound desk nearest him within {@code harbor_desks.desk_reach} that
 * the player can also reach (his own port's first) and opens it through {@link HarborDeskService#useViaHarborMaster},
 * so the desk session, its reach and every refusal of the desk stay as they are; on opening he greets the player. A
 * harbor master the player hit, or who is running from danger, says "not now".
 */
public final class HarborMasterInteractions {

    public static final String KEY = "message." + Constants.MOD_ID + ".harbor_master.";
    public static final String NOT_NOW = KEY + "not_now";
    public static final String NO_DESK = KEY + "no_desk";
    public static final String GREETING = KEY + "greeting.";

    /** What talking to him did (for tests and the caller's message). */
    public enum Talk { NOT_A_GESTURE, NOT_NOW, NO_DESK, DESK }

    private HarborMasterInteractions() {
    }

    /** Whether the gesture is the talking one (both sides; the prisoner state is synced). */
    public static boolean isTalkGesture(Player player, Entity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof HarborMaster master) || !master.isAlive()) return false;
        if (player.isShiftKeyDown() || player.isSpectator()) return false;
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.NAME_TAG) || held.is(Items.LEAD) || held.getItem() instanceof SpawnEggItem) return false;
        return !BrigService.isPrisoner(master);
    }

    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        if (!isTalkGesture(player, target, hand)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        HarborMaster master = (HarborMaster) target;
        talk(sp, master);
        return InteractionResult.CONSUME;
    }

    /** {@code player} talks to {@code master}: the desk opens, or he says why not. Server side. */
    public static Talk talk(ServerPlayer player, HarborMaster master) {
        if (master.resents(player) || master.fleeing()) {
            player.displayClientMessage(Component.translatable(NOT_NOW).withStyle(ChatFormatting.RED), true);
            master.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return Talk.NOT_NOW;
        }
        Optional<BlockPos> desk = desk(player, master);
        if (desk.isEmpty()) {
            player.displayClientMessage(Component.translatable(NO_DESK), true);
            master.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return Talk.NO_DESK;
        }
        master.getLookControl().setLookAt(player);
        HarborDeskService.Use use = HarborDeskService.useViaHarborMaster(player, desk.get());
        if (use == HarborDeskService.Use.OPENED) {
            greet(player, master, desk.get());
            master.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
        } else if (use.message() != null) {
            player.displayClientMessage(Component.translatable(use.message()), true);
        }
        return Talk.DESK;
    }

    /**
     * The bound desk he serves: the desk nearest him within {@code desk_reach} of both him and the player, bound to
     * his own port if any is, else to any port.
     */
    public static Optional<BlockPos> desk(ServerPlayer player, HarborMaster master) {
        ServerLevel level = (ServerLevel) master.level();
        double reach = TradeConfig.DESK_REACH.get();
        int r = (int) Math.ceil(reach);
        Vec3 at = master.position();
        ResourceLocation own = master.port();
        BlockPos best = null;
        boolean bestOwn = false;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(master.blockPosition().offset(-r, -r, -r), master.blockPosition().offset(r, r, r))) {
            double d = Vec3.atCenterOf(p).distanceToSqr(at);
            if (d > reach * reach) continue;
            Optional<ResourceLocation> port = HarborDeskService.boundPort(level, p);
            if (port.isEmpty() || !HarborDeskService.inReach(player, p)) continue;
            boolean isOwn = port.get().equals(own);
            if (best == null || (isOwn && !bestOwn) || (isOwn == bestOwn && d < bestDist)) {
                best = p.immutable();
                bestOwn = isOwn;
                bestDist = d;
            }
        }
        return Optional.ofNullable(best);
    }

    private static void greet(ServerPlayer player, HarborMaster master, BlockPos desk) {
        PortKind kind = HarborDeskService.boundPort(player.level(), desk)
                .flatMap(port -> TradeService.market(player.server, port))
                .map(m -> m.profile().kind()).orElse(PortKind.SEAFARER_VILLAGE);
        player.sendSystemMessage(Component.translatable(GREETING + kind.getSerializedName(), master.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
    }
}
