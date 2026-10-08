package com.richardsenger.piratesnships.crew.hiring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * One man or woman looking for a berth at a port today (CRW1): an id for the Hire button, the name the crew member will
 * carry, the kind, and the fee in doubloons asked when the candidate was made ({@code crew.hiring.fee_<kind>}).
 */
public record Candidate(UUID id, String name, CandidateKind kind, int fee) {

    public static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Candidate::id),
            Codec.STRING.fieldOf("name").forGetter(Candidate::name),
            CandidateKind.CODEC.fieldOf("kind").forGetter(Candidate::kind),
            Codec.INT.fieldOf("fee").forGetter(Candidate::fee)
    ).apply(i, Candidate::new));
}
