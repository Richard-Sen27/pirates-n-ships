package com.richardsenger.piratesnships.sailing.wind;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindSyncTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearClient() {
        ClientWind.reset();
    }

    @Test
    void payloadRoundTrips() {
        WindSyncPayload p = WindSyncPayload.of(WindSample.of(123.5, 7.25, 1.5, 0.3), 20);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WindSyncPayload.CODEC.encode(buf, p);
        WindSyncPayload back = WindSyncPayload.CODEC.decode(buf);
        assertEquals(p, back);
        assertEquals(0, buf.readableBytes());
        assertEquals(123.5, back.toSample().towardDegrees(), 1e-4);
    }

    @Test
    void blendTurnsAlongTheShortArc() {
        WindBlend b = new WindBlend(WindSample.of(350, 4, 1, 0), WindSample.of(10, 8, 1, 0), 100, 20);
        assertEquals(350.0, b.at(100).towardDegrees(), 1e-9);
        assertEquals(0.0, b.at(110).towardDegrees(), 1e-9);
        assertEquals(6.0, b.at(110).strength(), 1e-9);
        assertEquals(b.target(), b.at(120));
        assertEquals(b.target(), b.at(500));
        assertEquals(b.from().towardDegrees(), b.at(0).towardDegrees(), 1e-9);
    }

    @Test
    void clientHolderShowsFirstSampleAtOnceThenBlends() {
        assertFalse(ClientWind.hasData());
        ClientWind.accept(WindSyncPayload.of(WindSample.of(90, 5, 1, 0), 20), 1000);
        assertTrue(ClientWind.hasData());
        assertEquals(90.0, ClientWind.sample(1000).towardDegrees(), 1e-4);
        ClientWind.accept(WindSyncPayload.of(WindSample.of(110, 5, 1, 0), 20), 1020);
        assertEquals(90.0, ClientWind.sample(1020).towardDegrees(), 1e-4);
        assertEquals(100.0, ClientWind.sample(1030).towardDegrees(), 1e-4);
        assertEquals(110.0, ClientWind.sample(1040).towardDegrees(), 1e-4);
    }
}
