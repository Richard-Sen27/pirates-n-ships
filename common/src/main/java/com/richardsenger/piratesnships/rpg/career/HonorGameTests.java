package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.rpg.deeds.Deed;
import com.richardsenger.piratesnships.rpg.deeds.DeedContext;
import com.richardsenger.piratesnships.rpg.deeds.Deeds;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Honor and status in a real server (HON1, docs/design.md §15): the title team follows promotion, infamy, the letter,
 * resignation and desertion; a player on another team is left alone; the {@code careers.name_prefix} toggle takes
 * players off at login; naming a ship at the helm puts the owner's title in front, renaming after a promotion swaps
 * it, a non-owner's typed title is removed; {@code careers.title_on_ship} off stores the typed name. Each test uses
 * its own player name, since the scoreboard is shared by all tests, and leaves the title teams at the end.
 */
public final class HonorGameTests {

    private static final String BATCH = "pirates_n_ships_honor_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_honor_";

    private HonorGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HonorGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    /** A real server player with a mock connection and a name of its own, not in the level or the player list. */
    private static ServerPlayer serverPlayer(GameTestHelper helper) {
        String name = "hon_" + UUID.randomUUID().toString().substring(0, 8);
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        p.getInventory().clearContent();
        return p;
    }

    private static ServerPlayer enlisted(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        Reputation.set(p, Faction.NAVY, CareerConfig.thresholds().step(NavyRank.MIDSHIPMAN).minNavyRep(), "test");
        h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist");
        return p;
    }

    private static PlayerTeam teamOf(ServerPlayer p) {
        return p.getScoreboard().getPlayersTeam(p.getScoreboardName());
    }

    /** Asserts the player is on {@code title}'s team and that team's prefix reads "{@code <title> }". */
    private static void assertTitle(GameTestHelper h, ServerPlayer p, CareerTitles.Title title, String when) {
        PlayerTeam team = teamOf(p);
        h.assertTrue(team != null, when + ": on no team");
        h.assertValueEqual(team.getName(), CareerTeams.teamName(title), when + ": team");
        h.assertValueEqual(team.getPlayerPrefix().getString(), title.text() + " ", when + ": prefix");
        h.assertValueEqual(PlayerTeam.formatNameForTeam(team, p.getName()).getString(), title.text() + " " + p.getScoreboardName(),
                when + ": name as chat and the name tag show it");
    }

    private static void assertNoTeam(GameTestHelper h, ServerPlayer p, String when) {
        PlayerTeam team = teamOf(p);
        h.assertTrue(team == null, when + ": still on team " + (team == null ? "" : team.getName()));
    }

    /** Takes the test player off whatever team it is on (the scoreboard outlives the test). */
    private static void leave(ServerPlayer p) {
        Scoreboard s = p.getScoreboard();
        PlayerTeam team = s.getPlayersTeam(p.getScoreboardName());
        if (team != null) s.removePlayerFromTeam(p.getScoreboardName(), team);
    }

    // ------------------------------------------------------------------ the name prefix

    /** Enlisting puts a midshipman on the "Mid." team; promotion to lieutenant moves them to "Lt.". */
    @ModGameTest(batch = BATCH + "promotion")
    public static void promotionMovesThePlayerToTheNewTitle(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        assertTitle(h, p, CareerTitles.Title.MIDSHIPMAN, "after enlisting");
        CareerThresholds.NavyStep lt = CareerConfig.thresholds().step(NavyRank.LIEUTENANT);
        Reputation.set(p, Faction.NAVY, lt.minNavyRep(), "test");
        for (int i = 0; i < lt.quests(); i++) Careers.recordQuest(p, Faction.NAVY);
        for (int i = 0; i < lt.piratesDefeated(); i++) Deeds.record(p, Deed.KILL_PIRATE, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.LIEUTENANT, "rank");
        assertTitle(h, p, CareerTitles.Title.LIEUTENANT, "after promotion");
        h.assertValueEqual(CareerTeams.refresh(p), CareerTeams.Outcome.UNCHANGED, "a second refresh");
        Careers.setNavy(p, NavyRank.ADMIRAL);
        assertTitle(h, p, CareerTitles.Title.ADMIRAL, "after the operator's promotion");
        leave(p);
        h.succeed();
    }

    /** Resigning takes the player off the title teams. */
    @ModGameTest(batch = BATCH + "resign")
    public static void resigningClearsThePrefix(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        assertTitle(h, p, CareerTitles.Title.MIDSHIPMAN, "after enlisting");
        h.assertTrue(Careers.resign(p), "resign");
        assertNoTeam(h, p, "after resigning");
        leave(p);
        h.succeed();
    }

    /** Desertion (attacking the navy in service) takes the player off the title teams. */
    @ModGameTest(batch = BATCH + "desertion")
    public static void desertionClearsThePrefix(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        assertTitle(h, p, CareerTitles.Title.CAPTAIN, "as captain");
        Deeds.record(p, Deed.ATTACK_NAVY, DeedContext.NONE);
        h.assertValueEqual(Careers.navyRank(p), NavyRank.NONE, "rank after desertion");
        assertNoTeam(h, p, "after desertion");
        leave(p);
        h.succeed();
    }

    /** Infamy gives the pirate titles, a letter of marque the privateer's; a void letter none. */
    @ModGameTest(batch = BATCH + "pirates")
    public static void infamyAndTheLetterGiveTheirTitles(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        h.assertValueEqual(CareerTeams.refresh(p), CareerTeams.Outcome.UNCHANGED, "a deckhand");
        assertNoTeam(h, p, "a deckhand");
        Careers.forceLetter(p);
        assertTitle(h, p, CareerTitles.Title.PRIVATEER, "with a letter");
        Careers.voidLetter(p);
        assertNoTeam(h, p, "after the letter is void");
        Careers.setInfamy(p, InfamyRank.BUCCANEER);
        assertTitle(h, p, CareerTitles.Title.BUCCANEER, "buccaneer");
        Careers.setInfamy(p, InfamyRank.DREAD_CAPTAIN);
        assertTitle(h, p, CareerTitles.Title.DREAD_PIRATE, "dread captain");
        Careers.setInfamy(p, InfamyRank.PIRATE_LORD);
        assertTitle(h, p, CareerTitles.Title.PIRATE_LORD, "pirate lord");
        leave(p);
        h.succeed();
    }

    /** A player already on another team keeps it through enlisting and promotion. */
    @ModGameTest(batch = BATCH + "other_team")
    public static void aPlayerOnAnotherTeamIsLeftAlone(GameTestHelper h) {
        ServerPlayer p = serverPlayer(h);
        Scoreboard s = p.getScoreboard();
        String otherName = "hon_other_" + p.getScoreboardName();
        PlayerTeam other = s.addPlayerTeam(otherName);
        try {
            s.addPlayerToTeam(p.getScoreboardName(), other);
            Reputation.set(p, Faction.NAVY, CareerConfig.thresholds().step(NavyRank.MIDSHIPMAN).minNavyRep(), "test");
            h.assertValueEqual(Careers.enlist(p), CareerRules.EnlistVerdict.OK, "enlist");
            Careers.setNavy(p, NavyRank.CAPTAIN);
            h.assertValueEqual(teamOf(p), other, "team after enlisting and promotion");
            h.assertValueEqual(CareerTeams.refresh(p), CareerTeams.Outcome.OTHER_TEAM, "refresh");
            h.assertTrue(other.getPlayerPrefix().getString().isEmpty(), "the other team's prefix was changed");
            // leaving the other team: the next change of career brings the title
            s.removePlayerFromTeam(p.getScoreboardName(), other);
            Careers.setNavy(p, NavyRank.COMMODORE);
            assertTitle(h, p, CareerTitles.Title.COMMODORE, "after leaving the other team");
        } finally {
            leave(p);
            s.removePlayerTeam(other);
        }
        h.succeed();
    }

    /** With {@code careers.name_prefix} off the login refresh takes the player off, and nothing puts them back on. */
    @ModGameTest(batch = CONFIG_BATCH + "name_prefix")
    public static void prefixToggleOffClearsAtLogin(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        assertTitle(h, p, CareerTitles.Title.MIDSHIPMAN, "toggle on");
        ConfigOverrides.during(h, CareerConfig.NAME_PREFIX, false);
        // CareerModule's PLAYER_LOGIN listener calls exactly this
        h.assertValueEqual(CareerTeams.refresh(p), CareerTeams.Outcome.LEFT, "login refresh with the toggle off");
        assertNoTeam(h, p, "toggle off, after login");
        Careers.setNavy(p, NavyRank.CAPTAIN);
        assertNoTeam(h, p, "toggle off, after promotion");
        leave(p);
        h.succeed();
    }

    /** With {@code careers.enabled} off nobody joins a title team either. */
    @ModGameTest(batch = CONFIG_BATCH + "honor_enabled")
    public static void careersOffShowNoTitle(GameTestHelper h) {
        ConfigOverrides.during(h, CareerConfig.ENABLED, false);
        ServerPlayer p = serverPlayer(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        assertNoTeam(h, p, "careers off");
        h.assertValueEqual(CareerShipTitles.forNaming(p, Optional.empty(), "Capt. Black Gull"), "Capt. Black Gull",
                "careers off: the typed name is kept");
        leave(p);
        h.succeed();
    }

    // ------------------------------------------------------------------ the title on the ship

    private static final BlockPos HELM = new BlockPos(4, 3, 4);

    private static ShipBody assembleHull(GameTestHelper h) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
        }
        h.setBlock(HELM, AssemblyContent.HELM.get());
        AssemblyResult r = ShipTestCleanup.assemble(h, HELM);
        h.assertTrue(r.success() && r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        h.assertTrue(ship != null, "no sub-level for " + r.shipId());
        return ship;
    }

    private static BlockPos helmOf(GameTestHelper h, ShipBody ship) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(AssemblyContent.HELM.get())) return p;
        }
        h.fail("helm not in the plot");
        return null;
    }

    /** Uses a name tag reading {@code name} on the ship's helm, the way a player names a ship. */
    private static void nameAtHelm(GameTestHelper h, ServerPlayer p, ShipBody ship, String name) {
        BlockPos helm = helmOf(h, ship);
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        p.setItemInHand(InteractionHand.MAIN_HAND, tag);
        ship.level().getBlockState(helm).useItemOn(tag, ship.level(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helm), Direction.UP, helm, false));
    }

    private static String storedName(GameTestHelper h, ShipBody ship) {
        return ShipRegistry.get(h.getLevel().getServer()).find(ship.id()).map(ShipData::name).orElse("<no ship>");
    }

    private static void own(GameTestHelper h, ShipBody ship, ServerPlayer owner) {
        ShipRegistry registry = ShipRegistry.get(h.getLevel().getServer());
        registry.put(registry.find(ship.id()).orElseThrow().withOwner(Optional.of(owner.getUUID())));
    }

    /**
     * The owner, a captain, names the ship "Black Gull" at the helm and it is "Capt. Black Gull"; promoted to
     * commodore and renaming with the old name, it becomes "Cdre. Black Gull"; another player's typed title is removed.
     */
    @ModGameTest(batch = BATCH + "ship", template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void namingAShipPutsTheOwnersTitleInFront(GameTestHelper h) {
        ShipBody ship = assembleHull(h);
        ServerPlayer captain = enlisted(h);
        Careers.setNavy(captain, NavyRank.CAPTAIN);
        own(h, ship, captain);

        nameAtHelm(h, captain, ship, "Black Gull");
        h.assertValueEqual(storedName(h, ship), "Capt. Black Gull", "name after the captain named it");

        Careers.setNavy(captain, NavyRank.COMMODORE);
        nameAtHelm(h, captain, ship, "Capt. Black Gull");
        h.assertValueEqual(storedName(h, ship), "Cdre. Black Gull", "name after renaming as commodore");

        ServerPlayer stranger = enlisted(h);
        Careers.setNavy(stranger, NavyRank.ADMIRAL);
        nameAtHelm(h, stranger, ship, "Lt. Sea Wolf");
        h.assertValueEqual(storedName(h, ship), "Sea Wolf", "a non-owner's name: no title, the typed one removed");

        leave(captain);
        leave(stranger);
        h.succeed();
    }

    /** With {@code careers.title_on_ship} off the typed name is stored as it is. */
    @ModGameTest(batch = CONFIG_BATCH + "title_on_ship")
    public static void titleOnShipOffKeepsTheTypedName(GameTestHelper h) {
        ServerPlayer p = enlisted(h);
        Careers.setNavy(p, NavyRank.CAPTAIN);
        h.assertValueEqual(CareerShipTitles.forNaming(p, Optional.empty(), "Black Gull"), "Capt. Black Gull", "toggle on");
        h.assertValueEqual(CareerShipTitles.forNaming(p, Optional.of(UUID.randomUUID()), "Lt. Black Gull"), "Black Gull",
                "toggle on, someone else's ship");
        ConfigOverrides.during(h, CareerConfig.TITLE_ON_SHIP, false);
        h.assertValueEqual(CareerShipTitles.forNaming(p, Optional.empty(), "Black Gull"), "Black Gull", "toggle off");
        h.assertValueEqual(CareerShipTitles.forNaming(p, Optional.empty(), "Lt. Black Gull"), "Lt. Black Gull", "toggle off, typed title");
        leave(p);
        h.succeed();
    }
}
