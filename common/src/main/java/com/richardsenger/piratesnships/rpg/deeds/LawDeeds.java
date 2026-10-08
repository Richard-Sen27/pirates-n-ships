package com.richardsenger.piratesnships.rpg.deeds;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.rpg.reputation.ReputationConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deeds from the law module (docs/design.md §13, §15). {@code LawService} calls these in one line each: every reported
 * crime ({@link #onCrime}, before the criminal score's own toggle, so reputation works without it), every fine paid
 * ({@link #onFinePaid}) and every pirate turned in ({@link #onPirateTurnedIn}). Only players have reputation.
 */
public final class LawDeeds {

    private record Key(UUID offender, UUID victim) {
    }

    /** Last ship hit per player and ship, for the repeat window of {@link Deed#ATTACK_MERCHANT_SHIP}. */
    private static final Map<Key, Long> LAST_SHIP_HIT = new ConcurrentHashMap<>();

    private LawDeeds() {
    }

    /**
     * The deed a crime is, if any. Pure. Combat crimes are not here: {@link CombatDeeds} records attacks and kills on
     * navy, pirates and villagers itself, since killing a pirate is no crime.
     */
    public static Optional<Deed> deedForCrime(CrimeType type) {
        return switch (type) {
            case CAUGHT_FALSE_COLORS -> Optional.of(Deed.FLY_FALSE_COLOURS);
            case PIRACY -> Optional.of(Deed.PLUNDER_MERCHANT);
            case ATTACK_NEUTRAL_SHIP -> Optional.of(Deed.ATTACK_MERCHANT_SHIP);
            default -> Optional.empty();
        };
    }

    /** {@code LawService.reportCrime}: a crime by {@code offender} against {@code victim} (may be {@code null}). */
    public static void onCrime(LivingEntity offender, CrimeType type, @Nullable UUID victim) {
        if (!(offender instanceof ServerPlayer player)) return;
        Optional<Deed> deed = deedForCrime(type);
        if (deed.isEmpty()) return;
        if (deed.get() == Deed.ATTACK_MERCHANT_SHIP && victim != null) {
            // A broadside is many hits: one deed per ship and repeat window
            long now = player.level().getGameTime();
            Key key = new Key(player.getUUID(), victim);
            if (!CombatDeeds.newAttack(LAST_SHIP_HIT.get(key), now, ReputationConfig.ATTACK_REPEAT_SECONDS.get() * 20L)) return;
            LAST_SHIP_HIT.put(key, now);
        }
        Deeds.record(player, deed.get(), DeedContext.victim(victim));
    }

    /** {@code LawService.payFine}: a fine that removed points is the {@link Deed#PAY_FINE} deed. */
    public static void onFinePaid(LivingEntity payer, CriminalRecord.FineResult result) {
        if (payer instanceof ServerPlayer player && result.pointsRemoved() > 0) {
            Deeds.record(player, Deed.PAY_FINE, DeedContext.amount(result.doubloonsSpent()));
        }
    }

    /** {@code LawService.turnInPirate}: {@code claimant} delivered a captured pirate to the navy. */
    public static void onPirateTurnedIn(Player claimant, LivingEntity pirate) {
        if (claimant instanceof ServerPlayer player) Deeds.record(player, Deed.TURN_IN_PIRATE, DeedContext.victim(pirate));
    }

    public static void clear() {
        LAST_SHIP_HIT.clear();
    }
}
