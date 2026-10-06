package com.richardsenger.piratesnships.law.brig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * The shackles state of one entity (design.md §13.3), stored as a persistent, synced attachment
 * ({@link BrigService#PRISONER}). Immutable.
 *
 * @param captor     the player who holds the prisoner (later also a ship's captain)
 * @param captorName for messages and the debug list while the captor is offline
 * @param capturedAt game time of the capture
 * @param led        whether the prisoner currently follows its captor
 * @param releaseAt  game time at which a captured player goes free on their own, {@code -1} for NPCs
 */
public record PrisonerState(UUID captor, String captorName, long capturedAt, boolean led, long releaseAt) {

    private static final UUID NIL = new UUID(0L, 0L);

    /** "Not a prisoner": the attachment's default value. */
    public static final PrisonerState NONE = new PrisonerState(NIL, "", 0L, false, -1L);

    public static final Codec<PrisonerState> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("captor").forGetter(PrisonerState::captor),
            Codec.STRING.optionalFieldOf("captor_name", "").forGetter(PrisonerState::captorName),
            Codec.LONG.fieldOf("captured_at").forGetter(PrisonerState::capturedAt),
            Codec.BOOL.optionalFieldOf("led", false).forGetter(PrisonerState::led),
            Codec.LONG.optionalFieldOf("release_at", -1L).forGetter(PrisonerState::releaseAt)
    ).apply(i, PrisonerState::new));

    public static final StreamCodec<ByteBuf, PrisonerState> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PrisonerState::captor,
            ByteBufCodecs.STRING_UTF8, PrisonerState::captorName,
            ByteBufCodecs.VAR_LONG, PrisonerState::capturedAt,
            ByteBufCodecs.BOOL, PrisonerState::led,
            ByteBufCodecs.VAR_LONG, PrisonerState::releaseAt,
            PrisonerState::new);

    /** A fresh capture: led by its captor right away. */
    public static PrisonerState captured(UUID captor, String captorName, long now, long releaseAt) {
        return new PrisonerState(captor, captorName, now, true, releaseAt);
    }

    /** Whether this is a real prisoner state (not {@link #NONE}). */
    public boolean active() {
        return !NIL.equals(captor);
    }

    public boolean heldBy(UUID player) {
        return active() && captor.equals(player);
    }

    public boolean hasTimeLimit() {
        return releaseAt >= 0;
    }

    public boolean expired(long now) {
        return hasTimeLimit() && now >= releaseAt;
    }

    public PrisonerState withLed(boolean value) {
        return new PrisonerState(captor, captorName, capturedAt, value, releaseAt);
    }

    public PrisonerState withCaptor(UUID newCaptor, String newName) {
        return new PrisonerState(newCaptor, newName, capturedAt, led, releaseAt);
    }
}
