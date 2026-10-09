package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;

/**
 * VIS1c: each ship's bow reaches every client that renders the ship ({@link ShipBowSync}). The starter test hull of
 * {@link SailingGameTestsShips} (helm facing north, so the bow is south, +Z) with a furled square sail, afloat. Each
 * test runs its own sender with a chosen set of tracking players (GameTest mock players are not tracked by Sable) and
 * a recording sink, and every recorded payload goes through the codec into a fresh client copy
 * ({@link ClientShipBows}), the way a joining client receives it.
 */
public final class ShipBowSyncGameTests {

    private ShipBowSyncGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipBowSyncGameTests.class);
    }

    private record Sent(UUID player, ShipBowPayload payload) {
    }

    /** One player's client: decodes what it was sent into its own copy. */
    private static final class Client {
        final UUID id = UUID.randomUUID();
        final ClientShipBows bows = new ClientShipBows();
        int received;

        void receive(ShipBowPayload p) {
            ByteBuf buf = Unpooled.buffer();
            ShipBowPayload.CODEC.encode(buf, p);
            bows.accept(ShipBowPayload.CODEC.decode(buf));
            received++;
        }
    }

    /** Runs one tick of {@code sync} for {@code tracking} and hands each payload to its client; returns what was sent. */
    private static List<Sent> tick(GameTestHelper h, ShipBowSync sync, Fixture f, List<Client> tracking) {
        Set<UUID> ids = new HashSet<>();
        for (Client c : tracking) {
            ids.add(c.id);
        }
        List<Sent> sent = new ArrayList<>();
        sync.tick(h.getLevel(), ship -> ship.id().equals(f.ship().id()) ? ids : Set.of(), (player, payload) -> {
            sent.add(new Sent(player, payload));
            for (Client c : tracking) {
                if (c.id.equals(player)) {
                    c.receive(payload);
                }
            }
        });
        return sent;
    }

    private static Fixture ship(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        return SailingGameTestsShips.assemble(h, SailingGameTestsShips.squareHull(h, 17, 17, SailTrim.FURLED));
    }

    /**
     * On assembly a tracking client gets the ship's bow at once (south: the helm faces north), once only; a second
     * client coming into range gets it on its first tick; a client that left the range gets it again when it comes back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void bowReachesFreshClientsOnAssembly(GameTestHelper h) {
        Fixture f = ship(h);
        UUID id = f.ship().id();
        h.assertTrue(f.runtime().bow().equals(BowFrame.SOUTH), "the test hull's bow is " + f.runtime().bow().name());
        ShipBowSync sync = new ShipBowSync();
        Client a = new Client();
        Client b = new Client();
        List<Sent> first = tick(h, sync, f, List.of(a));
        h.assertTrue(first.size() == 1 && first.getFirst().player().equals(a.id), "first tick sent " + first);
        h.assertTrue(BowFrame.SOUTH.equals(a.bows.bow(id)), "client A's copy has " + a.bows.bow(id));
        h.assertTrue(tick(h, sync, f, List.of(a)).isEmpty(), "the bow was sent again without a change");
        List<Sent> joined = tick(h, sync, f, List.of(a, b));
        h.assertTrue(joined.size() == 1 && joined.getFirst().player().equals(b.id), "a new tracker got " + joined);
        h.assertTrue(BowFrame.SOUTH.equals(b.bows.bow(id)), "client B's copy has " + b.bows.bow(id));
        tick(h, sync, f, List.of(b)); // A leaves the range
        a.bows.clear(); // e.g. it logged out
        List<Sent> back = tick(h, sync, f, List.of(a, b));
        h.assertTrue(back.size() == 1 && back.getFirst().player().equals(a.id), "a returning tracker got " + back);
        h.assertTrue(BowFrame.SOUTH.equals(a.bows.bow(id)), "client A's copy after returning has " + a.bows.bow(id));
        h.assertTrue(a.received == 2 && b.received == 1, "received A " + a.received + ", B " + b.received);
        h.succeed();
    }

    /**
     * A changed bow goes to every tracking client again. The bow never changes under a live runtime (it is derived from
     * the helm once and kept in the ship's user data), so the change is made the way it could arrive: a new stored bow
     * and a fresh scan of the ship.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void changedBowReachesEveryClient(GameTestHelper h) {
        Fixture f = ship(h);
        UUID id = f.ship().id();
        ShipBowSync sync = new ShipBowSync();
        Client a = new Client();
        Client b = new Client();
        tick(h, sync, f, List.of(a, b));
        h.assertTrue(BowFrame.SOUTH.equals(a.bows.bow(id)) && BowFrame.SOUTH.equals(b.bows.bow(id)), "no initial bow");
        CompoundTag data = f.ship().userData(SailingRuntimes.USER_DATA_KEY);
        data.putString("bow", "east");
        f.ship().setUserData(SailingRuntimes.USER_DATA_KEY, data);
        SailingRuntimes.onShipRemoved(h.getLevel(), id, false); // drop the runtime: the next lookup scans again
        SailingRuntime rescanned = SailingRuntimes.getOrCreate(f.ship());
        h.assertTrue(rescanned != null && rescanned.bow().equals(new BowFrame(1, 0)), "the rescan did not take the new bow");
        List<Sent> changed = tick(h, sync, f, List.of(a, b));
        h.assertTrue(changed.size() == 2, "the changed bow went out " + changed.size() + " times");
        h.assertTrue(new BowFrame(1, 0).equals(a.bows.bow(id)) && new BowFrame(1, 0).equals(b.bows.bow(id)),
                "the clients have " + a.bows.bow(id) + " and " + b.bows.bow(id));
        h.assertTrue(tick(h, sync, f, List.of(a, b)).isEmpty(), "the changed bow was sent twice");
        h.succeed();
    }
}
