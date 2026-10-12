package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.trade.client.MarketLines;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.world.WorldConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Port names in a real server (NAME3): registered ports get a unique name of their kind, the server shows it wherever a
 * port is named ({@link PortNameBook}), the client copy arrives through {@link PortNameBook.Sync}, {@code world.ports.names}
 * off falls back to the readable ids, and {@code /pirates world port} accepts the id and the name. The world tests of
 * the three port structures check their generated ports through {@link #assertNamed}.
 */
public final class PortNameGameTests {

    private static final String BATCH = "pirates_n_ships_port_names";

    private PortNameGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(PortNameGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** Asserts that a generated or registered port has a name of its kind that no other registered port shares. */
    public static void assertNamed(GameTestHelper h, Port port) {
        Port stored = PortRegistry.get(h.getLevel().getServer()).index().byId(port.id()).orElse(port);
        String name = stored.name();
        h.assertTrue(!name.isEmpty(), "port " + port.id() + " has a name");
        h.assertTrue(fitsKind(name, stored.kind()), "\"" + name + "\" reads like a " + stored.kind());
        long same = PortRegistry.get(h.getLevel().getServer()).index().all().stream()
                .filter(p -> PortNames.key(p.name()).equals(PortNames.key(name))).count();
        h.assertValueEqual(same, 1L, "ports named " + name);
        if (WorldConfig.PORT_NAMES.get()) h.assertValueEqual(MarketLines.portName(port.id()), name, "the shown name");
    }

    static boolean fitsKind(String name, PortKind kind) {
        boolean fort = name.startsWith("Fort ") || name.endsWith(" Battery") || name.contains(" Battery ");
        boolean island = name.contains(" Cay") || name.contains("'s ") || name.contains("' ") || name.matches("\\S+ \\S+( [IVX]+)?") && !fort
                && !name.startsWith("Port ") && !name.contains(" Harbour") && !name.contains(" Quay");
        return switch (kind) {
            case NAVY_OUTPOST -> fort;
            case PIRATE_ISLAND -> !fort && island && !name.startsWith("St. ");
            case SEAFARER_VILLAGE -> !fort && !name.contains(" Cay");
        };
    }

    /** A port of {@code kind} at the test's origin with a one-block box (it binds nothing). */
    static Port port(GameTestHelper h, PortKind kind, int dx) {
        BlockPos at = h.absolutePos(new BlockPos(dx, 1, 0));
        return new Port(PortService.portId(kind, at), kind, h.getLevel().dimension(), at, new BoundingBox(at), Climate.TEMPERATE, List.of());
    }

    static List<Component> command(GameTestHelper h, String command) {
        List<Component> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.add(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withSource(capture).withLevel(h.getLevel()).withPermission(4);
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return out;
    }

    private static String text(List<Component> lines) {
        StringBuilder sb = new StringBuilder();
        for (Component c : lines) sb.append(c.getString()).append('\n');
        return sb.toString();
    }

    private static void remove(MinecraftServer server, Port... ports) {
        for (Port p : ports) PortRegistry.get(server).remove(p.id());
    }

    // ------------------------------------------------------------------ tests

    /** Three ports of the three kinds register with three names of their kinds, shown by name on the server. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = BATCH)
    public static void registeredPortsAreNamedByKind(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, PortKind.SEAFARER_VILLAGE, 0), outpost = port(h, PortKind.NAVY_OUTPOST, 0),
                island = port(h, PortKind.PIRATE_ISLAND, 0);
        for (Port p : List.of(village, outpost, island)) h.assertTrue(PortService.register(server, p), "registered " + p.id());
        Set<String> names = new HashSet<>();
        for (Port p : List.of(village, outpost, island)) {
            assertNamed(h, p);
            names.add(PortRegistry.get(server).index().byId(p.id()).orElseThrow().name());
        }
        h.assertValueEqual(names.size(), 3, "three names");
        // the id stays the key: lookups by id, the market and the desk binding are unchanged
        h.assertTrue(com.richardsenger.piratesnships.trade.TradeService.market(server, village.id()).isPresent(), "market under the id");
        String name = PortRegistry.get(server).index().byId(outpost.id()).orElseThrow().name();
        h.assertValueEqual(PortNameBook.labelWithId(outpost.id()), name + " (" + outpost.id() + ")", "operators see both");
        h.assertValueEqual(PortRegistry.get(server).byName(name.toLowerCase(java.util.Locale.ROOT)).map(Port::id), Optional.of(outpost.id()),
                "found by name in any case");
        remove(server, village, outpost, island);
        h.succeed();
    }

    /** {@code world.ports.names} off: every shown name is the readable id again; the names stay stored. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = "pirates_n_ships_config_world_port_names")
    public static void namesOffShowTheIds(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port village = port(h, PortKind.SEAFARER_VILLAGE, 1);
        PortService.register(server, village);
        String name = PortRegistry.get(server).index().byId(village.id()).orElseThrow().name();
        ConfigOverrides.during(h, WorldConfig.PORT_NAMES, false);
        h.assertValueEqual(MarketLines.portName(village.id()), PortNames.fromId(village.id()), "the readable id while off");
        h.assertValueEqual(PortNameBook.labelWithId(village.id()), village.id().toString(), "operators see the id");
        h.assertTrue(PortNameBook.snapshot(server).isEmpty(), "clients get no names while off");
        h.assertValueEqual(PortRegistry.get(server).index().byId(village.id()).orElseThrow().name(), name, "the name stays stored");
        h.assertTrue(text(command(h, "pirates world port \"" + name + "\"")).contains(village.id().toString()),
                "the command still accepts the name");
        remove(server, village);
        h.succeed();
    }

    /** The client copy: the sync payload survives its codec and a non-server thread reads the names from it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = BATCH)
    public static void theClientGetsTheNamesBySync(GameTestHelper h) throws InterruptedException {
        MinecraftServer server = h.getLevel().getServer();
        Port island = port(h, PortKind.PIRATE_ISLAND, 2);
        PortService.register(server, island);
        String name = PortRegistry.get(server).index().byId(island.id()).orElseThrow().name();
        Map<ResourceLocation, String> snapshot = PortNameBook.snapshot(server);
        h.assertValueEqual(snapshot.get(island.id()), name, "the snapshot carries the name");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        PortNameBook.Sync.CODEC.encode(buf, new PortNameBook.Sync(snapshot));
        PortNameBook.Sync back = PortNameBook.Sync.CODEC.decode(buf);
        buf.release();
        h.assertValueEqual(back.names(), snapshot, "codec round trip");

        PortNameBook.accept(back);
        AtomicReference<String> seen = new AtomicReference<>();
        Thread client = new Thread(() -> seen.set(PortNameBook.label(island.id())), "name3-fake-client");
        client.start();
        client.join(5_000);
        PortNameBook.clearClient();
        h.assertValueEqual(seen.get(), name, "a client thread reads the synced name");
        Thread after = new Thread(() -> seen.set(PortNameBook.label(island.id())), "name3-fake-client-2");
        after.start();
        after.join(5_000);
        h.assertValueEqual(seen.get(), PortNames.fromId(island.id()), "after leaving the world: the readable id");
        remove(server, island);
        h.succeed();
    }

    /** {@code /pirates world port} takes the id, the bare path, the quoted name and a unique start; a shared start asks which. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = BATCH)
    public static void theWorldPortCommandAcceptsIdAndName(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Port a = port(h, PortKind.SEAFARER_VILLAGE, 0).withName("Name3 Testharbour Alpha");
        Port b = port(h, PortKind.NAVY_OUTPOST, 0).withName("Name3 Testharbour Beta");
        PortRegistry.get(server).add(a);
        PortRegistry.get(server).add(b);
        h.assertValueEqual(PortRegistry.get(server).index().byId(a.id()).orElseThrow().name(), "Name3 Testharbour Alpha", "a free name is kept");

        String byId = text(command(h, "pirates world port " + a.id()));
        h.assertTrue(byId.contains("Name3 Testharbour Alpha") && byId.contains(a.id().toString()), "by id: " + byId);
        String byPath = text(command(h, "pirates world port " + b.id().getPath()));
        h.assertTrue(byPath.contains("Name3 Testharbour Beta"), "by the id's path: " + byPath);
        String byName = text(command(h, "pirates world port \"name3 testharbour beta\""));
        h.assertTrue(byName.contains(b.id().toString()), "by the name in any case: " + byName);
        String ambiguous = text(command(h, "pirates world port \"Name3 Testharbour\""));
        h.assertTrue(ambiguous.contains("Name3 Testharbour Alpha") && ambiguous.contains("Name3 Testharbour Beta")
                && ambiguous.contains("which one"), "a shared start asks which: " + ambiguous);
        String unknown = text(command(h, "pirates world port \"Name3 Nowhere\""));
        h.assertTrue(unknown.contains("No port"), "unknown: " + unknown);
        String list = text(command(h, "pirates world ports"));
        h.assertTrue(list.contains("Name3 Testharbour Alpha (" + a.id() + ")"), "the list names ports: " + list);
        remove(server, a, b);
        h.succeed();
    }
}
