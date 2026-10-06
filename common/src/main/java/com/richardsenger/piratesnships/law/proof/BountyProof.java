package com.richardsenger.piratesnships.law.proof;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Data component of a bounty proof item (docs/design.md §13.2 "defeat the target and bring a proof item").
 *
 * @param target     UUID of the killed target (what the claim looks up on the bounty board)
 * @param targetName the target's name at the time of the kill (tooltip, notices)
 * @param killedAt   overworld game time of the kill: only bounties placed at or before it can be claimed with this
 *                   proof, so one kill can't be kept to collect bounties placed later
 * @param killerName who made the kill (tooltip only; anyone holding the proof may claim)
 */
public record BountyProof(UUID target, String targetName, long killedAt, String killerName) {

    public static final Codec<BountyProof> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("target").forGetter(BountyProof::target),
            Codec.STRING.fieldOf("target_name").forGetter(BountyProof::targetName),
            Codec.LONG.fieldOf("killed_at").forGetter(BountyProof::killedAt),
            Codec.STRING.optionalFieldOf("killer_name", "").forGetter(BountyProof::killerName)
    ).apply(i, BountyProof::new));

    public static final StreamCodec<ByteBuf, BountyProof> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BountyProof::target,
            ByteBufCodecs.STRING_UTF8, BountyProof::targetName,
            ByteBufCodecs.VAR_LONG, BountyProof::killedAt,
            ByteBufCodecs.STRING_UTF8, BountyProof::killerName,
            BountyProof::new);
}
