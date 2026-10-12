package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.hiring.Candidate;
import com.richardsenger.piratesnships.crew.hiring.CandidateKind;
import com.richardsenger.piratesnships.crew.hiring.Hiring;
import com.richardsenger.piratesnships.crew.hiring.HiringConfig;
import com.richardsenger.piratesnships.crew.hiring.HiringText;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.brig.BrigService;
import com.richardsenger.piratesnships.trade.net.MarketBackend;
import com.richardsenger.piratesnships.trade.stall.StallKind;
import com.richardsenger.piratesnships.world.port.Port;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;

/**
 * Talking to a villager (LIFE1), from {@code VillageResident#mobInteract}. The main hand, the villager alive and not a
 * prisoner (a name tag, a lead or a spawn egg keeps its own use):
 *
 * <ul>
 *   <li><b>Use:</b> a vendor or a shipwright opens his stall: the market screen with the stall's wares
 *       ({@code MarketBackend.openStall}); at night the stall is shut. Anyone else says a word about its work.</li>
 *   <li><b>Sneak-use:</b> hiring. The first names the fee ({@code crew.hiring.fee_sailor}) and the wage; a second within
 *       {@code mobs.villagers.hire_confirm_ticks} signs it on through the hiring rules of the harbor desk
 *       ({@link Hiring#hireCandidate}: standing, the player's own ship moored at the port, a free bunk, the fee). The
 *       recruit goes aboard keeping its outfit ({@link CrewMember#setOutfit}) and its slot in the village is free
 *       ({@code respawn_days}).</li>
 * </ul>
 * A villager running from danger, hurt a moment ago or asleep has no time for either. All answers go to the chat.
 */
public final class ResidentInteractions {

    public static final String KEY = "message." + Constants.MOD_ID + ".villager.";
    public static final String NOT_NOW = KEY + "not_now";
    public static final String ASLEEP = KEY + "asleep";
    public static final String STALL_SHUT = KEY + "stall_shut";
    public static final String NO_MARKET = KEY + "no_market";
    public static final String HIRE_OFFER = KEY + "hire_offer";
    public static final String NO_VILLAGE = KEY + "no_village";
    public static final String GREETING = KEY + "greeting.";
    public static final String STALL_OPEN = KEY + "stall.";

    /** What a talk did (tests). */
    public enum Talk { NOT_A_GESTURE, NOT_NOW, GREETING, STALL, STALL_SHUT, HIRE_OFFER, HIRED, HIRE_REFUSED }

    private ResidentInteractions() {
    }

    static boolean isGesture(Player player, VillageResident v, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !v.isAlive() || player.isSpectator()) return false;
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.NAME_TAG) || held.is(Items.LEAD) || held.getItem() instanceof SpawnEggItem) return false;
        return !BrigService.isPrisoner(v);
    }

    static InteractionResult interact(Player player, VillageResident v, InteractionHand hand) {
        if (!isGesture(player, v, hand)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            hire(sp, v);
        } else {
            talk(sp, v);
        }
        return InteractionResult.CONSUME;
    }

    /** Busy with danger: hurt a moment ago, burning, or something it runs from is near. */
    static boolean shaken(VillageResident v) {
        return v.isOnFire() || v.getLastHurtByMob() != null && v.tickCount - v.getLastHurtByMobTimestamp() < 100;
    }

    /** {@code player} talks to {@code v}: its stall opens, or it says a word. Server side. */
    public static Talk talk(ServerPlayer player, VillageResident v) {
        if (shaken(v)) {
            say(player, Component.translatable(NOT_NOW, v.getDisplayName()).withStyle(ChatFormatting.RED));
            v.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return Talk.NOT_NOW;
        }
        if (v.isAsleep()) {
            say(player, Component.translatable(ASLEEP, v.getDisplayName()));
            return Talk.NOT_NOW;
        }
        v.getLookControl().setLookAt(player);
        Optional<StallKind> stall = v.plan().stall();
        if (stall.isPresent() && v.port() != null) {
            if (VillageRoutine.step(v.role(), v.level().getDayTime(), VillagerConfig.ROUTINES.get()).activity() == VillageRoutine.Activity.SLEEP) {
                say(player, Component.translatable(STALL_SHUT, v.getDisplayName()));
                return Talk.STALL_SHUT;
            }
            if (!MarketBackend.openStall(player, v.port(), v.plan().home().pos(), stall.get())) {
                say(player, Component.translatable(NO_MARKET, v.getDisplayName()));
                return Talk.NOT_NOW;
            }
            say(player, Component.translatable(STALL_OPEN + stall.get().id(), v.getDisplayName()).withStyle(ChatFormatting.GRAY));
            v.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
            return Talk.STALL;
        }
        say(player, Component.translatable(GREETING + v.role().id(), v.getDisplayName()).withStyle(ChatFormatting.GRAY));
        v.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, 1.0f);
        return Talk.GREETING;
    }

    /** {@code player} sneak-uses {@code v}: the fee first, the hire on the second within the window. Server side. */
    public static Talk hire(ServerPlayer player, VillageResident v) {
        String name = v.getDisplayName().getString();
        if (!Hiring.enabled()) {
            say(player, Component.translatable(HiringText.result(HiringText.DISABLED), name));
            return Talk.HIRE_REFUSED;
        }
        if (shaken(v) || v.isAsleep()) {
            say(player, Component.translatable(NOT_NOW, v.getDisplayName()).withStyle(ChatFormatting.RED));
            return Talk.NOT_NOW;
        }
        Optional<Port> port = v.port() == null ? Optional.empty() : Hiring.port(player.server, v.port());
        if (port.isEmpty()) {
            say(player, Component.translatable(NO_VILLAGE, v.getDisplayName()));
            return Talk.HIRE_REFUSED;
        }
        CandidateKind kind = CandidateKind.at(port.get().kind());
        int fee = HiringConfig.fee(kind);
        if (!v.hasHireOffer(player)) {
            v.offerHire(player, v.level().getGameTime() + VillagerConfig.HIRE_CONFIRM_TICKS.get());
            v.getLookControl().setLookAt(player);
            say(player, Component.translatable(HIRE_OFFER, v.getDisplayName(),
                    Component.translatable(roleKey(v.role())), fee).withStyle(ChatFormatting.GRAY));
            return Talk.HIRE_OFFER;
        }
        v.clearHireOffer();
        Hiring.Result r = hireNow(player, v, port.get(), new Candidate(v.getUUID(), name, kind, fee));
        say(player, r.message());
        return r.done() ? Talk.HIRED : Talk.HIRE_REFUSED;
    }

    /**
     * Signs {@code v} on as {@code candidate} for {@code player} at {@code port}: the recruit aboard in {@code v}'s
     * outfit, {@code v} gone from the village (its slot free from today). Server side; the second sneak-use and tests.
     */
    public static Hiring.Result hireNow(ServerPlayer player, VillageResident v, Port port, Candidate candidate) {
        Hiring.Result r = Hiring.hireCandidate(player, port, candidate);
        if (!r.done()) return r;
        CrewMember crew = r.crew();
        if (crew != null) crew.setOutfit(v.role().id());
        if (v.getVehicle() instanceof BenchSeat) v.stopRiding();
        v.discard(); // remove() marks its slot free (Residents.onLost)
        return r;
    }

    /** The lang key of a role's name ("fishwife", "net mender", ...). */
    public static String roleKey(VillageRole role) {
        return KEY + "role." + role.id();
    }

    private static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
