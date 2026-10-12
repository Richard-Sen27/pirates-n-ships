package com.richardsenger.piratesnships.mob.monsters;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.hammock.HammockRef;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.mob.MobConfig;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.mob.entity.NavySoldier;
import com.richardsenger.piratesnships.mob.entity.Pirate;
import com.richardsenger.piratesnships.mob.entity.Sailor;
import com.richardsenger.piratesnships.mob.harbor.HarborMaster;
import com.richardsenger.piratesnships.mob.harbor.HarborMasterConfig;
import com.richardsenger.piratesnships.mob.harbor.HarborMasterRegistry;
import com.richardsenger.piratesnships.mob.harbor.HarborMasters;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import com.richardsenger.piratesnships.trade.market.Climate;
import com.richardsenger.piratesnships.trade.market.GoodRole;
import com.richardsenger.piratesnships.trade.market.PortKind;
import com.richardsenger.piratesnships.trade.market.PortProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Monsters and the mod's people in a real server (MOB3, {@link MonsterRules}). Every test whose monsters have AI runs
 * in its own batch, so a monster never sees the people of another test; tests that change config use
 * {@code pirates_n_ships_config_*} batches. Husks stand in for zombies where daylight would set a zombie alight; the
 * zombie of the first test wears a helmet for the same reason.
 */
public final class MonstersGameTests {

    private static final String BATCH = "pirates_n_ships_monsters_";
    private static final String CONFIG_BATCH = "pirates_n_ships_config_monsters_";
    /** Ticks a fresh test ship needs to rise and settle before anyone boards it (as in {@code MobGameTests}). */
    private static final int SETTLE_TICKS = 60;

    private MonstersGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(MonstersGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void floor(GameTestHelper h, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A zombie with a helmet (no sunburn) at {@code (x, 1, z)}. */
    private static Zombie zombie(GameTestHelper h, int x, int z) {
        Zombie z0 = h.spawn(EntityType.ZOMBIE, new BlockPos(x, 1, z));
        z0.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return z0;
    }

    private static Husk husk(GameTestHelper h, int x, int z) {
        return h.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
    }

    /** A husk at world position {@code w} that hardly moves (it stays on its pier or deck). */
    private static Husk huskAt(GameTestHelper h, Vec3 w) {
        Husk b = EntityType.HUSK.create(h.getLevel());
        if (b == null) throw new AssertionError("no husk");
        b.moveTo(w.x, w.y, w.z, 0, 0);
        b.setPersistenceRequired();
        b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 9));
        h.getLevel().addFreshEntity(b);
        return b;
    }

    private static Sailor sailor(GameTestHelper h, int x, int z) {
        return h.spawn(MobContent.SAILOR.get(), new BlockPos(x, 1, z));
    }

    private static CrewMember crew(GameTestHelper h, int x, int z) {
        return h.spawn(StationContent.CREW_MEMBER.get(), new BlockPos(x, 1, z));
    }

    // ------------------------------------------------------------------ monsters hunt people

    /** A zombie next to a sailor hunts it, as it hunts villagers. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120, batch = BATCH + "zombie_sailor")
    public static void aZombieHuntsASailor(GameTestHelper h) {
        floor(h, 9);
        Sailor sailor = sailor(h, 2, 4);
        Zombie zombie = zombie(h, 6, 4);
        h.succeedWhen(() -> h.assertTrue(zombie.getTarget() == sailor, "the zombie did not target the sailor: " + zombie.getTarget()));
    }

    /** {@code mobs.monsters_attack_npcs} off: the zombie leaves the sailor alone (the behaviour before MOB3). */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 120, batch = CONFIG_BATCH + "off")
    public static void withTheToggleOffAZombieIgnoresASailor(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.MONSTERS_ATTACK_NPCS, false);
        floor(h, 9);
        Sailor sailor = sailor(h, 2, 4);
        CrewMember crew = crew(h, 2, 6);
        Zombie zombie = zombie(h, 6, 4);
        h.onEachTick(() -> {
            if (zombie.getTarget() == sailor || zombie.getTarget() == crew) h.fail("the zombie targeted " + zombie.getTarget());
        });
        h.runAfterDelay(80, h::succeed);
    }

    /** Pillagers (an illager) and drowned hunt a crew member too; skeletons, which leave villagers alone, do not. */
    @ModGameTest(template = GameTestTemplates.EMPTY_3, batch = BATCH + "who")
    public static void theVillagerHuntersAreArmed(GameTestHelper h) {
        Mob pillager = h.spawnWithNoFreeWill(EntityType.PILLAGER, 0, 1, 0);
        Mob drowned = h.spawnWithNoFreeWill(EntityType.DROWNED, 1, 1, 0);
        Mob skeleton = h.spawnWithNoFreeWill(EntityType.SKELETON, 2, 1, 0);
        h.assertTrue(pillager.getType().is(MonsterTargets.HUNTS_NPCS) && drowned.getType().is(MonsterTargets.HUNTS_NPCS),
                "pillager and drowned are in #hunts_npcs");
        h.assertFalse(skeleton.getType().is(MonsterTargets.HUNTS_NPCS), "the skeleton is not");
        h.assertFalse(EntityType.ZOMBIFIED_PIGLIN.is(MonsterTargets.HUNTS_NPCS), "the zombified piglin is not");
        h.assertFalse(MonsterTargets.arm((net.minecraft.world.entity.PathfinderMob) pillager), "armed once on joining, not twice");
        h.assertTrue(MonsterTargets.arm((net.minecraft.world.entity.PathfinderMob) skeleton), "a skeleton was not armed");
        h.succeed();
    }

    // ------------------------------------------------------------------ the crew answers

    /** An armed crew member fights the husk that hunts it, and hurts it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300, batch = BATCH + "armed")
    public static void anArmedCrewMemberFightsBack(GameTestHelper h) {
        floor(h, 9);
        CrewMember crew = crew(h, 2, 4);
        crew.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Husk husk = husk(h, 6, 4);
        boolean[] targeted = {false};
        h.onEachTick(() -> targeted[0] |= crew.getTarget() == husk);
        h.succeedWhen(() -> {
            h.assertTrue(targeted[0], "the crew member never took on the husk");
            h.assertTrue(!husk.isAlive() || husk.getHealth() < husk.getMaxHealth(), "the husk took no damage");
            h.assertTrue(crew.isAlive(), "the crew member died");
        });
    }

    /** An unarmed crew member runs from the husk that hunts it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "unarmed")
    public static void anUnarmedCrewMemberRuns(GameTestHelper h) {
        floor(h, 24);
        CrewMember crew = crew(h, 10, 12);
        Husk husk = husk(h, 14, 12);
        husk.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 3));
        h.succeedWhen(() -> {
            h.assertTrue(husk.getTarget() == crew, "the husk does not hunt the crew member");
            h.assertTrue(crew.getTarget() == null, "the unarmed crew member picked a fight");
            h.assertTrue(crew.distanceTo(husk) > 8.0, "the crew member did not run: " + crew.distanceTo(husk));
        });
    }

    /** A monster's blow gets a sleeper out of its hammock. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "wake")
    public static void aBlowFromAMonsterWakesASleeper(GameTestHelper h) {
        floor(h, 9);
        CrewMember crew = crew(h, 2, 4);
        Mob husk = h.spawnWithNoFreeWill(EntityType.HUSK, 6, 1, 4);
        crew.setRest(new HammockRef(UUID.randomUUID(), BlockPos.ZERO));
        crew.hurt(h.getLevel().damageSources().mobAttack(husk), 1.0f);
        h.assertTrue(crew.rest() == null, "the sleeper slept on");
        h.succeed();
    }

    /** Pirates and navy are no prey, but fight back against a monster that hits them. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH + "fighters")
    public static void piratesAndNavyFightBackAgainstMonsters(GameTestHelper h) {
        floor(h, 9);
        Pirate pirate = h.spawn(MobContent.PIRATE.get(), new BlockPos(2, 1, 2));
        NavySoldier soldier = h.spawn(MobContent.NAVY_SOLDIER.get(), new BlockPos(2, 1, 6));
        Mob husk = h.spawnWithNoFreeWill(EntityType.HUSK, 6, 1, 4);
        h.assertFalse(MonsterTargets.hunts(husk, pirate) || MonsterTargets.hunts(husk, soldier), "pirates and navy are no prey");
        pirate.hurt(h.getLevel().damageSources().mobAttack(husk), 1.0f);
        soldier.hurt(h.getLevel().damageSources().mobAttack(husk), 1.0f);
        h.assertTrue(pirate.getTarget() == husk, "the pirate does not fight back");
        h.assertTrue(soldier.getTarget() == husk, "the soldier does not fight back");
        h.succeed();
    }

    // ------------------------------------------------------------------ aboard a ship

    /**
     * A husk on a pier beside a ship leaves the sailor on deck alone, on every tick; a husk aboard the same ship hunts
     * it. Basin as in {@code MobGameTests#mobsOnADeckMoveWithTheShip}, with a stone pier east of the hull, beyond the
     * sailor's flee range (he must not run off the deck). Aboard is decided from the plot under the feet every tick
     * ({@link MonsterTargets#shipOf}), never from Sable's contact tracking alone, which drops for a tick now and then.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 360, batch = BATCH + "aboard")
    public static void onlyAMonsterAboardHuntsTheDeck(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 23 || z == 0 || z == 23;
                boolean pier = x >= 19 && x <= 21 && z >= 9 && z <= 13;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall || pier ? Blocks.STONE : y <= 7 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        int x0 = 6, z0 = 9;
        for (int x = x0; x <= x0 + 4; x++) {
            for (int z = z0; z <= z0 + 4; z++) {
                for (int y = 5; y <= 8; y++) {
                    boolean shell = y == 5 || y == 8 || x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.OAK_PLANKS : Blocks.AIR);
                }
            }
        }
        BlockPos helm = new BlockPos(x0 + 2, 9, z0);
        h.setBlock(helm, AssemblyContent.HELM.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        AssemblyResult r = ShipTestCleanup.assemble(h, helm);
        h.assertTrue(r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(h.getLevel(), r.shipId());
        h.assertTrue(ship != null, "no ship after assembly");
        BlockPos helmPlot = ship.plotBlocks().stream().filter(p -> h.getLevel().getBlockState(p).is(AssemblyContent.HELM.get()))
                .findFirst().orElseThrow(() -> new AssertionError("no helm on the ship"));
        Sailor[] sailor = new Sailor[1];
        Husk[] quay = new Husk[1];
        Husk[] boarder = new Husk[1];
        h.runAfterDelay(SETTLE_TICKS, () -> {
            Sailor s = MobContent.SAILOR.get().create(h.getLevel());
            Vec3 w = ship.toWorld(Vec3.atBottomCenterOf(helmPlot.offset(-1, 0, 2)));
            s.setStationary(true);
            s.moveTo(w.x, w.y + 0.05, w.z, 0, 0);
            h.getLevel().addFreshEntity(s);
            sailor[0] = s;
            quay[0] = huskAt(h, h.absoluteVec(new Vec3(20.5, 9, 11.5)));
        });
        h.runAfterDelay(SETTLE_TICKS + 20, () -> {
            ShipBody on = ShipEntities.standingOrRiding(sailor[0]);
            h.assertTrue(on != null && on.id().equals(ship.id()), "the sailor is not tracked on the ship");
        });
        h.onEachTick(() -> {
            if (sailor[0] != null && !ship.id().equals(MonsterTargets.shipOf(sailor[0]))) {
                h.fail("the sailor is no longer aboard at tick " + h.getTick() + ": plot " + ship.toPlot(sailor[0].position()));
            }
            if (quay[0] != null && sailor[0] != null && quay[0].getTarget() == sailor[0]) {
                ShipBody on = ShipEntities.standingOrRiding(sailor[0]);
                h.fail("the husk on the pier hunted the deck at tick " + h.getTick() + ": sailor tracked on " + (on == null ? null : on.id())
                        + ", in water " + sailor[0].isInWater() + ", plot " + ship.toPlot(sailor[0].position())
                        + ", helm plot " + helmPlot + ", ship " + ship.id());
            }
        });
        h.runAfterDelay(SETTLE_TICKS + 120, () -> {
            quay[0].discard();
            quay[0] = null;
            boarder[0] = huskAt(h, ship.toWorld(Vec3.atBottomCenterOf(helmPlot.offset(1, 0, 3))).add(0, 0.05, 0));
        });
        h.runAfterDelay(SETTLE_TICKS + 121, () -> h.succeedWhen(() -> {
            h.assertTrue(boarder[0].getTarget() == sailor[0], "the husk aboard does not hunt the sailor: " + boarder[0].getTarget());
            sailor[0].discard();
            boarder[0].discard();
        }));
    }

    // ------------------------------------------------------------------ the harbor master and pirates

    private static ResourceLocation port(GameTestHelper h, PortKind kind) {
        ResourceLocation id = Constants.id("gametest/monsters_port_" + UUID.randomUUID());
        TradeService.openMarket(h.getLevel().getServer(), id,
                () -> new PortProfile(kind, Climate.TROPICAL, 1L, Map.of(TradeGoods.SUGAR, GoodRole.NEUTRAL)));
        return id;
    }

    private static HarborMaster master(GameTestHelper h, ResourceLocation port, BlockPos post) {
        HarborMaster m = HarborMasters.spawn(h.getLevel(), port, h.absolutePos(post), Direction.NORTH);
        h.assertTrue(m != null, "the harbor master of " + port + " spawns");
        return m;
    }

    /**
     * A pirate beside each: the island's harbor master (the fence) keeps his post and talks, the village's runs.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = BATCH + "fence")
    public static void theIslandFenceDoesNotFearPiratesTheVillageMasterDoes(GameTestHelper h) {
        floor(h, 24);
        ResourceLocation islandPort = port(h, PortKind.PIRATE_ISLAND), villagePort = port(h, PortKind.SEAFARER_VILLAGE);
        BlockPos islandPost = new BlockPos(19, 1, 19), villagePost = new BlockPos(4, 1, 4);
        HarborMaster island = master(h, islandPort, islandPost);
        HarborMaster village = master(h, villagePort, villagePost);
        Mob islandPirate = h.spawnWithNoFreeWill(MobContent.PIRATE.get(), 19, 1, 17);
        Mob villagePirate = h.spawnWithNoFreeWill(MobContent.PIRATE.get(), 4, 1, 6);
        h.assertValueEqual(island.portKind(), PortKind.PIRATE_ISLAND, "island kind");
        h.assertValueEqual(village.portKind(), PortKind.SEAFARER_VILLAGE, "village kind");
        h.assertFalse(island.fearsPirates(), "the fence fears pirates");
        h.assertFalse(island.fleesFrom(islandPirate), "the fence runs from a pirate");
        h.assertFalse(island.fleeing(), "the fence is shaken by a pirate");
        h.assertTrue(village.fleesFrom(villagePirate), "the village harbor master does not run from a pirate");
        h.assertTrue(village.fleeing(), "the village harbor master is calm beside a pirate");
        double[] villageFled = {0}, islandWandered = {0};
        h.onEachTick(() -> {
            villageFled[0] = Math.max(villageFled[0], Math.sqrt(h.absolutePos(villagePost).distToCenterSqr(village.position())));
            islandWandered[0] = Math.max(islandWandered[0], Math.sqrt(h.absolutePos(islandPost).distToCenterSqr(island.position())));
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(villageFled[0] > 3.0, "the village harbor master did not run: " + villageFled[0]);
            h.assertTrue(islandWandered[0] < HarborMasterConfig.RETURN_DISTANCE.get() + 1.0, "the fence left his post: " + islandWandered[0]);
            island.discard();
            village.discard();
            islandPirate.discard();
            villagePirate.discard();
            HarborMasterRegistry.get(h.getLevel().getServer()).remove(islandPort);
            HarborMasterRegistry.get(h.getLevel().getServer()).remove(villagePort);
            h.succeed();
        });
    }

    /** {@code mobs.harbor_master.fears_pirates_at_islands} on: the island's harbor master runs from pirates as well. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = CONFIG_BATCH + "fence")
    public static void theFenceCanBeMadeToFearPirates(GameTestHelper h) {
        ConfigOverrides.during(h, HarborMasterConfig.FEARS_PIRATES_AT_ISLANDS, true);
        floor(h, 9);
        ResourceLocation islandPort = port(h, PortKind.PIRATE_ISLAND);
        HarborMaster island = master(h, islandPort, new BlockPos(4, 1, 4));
        Mob pirate = h.spawnWithNoFreeWill(MobContent.PIRATE.get(), 4, 1, 6);
        h.assertTrue(island.fearsPirates() && island.fleesFrom(pirate), "the island harbor master does not fear pirates");
        island.discard();
        pirate.discard();
        HarborMasterRegistry.get(h.getLevel().getServer()).remove(islandPort);
        h.succeed();
    }
}
