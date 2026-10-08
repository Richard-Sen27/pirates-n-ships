package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.trade.coin.Wallet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prize money for pirate ships (docs/design.md §15 "Privateer", QST2): a player holding a valid letter of marque who
 * sinks (as the last cannon shooter) or captures an NPC ship under the pirates' colours is paid
 * {@code careers.prize_money} doubloons at once through the wallet, with a chat line, once per voyage. Without a
 * letter, with a voided one, or while careers or letters are off, nothing. The world simulation's ending listener in
 * the quest module ({@code rpg.quest.SeaQuests}) calls {@link #award}.
 */
public final class ShipPrizes {

    public static final long DEFAULT_PRIZE = 60;
    public static final String PAID = CareerText.MSG + "prize.ship";

    /** Voyages already paid for (the ending fires once per voyage; this guards a repeated call). */
    private static final Map<UUID, Boolean> PAID_VOYAGES = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 512;
        }
    });

    private ShipPrizes() {
    }

    /**
     * The prize for beating a ship of {@code faction} ({@code beaten}: sunk or captured, not merely plundered) with the
     * letter in {@code letter}: {@code amount} for a pirate ship under an {@link LetterState#ACTIVE} letter while careers
     * and letters are on, else 0.
     */
    public static long prize(LetterState letter, boolean careersOn, boolean lettersOn, Faction faction, boolean beaten, long amount) {
        if (!careersOn || !lettersOn || !beaten || faction != Faction.PIRATES || letter != LetterState.ACTIVE) return 0;
        return Math.max(0, amount);
    }

    /**
     * Pays {@code player} for the voyage {@code voyage}'s ship of {@code faction} if {@link #prize} says so and the
     * voyage was not paid for yet. Returns the doubloons paid.
     */
    public static long award(ServerPlayer player, UUID voyage, Faction faction, boolean beaten) {
        long coins = prize(Careers.record(player).letter(), Careers.enabled(), CareerConfig.LETTER_ENABLED.get(), faction, beaten,
                CareerConfig.PRIZE_MONEY.get());
        if (coins <= 0 || PAID_VOYAGES.putIfAbsent(voyage, Boolean.TRUE) != null) return 0;
        Wallet.give(player, coins);
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable(PAID, coins).withStyle(ChatFormatting.GOLD));
        return coins;
    }

    /** Forgets the paid voyages (server stop). */
    public static void clear() {
        PAID_VOYAGES.clear();
    }

    public static void lang(LangBuilder lang) {
        lang.add(PAID, "Prize money under your letter of marque: %s doubloons for the pirate ship.");
    }
}
