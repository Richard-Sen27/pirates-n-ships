package com.richardsenger.piratesnships.ship.assembly;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.crew.npc.CrewStations;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.helm.HelmBlockEntity;
import com.richardsenger.piratesnships.sailing.helm.HelmService;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.station.StationContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * HL1 (docs/design.md §4.1): breaking the helm of an assembled ship keeps the ship (same body, same id, record and
 * stations); a helm placed anywhere on it becomes its steering helm, and a second helm does nothing while the first
 * stands. The deck is a 6×6 plank plate on stone with the helm amidships, a sail winch and a capstan.
 *
 * <p>Every test has its own batch: helm sessions and splits must not meet other tests' ships.
 */
public final class HelmReplaceGameTests {

    private static final String BATCH = "pirates_n_ships_helm_replace_";
    private static final BlockPos HELM = new BlockPos(5, 3, 5);
    private static final BlockPos OTHER = new BlockPos(3, 3, 6);
    private static final BlockPos WINCH = new BlockPos(4, 3, 4);
    private static final BlockPos CAPSTAN = new BlockPos(6, 3, 6);
    private static final String NAME = "Grey Heron";

    private HelmReplaceGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(HelmReplaceGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /** A player that keeps the messages it is shown (the GameTest mock player drops them). */
    static final class Captain extends Player {
        final List<Component> messages = new ArrayList<>();

        Captain(ServerLevel level) {
            super(level, BlockPos.ZERO, 0f, new GameProfile(UUID.randomUUID(), "hl1-captain"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        String last() {
            return messages.isEmpty() ? "" : key(messages.get(messages.size() - 1));
        }
    }

    /** The deck ship and the plot positions of its helm, the second helm spot, the winch and the capstan. */
    record Deck(ShipBody ship, BlockPos helm, BlockPos other, BlockPos winch, BlockPos capstan) { }

    private static Deck deck(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 7; x++) {
            for (int z = 2; z <= 7; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        h.setBlock(WINCH, SailingBlocks.SAIL_WINCH.get());
        h.setBlock(CAPSTAN, SailingBlocks.CAPSTAN.get());
        h.setBlock(HELM, helmState());
        ShipBody ship = assembleNamed(h, HELM);
        return new Deck(ship, plotOf(h, ship, HELM), plotOf(h, ship, OTHER), plotOf(h, ship, WINCH), plotOf(h, ship, CAPSTAN));
    }

    private static ShipBody assembleNamed(GameTestHelper h, BlockPos helm) {
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        if (r.shipId() == null) {
            throw new AssertionError("assembly failed: " + r);
        }
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        if (ship == null) {
            throw new AssertionError("no ship after assembly");
        }
        ShipAssembler.name(ship, NAME);
        return ship;
    }

    private static BlockState helmState() {
        return AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
    }

    private static BlockPos plotOf(GameTestHelper h, ShipBody ship, BlockPos rel) {
        return BlockPos.containing(ship.toPlot(Vec3.atCenterOf(h.absolutePos(rel))));
    }

    static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static List<ShipBody> shipsHere(GameTestHelper h) {
        AABB area = h.getBounds().inflate(4);
        List<ShipBody> out = new ArrayList<>();
        for (ShipBody s : SableShips.all(h.getLevel())) {
            if (!s.isRemoved() && s.worldBounds().intersects(area)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Tracks every ship that appears in the test area for removal at the end (split pieces included). */
    private static void trackPieces(GameTestHelper h) {
        Set<UUID> seen = new HashSet<>();
        h.onEachTick(() -> {
            for (ShipBody s : shipsHere(h)) {
                if (seen.add(s.id())) {
                    ShipTestCleanup.track(h, s.id());
                }
            }
        });
    }

    private static void use(GameTestHelper h, Player player, BlockPos plotPos) {
        ServerLevel level = h.getLevel();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(plotPos), Direction.UP, plotPos, false);
        level.getBlockState(plotPos).useWithoutItem(level, player, hit);
    }

    /** A player places a helm at {@code plotPos}, on top of the deck block below it (the block item's whole path). */
    private static void placeHelm(Player player, BlockPos plotPos) {
        ItemStack stack = new ItemStack(AssemblyContent.HELM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = plotPos.below();
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** The ship is still the same registered ship, with working stations. */
    private static void assertStillTheShip(GameTestHelper h, Deck d, String when) {
        ServerLevel level = h.getLevel();
        ShipBody now = SableShips.byId(level, d.ship().id());
        h.assertTrue(now != null && !now.isRemoved(), when + ": the ship's body is gone");
        ShipData data = ShipRegistry.get(level.getServer()).find(d.ship().id()).orElse(null);
        h.assertTrue(data != null && NAME.equals(data.name()), when + ": the ship lost its record: " + data);
        h.assertTrue(ShipHelm.isOurShip(now), when + ": the ship pointer is gone");
        ShipBody winchShip = SableShips.containing(level, d.winch());
        h.assertTrue(winchShip != null && d.ship().id().equals(winchShip.id()), when + ": the winch left the ship");
        String winch = key(SailWinchBlock.use(level, d.winch()));
        h.assertTrue(!winch.equals(SailWinchBlock.KEY_NOT_ON_SHIP), when + ": the winch says it is not on a ship");
        String capstan = key(ShipControls.useCapstan(level, d.capstan()));
        h.assertTrue(!capstan.equals(ShipControls.KEY_CAPSTAN_NOT_ON_SHIP), when + ": the capstan says it is not on a ship");
    }

    /** {@code player} takes the wheel at {@code helm} and turns it: the ship's runtime follows. */
    private static void assertSteers(GameTestHelper h, UUID ship, Captain player, BlockPos helm) {
        ServerLevel level = h.getLevel();
        h.assertTrue(level.getBlockEntity(helm) instanceof HelmBlockEntity be && be.wheel() == 0f,
                "the helm's wheel is not at midships");
        use(h, player, helm);
        h.assertTrue(player.last().equals(HelmService.KEY_HOLDING), "using the helm did not take the wheel: " + player.last());
        h.assertTrue(helm.equals(HelmService.session(player)), "no steering session at the helm");
        h.assertTrue(HelmService.turn(player, helm, 5.0), "the wheel did not turn");
        SailingRuntime rt = SailingRuntimes.get(level, ship);
        h.assertTrue(rt != null && rt.helm() != null, "the sailing runtime has no helm (no rudder)");
        h.assertTrue(rt.wheelAngle() > 0.0, "the runtime's wheel did not follow: " + rt.wheelAngle());
        HelmService.release(player, helm);
    }

    /** Sneak-use with an empty hand at {@code helm} disassembles the ship. */
    private static void assertDisassembles(GameTestHelper h, Deck d, Captain player, BlockPos helm, BlockPos helmRel) {
        player.setShiftKeyDown(true);
        use(h, player, helm);
        player.setShiftKeyDown(false);
        h.assertTrue(player.last().equals(AssemblyResult.Outcome.DISASSEMBLED.key()), "sneak-use did not disassemble: " + player.last());
        h.assertTrue(SableShips.byId(h.getLevel(), d.ship().id()) == null, "the ship's body is still there");
        h.assertTrue(ShipRegistry.get(h.getLevel().getServer()).find(d.ship().id()).isEmpty(), "the record is still there");
        h.assertTrue(h.getBlockState(helmRel).getBlock() instanceof HelmBlock, "the helm is not back in the world at " + helmRel);
    }

    // ------------------------------------------------------------------ tests

    /**
     * The helm is broken (with drops) and a helm is placed again where it stood: the ship never stops being the ship,
     * the new helm steers with a fresh wheel at midships and disassembles the ship.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "same_place")
    public static void helmBrokenAndPlacedAgainAtTheSamePlace(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        Captain captain = new Captain(level);
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            h.assertTrue(d.helm().equals(ShipHelm.steering(d.ship())), "assembly did not remember the steering helm");
            if (level.getBlockEntity(d.helm()) instanceof HelmBlockEntity be) {
                be.setWheel(40.0); // the old wheel was turned: the new one must not inherit it
            }
            level.destroyBlock(d.helm(), true);
        });
        h.runAfterDelay(25, () -> {
            assertStillTheShip(h, d, "after the helm broke");
            h.assertTrue(shipsHere(h).size() == 1, "the ship split: " + shipsHere(h).size() + " bodies");
            h.assertTrue(ShipHelm.steering(d.ship()) == null, "a broken helm still steers");
            SailingRuntime rt = SailingRuntimes.get(level, d.ship().id());
            h.assertTrue(rt != null && rt.helm() == null && rt.wheelAngle() == 0.0, "the helmless ship still has a rudder");
            h.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(6),
                    e -> e.getItem().is(AssemblyContent.HELM.get().asItem())).isEmpty(), "the broken helm dropped no item");
            level.setBlock(d.helm(), helmState(), 3);
        });
        h.runAfterDelay(27, () -> {
            h.assertTrue(d.helm().equals(ShipHelm.steering(d.ship())), "the helm placed again does not steer");
            assertStillTheShip(h, d, "after the helm was placed again");
            assertSteers(h, d.ship().id(), captain, d.helm());
        });
        h.runAfterDelay(60, () -> {
            assertDisassembles(h, d, captain, d.helm(), HELM);
            h.succeed();
        });
    }

    /** A player breaks the helm and places one at another spot of the deck: it attaches and steers from there. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "other_place")
    public static void helmPlacedElsewhereOnTheDeckTakesOver(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        Captain captain = new Captain(level);
        trackPieces(h);
        h.runAfterDelay(2, () -> level.destroyBlock(d.helm(), true, captain));
        h.runAfterDelay(20, () -> {
            assertStillTheShip(h, d, "after the helm broke");
            placeHelm(captain, d.other());
            h.assertTrue(level.getBlockState(d.other()).getBlock() instanceof HelmBlock, "the helm was not placed");
            h.assertTrue(captain.last().equals(ShipHelm.KEY_ATTACHED), "placing told " + captain.last());
            h.assertTrue(d.other().equals(ShipHelm.steering(d.ship())), "the new helm does not steer");
            assertStillTheShip(h, d, "after the new helm was placed");
            assertSteers(h, d.ship().id(), captain, d.other());
        });
        h.runAfterDelay(50, () -> {
            assertDisassembles(h, d, captain, d.other(), OTHER);
            h.assertTrue(h.getBlockState(HELM).isAir(), "something stands where the broken helm was");
            h.succeed();
        });
    }

    /**
     * Two helms: the one the ship was assembled with steers; a second one placed later neither steers nor
     * disassembles. When the first breaks, the second takes over on its next use.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "two_helms")
    public static void aSecondHelmWaitsUntilTheFirstIsGone(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        Captain captain = new Captain(level);
        trackPieces(h);
        h.runAfterDelay(2, () -> {
            placeHelm(captain, d.other());
            h.assertTrue(captain.last().equals(ShipHelm.KEY_SECOND), "placing a second helm told " + captain.last());
            use(h, captain, d.other());
            h.assertTrue(captain.last().equals(ShipHelm.KEY_SECOND), "using the second helm told " + captain.last());
            h.assertTrue(HelmService.session(captain) == null, "the second helm took the wheel");
            captain.setShiftKeyDown(true);
            use(h, captain, d.other());
            captain.setShiftKeyDown(false);
            h.assertTrue(SableShips.byId(level, d.ship().id()) != null, "the second helm disassembled the ship");
            h.assertTrue(d.helm().equals(ShipHelm.steering(d.ship())), "the second helm took over the steering");
            assertSteers(h, d.ship().id(), captain, d.helm());
            level.destroyBlock(d.helm(), true);
        });
        h.runAfterDelay(20, () -> {
            assertStillTheShip(h, d, "after the first helm broke");
            h.assertTrue(ShipHelm.steering(d.ship()) == null, "the ship should be helmless until the second helm is used");
            use(h, captain, d.other());
            h.assertTrue(captain.last().equals(HelmService.KEY_HOLDING), "the second helm did not take over: " + captain.last());
            h.assertTrue(d.other().equals(ShipHelm.steering(d.ship())), "the second helm is not the steering helm");
            HelmService.release(captain, d.other());
            h.succeed();
        });
    }

    /** Crew at the ship's stations stay at them when the helm breaks and when a new one is placed. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "crew")
    public static void crewKeepTheirStationsWhenTheHelmBreaks(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        trackPieces(h);
        CrewMember crew = h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(4, 3, 3));
        h.assertTrue(CrewStations.assign(level, crew, d.winch()) == CrewStations.AssignResult.ASSIGNED, "could not assign the crew");
        boolean[] seated = new boolean[1];
        h.runAfterDelay(20, () -> {
            seated[0] = crew.isAtStation();
            level.destroyBlock(d.helm(), true);
        });
        h.runAfterDelay(45, () -> {
            h.assertTrue(crew.assignment() != null && crew.assignment().ship().equals(d.ship().id())
                    && crew.assignment().pos().equals(d.winch()), "the crew lost its station when the helm broke: " + crew.assignment());
            h.assertTrue(!seated[0] || crew.isAtStation(), "the crew left the winch when the helm broke");
            level.setBlock(d.other(), helmState(), 3);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(crew.assignment() != null && crew.assignment().ship().equals(d.ship().id()),
                    "the crew lost its station when the new helm was placed: " + crew.assignment());
            h.assertTrue(!seated[0] || crew.isAtStation(), "the crew left the winch when the new helm was placed");
            crew.discard();
            h.succeed();
        });
    }

    /**
     * A helmless ship is saved and loaded: the record (saved data codec) and the ship pointer and sailing state (the
     * body's user data, saved by Sable) come back, the rebuilt runtimes have no helm and keep the bow, and a helm placed
     * afterwards steers.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = BATCH + "reload")
    public static void helmlessShipSurvivesSaveAndLoad(GameTestHelper h) {
        Deck d = deck(h);
        ServerLevel level = h.getLevel();
        Captain captain = new Captain(level);
        trackPieces(h);
        String[] bow = new String[1];
        h.runAfterDelay(2, () -> {
            SailingRuntimes.getOrCreate(d.ship()); // the first scan stores the bow
            bow[0] = d.ship().userData(SailingRuntimes.USER_DATA_KEY).getString("bow");
            h.assertTrue(!bow[0].isEmpty(), "the ship has no bow yet");
            level.destroyBlock(d.helm(), true);
        });
        h.runAfterDelay(20, () -> {
            // the record through the saved data's own encoding
            CompoundTag saved = ShipRegistry.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            List<ShipData> loaded = ShipData.CODEC.listOf().parse(NbtOps.INSTANCE, saved.get("ships")).getOrThrow();
            h.assertTrue(loaded.stream().anyMatch(s -> s.id().equals(d.ship().id()) && NAME.equals(s.name())),
                    "the helmless ship's record did not survive saving");
            // the runtimes are rebuilt from the body, as after an unload and load
            SailingRuntimes.onShipRemoved(level, d.ship().id(), false);
            HullRuntimes.onShipRemoved(level, d.ship().id(), false);
            ShipBody ship = SableShips.byId(level, d.ship().id());
            SailingRuntime fresh = ship == null ? null : SailingRuntimes.getOrCreate(ship);
            h.assertTrue(fresh != null, "no sailing runtime for the helmless ship after the reload");
            h.assertTrue(fresh.helm() == null, "the reloaded helmless ship has a helm");
            h.assertTrue(bow[0].equals(ship.userData(SailingRuntimes.USER_DATA_KEY).getString("bow")),
                    "the bow changed: " + ship.userData(SailingRuntimes.USER_DATA_KEY));
            assertStillTheShip(h, d, "after the reload");
            placeHelm(captain, d.helm());
            h.assertTrue(captain.last().equals(ShipHelm.KEY_ATTACHED), "placing told " + captain.last());
            assertSteers(h, d.ship().id(), captain, d.helm());
            h.succeed();
        });
    }

    /**
     * The body Sable empties: after a split that cut every block off Sable's heat-map root (here: the helm bridging two
     * plates), Sable's block count of the remaining body is too low, and the next loss of its root moves the whole rest
     * into a new body and removes the old one in the same tick, before the split is reported (docs/sable-notes.md
     * §9.0j). The record, name and line follow the new body; its stations answer and a helm placed on it steers. Before
     * HL1 the record was dropped with the old body and the new one was "not an assembled ship".
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "emptied")
    public static void theShipFollowsTheBodySableMovesItTo(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        for (int x = 8; x <= 10; x++) {
            for (int z = 4; z <= 6; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        BlockPos bridge = new BlockPos(7, 2, 5);
        h.setBlock(bridge, helmState());
        h.setBlock(new BlockPos(3, 3, 5), SailingBlocks.SAIL_WINCH.get());
        h.setBlock(new BlockPos(5, 3, 5), SailingBlocks.CAPSTAN.get());
        ShipBody ship = assembleNamed(h, bridge);
        UUID original = ship.id();
        BlockPos helm = plotOf(h, ship, bridge);
        trackPieces(h);
        // 1: the helm (Sable's root) bridges the plates: both are cut off its root, the larger stays in the body
        h.runAfterDelay(3, () -> level.destroyBlock(helm, true));
        // 2: the remaining body's new root goes too
        h.runAfterDelay(30, () -> {
            ShipBody body = SableShips.byId(level, original);
            h.assertTrue(body != null && body.plotBlocks().size() == 17, "the first split did not leave the 17-block plate: "
                    + (body == null ? null : body.plotBlocks().size()));
            BlockPos root = SableShips.heatMapRoot(body);
            h.assertTrue(root != null, "no heat-map root found");
            level.destroyBlock(root, true);
        });
        h.runAfterDelay(60, () -> {
            ShipRegistry registry = ShipRegistry.get(level.getServer());
            ShipBody keeper = null;
            for (ShipBody s : shipsHere(h)) {
                h.assertTrue(ShipHelm.isOurShip(s) && registry.find(s.id()).isPresent(),
                        "an orphan body without record or pointer: " + s.id() + " (" + s.plotBlocks().size() + " blocks)");
                if (!ShipSplits.isWreck(s)) {
                    h.assertTrue(keeper == null, "two bodies are the ship");
                    keeper = s;
                }
            }
            h.assertTrue(keeper != null, "no body is the ship any more");
            h.assertTrue(!keeper.id().equals(original), "Sable did not move the rest into a new body (the case under test)");
            h.assertTrue(registry.find(original).isEmpty(), "the emptied body's record is still there");
            h.assertTrue(NAME.equals(registry.find(keeper.id()).orElseThrow().name()), "the ship lost its name");
            h.assertTrue(ShipSplits.lineage(keeper).origin().equals(original), "the ship's line is wrong: " + ShipSplits.lineage(keeper));
            BlockPos winch = null;
            BlockPos capstan = null;
            for (BlockPos p : keeper.plotBlocks()) {
                if (level.getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())) {
                    winch = p;
                }
                if (level.getBlockState(p).is(SailingBlocks.CAPSTAN.get())) {
                    capstan = p;
                }
            }
            h.assertTrue(winch != null || capstan != null, "both stations are gone");
            if (winch != null) {
                h.assertTrue(!key(SailWinchBlock.use(level, winch)).equals(SailWinchBlock.KEY_NOT_ON_SHIP), "the winch is not on a ship");
            }
            if (capstan != null) {
                h.assertTrue(!key(ShipControls.useCapstan(level, capstan)).equals(ShipControls.KEY_CAPSTAN_NOT_ON_SHIP),
                        "the capstan is not on a ship");
            }
            // a helm on the moved ship steers it
            BlockPos spot = null;
            for (BlockPos p : keeper.plotBlocks()) {
                if (level.getBlockState(p).is(Blocks.OAK_PLANKS) && level.getBlockState(p.above()).isAir()) {
                    spot = p.above();
                    break;
                }
            }
            h.assertTrue(spot != null, "no free deck spot");
            Captain captain = new Captain(level);
            placeHelm(captain, spot);
            h.assertTrue(captain.last().equals(ShipHelm.KEY_ATTACHED), "placing told " + captain.last());
            assertSteers(h, keeper.id(), captain, spot);
            h.succeed();
        });
    }
}
