package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.mob.entity.NavyOfficer;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.ShipAllegiance;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.template.ShipOrders;
import com.richardsenger.piratesnships.ship.template.ShipTemplate;
import com.richardsenger.piratesnships.ship.template.ShipTemplates;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import com.richardsenger.piratesnships.worldsim.materialize.VoyageGuns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The ship grants (docs/design.md §15, SHP1), server side: fighting ships come by rank or by capture, never from the
 * shipwright. Reaching {@code careers.ship_grants.navy_rank} (Captain) or {@code infamy_rank} (Dread Captain) hands
 * out a {@link ShipCommissionItem} once per player and ladder, through the rank rewards' path
 * ({@link CareerRewards#afterStore}; a full pack drops it). Redeemed at a navy outpost's officer or harbor master's
 * desk (navy) or a pirate island's desk, the fence (pirates), the ship is delivered like a shipwright pickup
 * ({@link ShipOrders#placeAtFreeBerth}): at the port's first free berth, assembled, owned by the player, named by the
 * player's title rule ({@link CareerShipTitles}), flying the navy flag or the Jolly Roger, its shot lockers empty. A
 * redeemed commission is consumed and noted in the ledger, so the ladder's ship is delivered once. The rules are pure
 * ({@link ShipGrantRules}). Server thread only.
 */
public final class ShipGrants {

    static final String KEY = CareerText.MSG + "ship_grant.";
    public static final String KEY_GRANTED_NAVY = KEY + "granted.navy";
    public static final String KEY_GRANTED_PIRATES = KEY + "granted.pirates";
    public static final String KEY_DELIVERED = KEY + "delivered";
    public static final String KEY_NOT_ASSEMBLED = KEY + "not_assembled";
    public static final String KEY_DISABLED = KEY + "disabled";
    public static final String KEY_NOT_YOURS = KEY + "not_yours";
    public static final String KEY_ALREADY = KEY + "already_redeemed";
    public static final String KEY_WRONG_PORT_NAVY = KEY + "wrong_port.navy";
    public static final String KEY_WRONG_PORT_PIRATES = KEY + "wrong_port.pirates";
    public static final String KEY_RANK_LOST = KEY + "rank_lost";
    public static final String KEY_NO_PORT = KEY + "no_port";
    public static final String KEY_NO_BERTH = KEY + "no_berth";
    public static final String KEY_LOST = KEY + "lost";
    public static final String KEY_CMD_RESET = CareerText.CMD + "ship_grant.reset";

    /** What a redemption did. */
    public enum Outcome { DELIVERED, REFUSED, NO_PORT, NO_BERTH, LOST }

    /**
     * What a redemption did: {@code verdict} is the rule's answer (OK once the checks passed), {@code ship} the new
     * ship (null unless delivered and assembled), {@code berth} 1-based (0 unless delivered).
     */
    public record Redeem(Outcome outcome, ShipGrantRules.Verdict verdict, Component message, @Nullable UUID ship, int berth) {
    }

    private ShipGrants() {
    }

    public static ShipGrantRules.Params params() {
        return CareerConfig.shipGrants();
    }

    /** The player's ledger (the promotion gifts' list, which also holds the grant and redemption keys). */
    static Set<String> ledger(Player player) {
        return CareerRewards.gifted(player);
    }

    // ------------------------------------------------------------------ the grant

    /**
     * After a career changed to {@code after}: hands out the commission of every ladder whose rank is now held and
     * whose grant is not in the ledger yet, and notes it. Called by {@link CareerRewards#afterStore} (careers on).
     */
    static void afterStore(ServerPlayer player, CareerRecord after) {
        ShipGrantRules.Params p = params();
        Set<String> ledger = ledger(player);
        List<ShipGrantRules.Ladder> due = ShipGrantRules.grantsDue(after, ledger, p);
        if (due.isEmpty()) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (ShipGrantRules.Ladder ladder : due) {
            ledger.add(ShipGrantRules.grantKey(ladder));
            stacks.add(commission(player, ladder, p));
            Constants.LOG.debug("Ship grant: {} commission for {}", ladder.id(), player.getGameProfile().getName());
        }
        Services.ATTACHMENTS.set(player, CareerAttachments.GIFTS, List.copyOf(ledger));
        CareerRewards.give(player, stacks);
        for (ShipGrantRules.Ladder ladder : due) {
            player.sendSystemMessage(Component.translatable(ladder == ShipGrantRules.Ladder.NAVY ? KEY_GRANTED_NAVY : KEY_GRANTED_PIRATES)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Operators ({@code /pirates career ship_grant <player> reset}): forgets the player's grants and redemptions on both
     * ladders, then grants again what the held ranks earn (careers and grants on). Returns the commissions handed out.
     */
    public static int reset(ServerPlayer player) {
        Set<String> ledger = ledger(player);
        for (ShipGrantRules.Ladder l : ShipGrantRules.Ladder.values()) {
            ledger.remove(ShipGrantRules.grantKey(l));
            ledger.remove(ShipGrantRules.redeemedKey(l));
        }
        Services.ATTACHMENTS.set(player, CareerAttachments.GIFTS, List.copyOf(ledger));
        if (!Careers.enabled()) return 0;
        int due = ShipGrantRules.grantsDue(Careers.record(player), ledger, params()).size();
        afterStore(player, Careers.record(player));
        return due;
    }

    /** A new commission of {@code ladder} naming {@code player}, for the configured template. */
    public static ItemStack commission(ServerPlayer player, ShipGrantRules.Ladder ladder, ShipGrantRules.Params p) {
        ResourceLocation template = p.template(ladder);
        String name = ShipTemplates.TYPE.server().get(template).map(ShipTemplate::name).orElse(ShipTemplates.nameKey(template));
        return ShipCommissionItem.stack(new ShipCommission(ladder, template, name, player.getUUID(), player.getGameProfile().getName()));
    }

    // ------------------------------------------------------------------ redemption

    /** {@code player} uses the harbor master's desk at {@code desk} with {@code stack}; empty if it is no commission. */
    public static Optional<Redeem> redeemAtDesk(ServerPlayer player, BlockPos desk, ItemStack stack) {
        if (ShipCommissionItem.commission(stack).isEmpty()) return Optional.empty();
        Optional<Port> port = HarborDeskService.boundPort(player.level(), desk)
                .flatMap(id -> PortRegistry.get(player.server).index().byId(id));
        return Optional.of(redeem(player, port.orElse(null), stack));
    }

    /**
     * {@code player} uses {@code officer} with {@code stack}: the officer delivers from the port he stands in. Empty
     * if it is no commission.
     */
    public static Optional<Redeem> redeemAtOfficer(ServerPlayer player, NavyOfficer officer, ItemStack stack) {
        if (ShipCommissionItem.commission(stack).isEmpty()) return Optional.empty();
        Optional<Port> port = PortRegistry.get(player.server).index().containing(officer.level().dimension(), officer.blockPosition());
        return Optional.of(redeem(player, port.orElse(null), stack));
    }

    /**
     * Redeems the commission {@code stack} at {@code port} (null: the desk or officer belongs to no port): checks the
     * rule ({@link ShipGrantRules#redeem}), then delivers the ship at the port's first free berth. Delivered, the
     * commission is consumed and noted as redeemed; otherwise it stays and the result says why.
     */
    public static Redeem redeem(ServerPlayer player, @Nullable Port port, ItemStack stack) {
        ShipCommission c = ShipCommissionItem.commission(stack).orElseThrow(() -> new IllegalArgumentException("not a commission"));
        ShipGrantRules.Params p = params();
        if (!p.enabled()) return refused(ShipGrantRules.Verdict.DISABLED, c.ladder());
        if (port == null) return result(Outcome.NO_PORT, ShipGrantRules.Verdict.OK, Component.translatable(KEY_NO_PORT));
        Set<String> ledger = ledger(player);
        ShipGrantRules.Verdict verdict = ShipGrantRules.redeem(c, player.getUUID(), port.kind(), Careers.record(player), ledger, p);
        if (verdict != ShipGrantRules.Verdict.OK) return refused(verdict, c.ladder());
        ServerLevel level = player.server.getLevel(port.dimension());
        if (level == null || ShipTemplates.TYPE.server().get(c.template()).isEmpty()) {
            Constants.LOG.warn("Ship grant: the {} commission of {} names template {}, which is gone", c.ladder().id(),
                    player.getGameProfile().getName(), c.template());
            return result(Outcome.LOST, verdict, Component.translatable(KEY_LOST));
        }
        Optional<ShipOrders.Berthed> berthed = ShipOrders.placeAtFreeBerth(level, port, c.template(), player,
                "Ship grant " + c.ladder().id() + " for " + player.getGameProfile().getName());
        if (berthed.isEmpty()) return result(Outcome.NO_BERTH, verdict, Component.translatable(KEY_NO_BERTH));

        stack.shrink(1);
        player.containerMenu.broadcastChanges();
        ledger.add(ShipGrantRules.redeemedKey(c.ladder()));
        Services.ATTACHMENTS.set(player, CareerAttachments.GIFTS, List.copyOf(ledger));
        int berth = berthed.get().berth();
        AssemblyResult a = berthed.get().placed().assembly();
        ShipBody ship = a == null || !a.success() || a.shipId() == null ? null : SableShips.byId(level, a.shipId());
        if (ship == null) {
            Constants.LOG.warn("Ship grant: {} sloop of {} placed at berth {} of {} but not assembled: {}", c.ladder().id(),
                    player.getGameProfile().getName(), berth, port.id(), a == null ? null : a.outcome());
            return new Redeem(Outcome.DELIVERED, verdict, Component.translatable(KEY_NOT_ASSEMBLED, berth), null, berth);
        }
        String name = CareerShipTitles.forNaming(player, Optional.of(player.getUUID()), ShipGrantRules.baseName(c.ladder(), player.getUUID()));
        ShipAssembler.name(ship, name);
        raiseFlag(ship, c.ladder() == ShipGrantRules.Ladder.NAVY ? FlagKind.NAVY : FlagKind.JOLLY_ROGER);
        emptyLockers(level, ship);
        Constants.LOG.debug("Ship grant: {} delivered '{}' to {} at berth {} of {}", c.ladder().id(), name,
                player.getGameProfile().getName(), berth, port.id());
        return new Redeem(Outcome.DELIVERED, verdict, Component.translatable(KEY_DELIVERED, name, berth), ship.id(), berth);
    }

    /** Hoists {@code kind} on the ship's first flagpole (the template's ensign staff). */
    private static void raiseFlag(ShipBody ship, FlagKind kind) {
        List<FlagpoleBlockEntity> poles = ShipAllegiance.poles(ship);
        if (poles.isEmpty()) return;
        if (ShipAllegiance.read(ship).kind() != kind) poles.get(0).commandSet(kind, false, null);
        ShipAllegiance.refresh(ship);
    }

    /** The ammunition is the captain's to buy: every shot locker of the new ship starts empty. */
    private static void emptyLockers(ServerLevel level, ShipBody ship) {
        for (BlockPos pos : VoyageGuns.lockers(level, ship)) {
            if (level.getBlockEntity(pos) instanceof Container c) c.clearContent();
        }
    }

    private static Redeem refused(ShipGrantRules.Verdict verdict, ShipGrantRules.Ladder ladder) {
        String key = switch (verdict) {
            case DISABLED -> KEY_DISABLED;
            case NOT_YOURS -> KEY_NOT_YOURS;
            case ALREADY_REDEEMED -> KEY_ALREADY;
            case WRONG_PORT -> ladder == ShipGrantRules.Ladder.NAVY ? KEY_WRONG_PORT_NAVY : KEY_WRONG_PORT_PIRATES;
            case RANK_LOST -> KEY_RANK_LOST;
            case OK -> throw new IllegalArgumentException("OK is no refusal");
        };
        return result(Outcome.REFUSED, verdict, Component.translatable(key));
    }

    private static Redeem result(Outcome outcome, ShipGrantRules.Verdict verdict, Component message) {
        return new Redeem(outcome, verdict, message, null, 0);
    }

    // ------------------------------------------------------------------ the officer

    /**
     * {@code CommonEvents.ENTITY_INTERACT}: a commission used on a navy officer redeems it there (before the officer's
     * own turn-ins, which take other items). A hostile officer refuses.
     */
    public static InteractionResult onEntityInteract(Player player, Entity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof NavyOfficer officer) || !officer.isAlive()) return InteractionResult.PASS;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ShipGrantContent.SHIP_COMMISSION.get()) || player.isSpectator()) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (CareerBackend.hostile(officer, sp)) {
            sp.displayClientMessage(Component.translatable(CareerText.HOSTILE).withStyle(ChatFormatting.RED), true);
            officer.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.9f);
            return InteractionResult.CONSUME;
        }
        redeemAtOfficer(sp, officer, stack).ifPresent(r -> {
            tell(sp, r);
            officer.playSound(r.outcome() == Outcome.DELIVERED ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, 1.0f, 0.9f);
        });
        return InteractionResult.CONSUME;
    }

    /** Sends the result's message to {@code player}. */
    public static void tell(ServerPlayer player, Redeem r) {
        player.displayClientMessage(r.message().copy().withStyle(r.outcome() == Outcome.DELIVERED ? ChatFormatting.GOLD : ChatFormatting.YELLOW), false);
    }

    // ------------------------------------------------------------------ text

    public static void lang(LangBuilder lang) {
        lang.item(ShipGrantContent.SHIP_COMMISSION, "Ship Commission")
                .add(ShipCommissionItem.KEY_SHIP, "Grants: %s")
                .add(ShipCommissionItem.KEY_FOR, "Made out to %s")
                .add(ShipCommissionItem.KEY_HINT_NAVY, "Hand it to a navy officer or the harbor master's desk at a navy outpost")
                .add(ShipCommissionItem.KEY_HINT_PIRATES, "Hand it in at the fence's desk on a pirate island")
                .add(KEY_GRANTED_NAVY, "The navy commissions you a ship: hand the commission to a navy officer or the harbor master's desk at a navy outpost")
                .add(KEY_GRANTED_PIRATES, "The brethren grant you a ship: hand the commission in at the fence's desk on a pirate island")
                .add(KEY_DELIVERED, "Your ship, the %s, lies at berth %s. Powder and shot are yours to buy")
                .add(KEY_NOT_ASSEMBLED, "Your ship lies at berth %s, but could not be assembled: use its helm")
                .add(KEY_DISABLED, "No ships are granted on this server")
                .add(KEY_NOT_YOURS, "This commission is made out to another captain")
                .add(KEY_ALREADY, "Your ship was already delivered")
                .add(KEY_WRONG_PORT_NAVY, "A navy commission is redeemed at a navy outpost")
                .add(KEY_WRONG_PORT_PIRATES, "The brethren deliver their ships at a pirate island")
                .add(KEY_RANK_LOST, "You no longer hold the rank this commission was granted for")
                .add(KEY_NO_PORT, "There is no port here to deliver a ship from")
                .add(KEY_NO_BERTH, "No berth is free; come back later")
                .add(KEY_LOST, "The ship of this commission cannot be built any more")
                .add(KEY_CMD_RESET, "Ship grants of %s reset; %s new commission(s) handed out");
    }
}
