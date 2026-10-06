package com.richardsenger.piratesnships.law.sync;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.proof.BountyProof;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Codec round trips of the wanted sync payload and the bounty proof component. */
class WantedSyncPayloadTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void payloadRoundTrip() {
        for (WantedLevel level : WantedLevel.values()) {
            WantedSyncPayload p = new WantedSyncPayload(level, 1234);
            ByteBuf buf = Unpooled.buffer();
            WantedSyncPayload.CODEC.encode(buf, p);
            assertEquals(p, WantedSyncPayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void payloadNormalizesInput() {
        assertEquals(new WantedSyncPayload(WantedLevel.CLEAN, 0), new WantedSyncPayload(null, -5));
    }

    @Test
    void proofRoundTrips() {
        BountyProof proof = new BountyProof(UUID.randomUUID(), "Blackbeard", 123456789L, "Hunter");
        ByteBuf buf = Unpooled.buffer();
        BountyProof.STREAM_CODEC.encode(buf, proof);
        assertEquals(proof, BountyProof.STREAM_CODEC.decode(buf));
        var json = BountyProof.CODEC.encodeStart(JsonOps.INSTANCE, proof).getOrThrow();
        assertEquals(proof, BountyProof.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
