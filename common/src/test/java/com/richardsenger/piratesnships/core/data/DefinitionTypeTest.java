package com.richardsenger.piratesnships.core.data;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.CoreDefinitions;
import com.richardsenger.piratesnships.core.CoreDefinitions.TestMarker;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Type registry, side separation and the sync payload, without a loader. */
class DefinitionTypeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void namesAreUniqueAndValidated() {
        DefinitionType.create("junit_unique", Codec.STRING);
        assertThrows(IllegalStateException.class, () -> DefinitionType.create("junit_unique", Codec.STRING));
        assertThrows(IllegalArgumentException.class, () -> DefinitionType.create("Bad Name", Codec.STRING));
        assertThrows(IllegalArgumentException.class, () -> DefinitionType.create("", Codec.STRING));
        assertTrue(DefinitionType.byName("junit_unique").isPresent());
    }

    @Test
    void directoryFollowsConvention() {
        assertEquals("pirates_n_ships/test_marker", CoreDefinitions.TEST_MARKER.directory());
    }

    @Test
    void serverAndClientStoresAreSeparate() {
        DefinitionType<String> type = DefinitionType.createSynced("junit_sides", Codec.STRING);
        List<Definitions<String>> serverEvents = new ArrayList<>();
        type.onServerReload(serverEvents::add);
        type.onServerReload(d -> { throw new RuntimeException("listener failure must not break the reload"); });
        ResourceLocation id = Constants.id("x");

        type.acceptServer(Map.of(id, "server"));
        assertEquals("server", type.server().require(id));
        assertTrue(type.client().isEmpty(), "client store untouched by the server reload");

        type.acceptClient(Map.of(id, "client"));
        assertEquals("server", type.of(false).require(id), "client sync must not clobber the server store");
        assertEquals("client", type.of(true).require(id));
        assertEquals(1, serverEvents.size());
        assertSame(type.server(), serverEvents.getFirst());
    }

    @Test
    void syncPayloadRoundTrips() {
        ResourceLocation id = Constants.id("round_trip");
        CoreDefinitions.TEST_MARKER.acceptServer(Map.of(id, new TestMarker("rt", 7)));
        DefinitionSyncPayload payload = DefinitionSyncPayload.of(CoreDefinitions.TEST_MARKER);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        DefinitionSyncPayload.STREAM_CODEC.encode(buf, payload);
        DefinitionSyncPayload decoded = DefinitionSyncPayload.STREAM_CODEC.decode(buf);
        assertSame(CoreDefinitions.TEST_MARKER, decoded.definitionType());
        assertEquals(Map.of(id, new TestMarker("rt", 7)), decoded.entries());

        decoded.applyOnClient();
        assertEquals(new TestMarker("rt", 7), CoreDefinitions.TEST_MARKER.client().require(id));
        CoreDefinitions.TEST_MARKER.acceptServer(Map.of());
        CoreDefinitions.TEST_MARKER.acceptClient(Map.of());
    }

    @Test
    void codecSyncedTypesRoundTripToo() {
        DefinitionType<String> type = DefinitionType.createSynced("junit_codec_sync", Codec.STRING);
        type.acceptServer(Map.of(Constants.id("s"), "hello"));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        DefinitionSyncPayload.STREAM_CODEC.encode(buf, DefinitionSyncPayload.of(type));
        assertEquals(Map.of(Constants.id("s"), "hello"), DefinitionSyncPayload.STREAM_CODEC.decode(buf).entries());
    }

    @Test
    void unsyncedTypesCannotBeSent() {
        DefinitionType<String> type = DefinitionType.create("junit_unsynced", Codec.STRING);
        assertThrows(IllegalArgumentException.class, () -> DefinitionSyncPayload.of(type));
    }
}
