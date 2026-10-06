package com.richardsenger.piratesnships.law.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One bounty on the board.
 *
 * @param id        unique id (navy bounties use {@link BountyBoard#navyBountyId}, so there is at most one per target)
 * @param target    who it is on
 * @param source    placed by the navy (automatic) or by a player at a notice board
 * @param payer     the paying player ({@code empty} for the navy)
 * @param payerName display name of the payer ("" for the navy)
 * @param amount    doubloons
 * @param createdAt game time it was placed
 * @param expiresAt game time it expires, {@link #NEVER} if it doesn't
 */
public record Bounty(UUID id, BountyTarget target, Source source, Optional<UUID> payer, String payerName, int amount,
                     long createdAt, long expiresAt) {

    public static final long NEVER = Long.MAX_VALUE;

    public enum Source implements StringRepresentable {
        NAVY, PLAYER;

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<Bounty> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Bounty::id),
            BountyTarget.CODEC.fieldOf("target").forGetter(Bounty::target),
            Source.CODEC.fieldOf("source").forGetter(Bounty::source),
            UUIDUtil.CODEC.optionalFieldOf("payer").forGetter(Bounty::payer),
            Codec.STRING.optionalFieldOf("payer_name", "").forGetter(Bounty::payerName),
            Codec.INT.fieldOf("amount").forGetter(Bounty::amount),
            Codec.LONG.fieldOf("created_at").forGetter(Bounty::createdAt),
            Codec.LONG.optionalFieldOf("expires_at", NEVER).forGetter(Bounty::expiresAt)
    ).apply(i, Bounty::new));

    public boolean isNavy() {
        return source == Source.NAVY;
    }

    public boolean expiredAt(long now) {
        return now >= expiresAt;
    }

    public Bounty withAmount(int newAmount) {
        return new Bounty(id, target, source, payer, payerName, newAmount, createdAt, expiresAt);
    }
}
