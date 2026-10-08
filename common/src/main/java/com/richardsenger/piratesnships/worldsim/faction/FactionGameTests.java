package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.flag.Faction;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * The faction state of WS1 (design.md §10.4) in a running server. The state is one per world, so every test runs in a
 * batch of its own, starts from a known state and puts the previous state (and the last day seen) back when it ends;
 * each test runs within one tick, so the real day cannot turn in between.
 */
public final class FactionGameTests {

    private static final double EPS = 1e-9;

    private FactionGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(FactionGameTests.class);
    }

    /** Runs {@code body} on {@link FactionState#INITIAL} and restores the world's faction data afterwards. */
    private static void withCleanState(GameTestHelper h, Consumer<MinecraftServer> body) {
        MinecraftServer server = h.getLevel().getServer();
        FactionData data = FactionData.get(server);
        FactionState before = data.state();
        long lastDay = data.lastDay();
        try {
            Factions.set(server, FactionState.INITIAL);
            body.accept(server);
        } finally {
            data.setState(before);
            data.setLastDay(lastDay);
        }
        h.succeed();
    }

    /** Two pirate kills by the navy raise Navy–Pirates tension each time; it never goes above 1. */
    @ModGameTest(batch = "pirates_n_ships_worldsim_faction_kills")
    public static void pirateKillsRaiseTensionUpToOne(GameTestHelper h) {
        withCleanState(h, server -> {
            double t0 = Factions.tension(server, Faction.NAVY, Faction.PIRATES);
            h.assertTrue(Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY), "first report changes the state");
            double t1 = Factions.tension(server, Faction.PIRATES, Faction.NAVY);
            h.assertTrue(Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY), "second report changes the state");
            double t2 = Factions.tension(server, Faction.NAVY, Faction.PIRATES);
            h.assertTrue(t1 > t0 && t2 > t1, "tension rises: " + t0 + " -> " + t1 + " -> " + t2);
            h.assertTrue(Factions.aggression(server, Faction.PIRATES) > FactionState.REST_AGGRESSION, "pirates grow angrier");

            Factions.set(server, Factions.state(server).withTension(FactionPair.NAVY_PIRATES, 0.98));
            Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY);
            Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY);
            double top = Factions.tension(server, Faction.NAVY, Faction.PIRATES);
            h.assertTrue(Math.abs(top - 1.0) < EPS, "tension capped at 1, was " + top);
        });
    }

    /** A delivered convoy makes the merchants richer. */
    @ModGameTest(batch = "pirates_n_ships_worldsim_faction_convoy")
    public static void convoyDeliveredRaisesMerchantWealth(GameTestHelper h) {
        withCleanState(h, server -> {
            long before = Factions.wealth(server, Faction.MERCHANTS);
            Factions.report(server, FactionEvent.CONVOY_DELIVERED);
            long after = Factions.wealth(server, Faction.MERCHANTS);
            h.assertTrue(after > before, "merchant wealth " + before + " -> " + after);
        });
    }

    /** One overworld day passing moves aggression and tension back by {@code decay_per_day}, once. */
    @ModGameTest(batch = "pirates_n_ships_config_worldsim_faction_decay")
    public static void dayEdgeDecaysByDecayPerDay(GameTestHelper h) {
        ConfigOverrides.during(h, FactionConfig.DECAY_PER_DAY, 0.1);
        withCleanState(h, server -> {
            Factions.set(server, FactionState.INITIAL.withAggression(Faction.NAVY, 0.8)
                    .withAggression(Faction.PIRATES, 0.0).withTension(FactionPair.NAVY_PIRATES, 0.5));
            FactionData.get(server).setLastDay(100);
            h.assertValueEqual(Factions.observeDay(server, 101), 1L, "days decayed at the edge");
            FactionState s = Factions.state(server);
            h.assertTrue(Math.abs(s.aggression(Faction.NAVY) - 0.7) < EPS, "navy aggression 0.8 -> 0.7, was " + s.aggression(Faction.NAVY));
            h.assertTrue(Math.abs(s.aggression(Faction.PIRATES) - 0.1) < EPS, "pirate aggression 0 -> 0.1");
            h.assertTrue(Math.abs(s.aggression(Faction.MERCHANTS) - 0.3) < EPS, "merchant aggression stays at rest");
            h.assertTrue(Math.abs(s.tension(FactionPair.NAVY_PIRATES) - 0.4) < EPS, "tension 0.5 -> 0.4");
            h.assertValueEqual(Factions.observeDay(server, 101), 0L, "the same day decays only once");
            h.assertValueEqual(Factions.state(server), s, "state unchanged within the day");
        });
    }

    /** With {@code enabled=false} reports and the day edge change nothing. */
    @ModGameTest(batch = "pirates_n_ships_config_worldsim_faction_enabled")
    public static void disabledIgnoresEvents(GameTestHelper h) {
        ConfigOverrides.during(h, FactionConfig.ENABLED, false);
        withCleanState(h, server -> {
            h.assertFalse(Factions.report(server, FactionEvent.PIRATE_KILLED_BY_NAVY), "report ignored");
            h.assertFalse(Factions.reportDeed(server, FactionEvent.MERCHANT_PLUNDERED), "deed ignored");
            FactionData.get(server).setLastDay(100);
            h.assertValueEqual(Factions.observeDay(server, 103), 0L, "no decay while disabled");
            h.assertValueEqual(Factions.state(server), FactionState.INITIAL, "state unchanged");
        });
    }

    /** {@code /pirates world factions} prints one line per faction. */
    @ModGameTest(batch = "pirates_n_ships_worldsim_faction_command")
    public static void commandPrintsThreeLines(GameTestHelper h) {
        withCleanState(h, server -> {
            List<Component> out = command(h, "pirates world factions");
            h.assertValueEqual(out.size(), 3, "lines printed: " + out);
            for (Component c : out) {
                h.assertTrue(c.getContents() instanceof TranslatableContents t && t.getKey().equals(FactionCommands.KEY_LINE),
                        "not a faction line: " + c);
            }
        });
    }

    /** Runs a command as an operator and returns its feedback. */
    private static List<Component> command(GameTestHelper h, String command) {
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
}
