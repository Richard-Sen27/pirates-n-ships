package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.mob.entity.Shark;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * The shark in a real server (M4). Every test with a shark runs in its own batch, so a shark never sees the players of
 * another test; tests that change config use {@code pirates_n_ships_config_*} batches. The basin is the whole 24×24
 * template: stone floor at y 1, stone walls, water from y 2 to {@link #WATER_TOP} (fluid surface at {@link #SURFACE}).
 */
public final class SharkGameTests {

    private static final int SIZE = 24;
    private static final int WATER_TOP = 6;
    /** The fluid surface: a source block under air is 8/9 of a block high, so not y 7.0. */
    private static final double SURFACE = WATER_TOP + 8.0 / 9.0;
    /**
     * The highest a shark's hitbox bottom may get when nothing but the shark moves it: the surface minus
     * {@link Shark#SURFACE_MARGIN}, plus 0.01 for float rounding.
     */
    private static final double HIGHEST_BOTTOM = SURFACE - Shark.SURFACE_MARGIN + 0.01;
    /** A swimmer's feet: in the water, eyes just above the surface. */
    private static final Vec3 SWIMMER = new Vec3(12.5, 5.6, 12.5);

    private SharkGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SharkGameTests.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void basin(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == SIZE - 1 || z == 0 || z == SIZE - 1;
                for (int y = 2; y <= WATER_TOP + 2; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= WATER_TOP ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    private static Shark shark(GameTestHelper h, int x, int y, int z) {
        return h.spawn(MobContent.SHARK.get(), new BlockPos(x, y, z));
    }

    /** A survival mock player held in place in the water (feet at {@link #SWIMMER}) every tick. */
    private static Player swimmer(GameTestHelper h) {
        Player player = MobTestSupport.playerInLevel(h, SWIMMER, 0);
        Vec3 at = h.absoluteVec(SWIMMER);
        h.onEachTick(() -> {
            player.setPos(at.x, at.y, at.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
        return player;
    }

    private static void run(GameTestHelper h, Vec3 relative, String command) {
        Vec3 pos = h.absoluteVec(relative);
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack()
                .withLevel(h.getLevel()).withPosition(pos).withPermission(4).withSuppressedOutput();
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    // ------------------------------------------------------------------ swimming

    /**
     * Without prey a shark cruises about the basin and never leaves the water, nor even comes near breaching: its
     * hitbox bottom stays {@link Shark#SURFACE_MARGIN} under the surface. (Q4: before, the move control's buoyancy
     * made an idle shark drift up and bob out of the water by a few hundredths of a block in about 1 run in 13.)
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_shark_cruise")
    public static void sharkStaysInWaterAndCruises(GameTestHelper h) {
        basin(h);
        Shark shark = shark(h, 6, 3, 6);
        Vec3 start = shark.position();
        double[] farthest = {0};
        h.onEachTick(() -> {
            if (shark.tickCount > 5 && !shark.isInWater()) h.fail("the shark left the water at " + h.relativeVec(shark.position()));
            double bottom = shark.getBoundingBox().minY - h.absoluteVec(Vec3.ZERO).y;
            if (bottom > HIGHEST_BOTTOM) h.fail("the shark rose to " + bottom + ", above " + HIGHEST_BOTTOM);
            farthest[0] = Math.max(farthest[0], shark.position().distanceTo(start));
        });
        h.runAfterDelay(280, () -> {
            h.assertTrue(shark.isAlive(), "the shark died");
            h.assertTrue(farthest[0] > 2.0, "the shark did not cruise: farthest " + farthest[0]);
            h.assertTrue(shark.getTarget() == null, "the shark found a target in an empty basin: " + shark.getTarget());
            h.succeed();
        });
    }

    /**
     * Swimming up hard (as a lunge at a swimmer above it would) stops under the surface: the shark's own moves never
     * carry its hitbox bottom above {@link #HIGHEST_BOTTOM}, so it never breaches.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 100, batch = "pirates_n_ships_shark_surface")
    public static void sharkSwimmingUpStopsUnderTheSurface(GameTestHelper h) {
        basin(h);
        Shark shark = shark(h, 12, 3, 12);
        double[] highest = {0};
        h.onEachTick(() -> {
            // isInWater is first updated on the shark's first tick
            if (shark.tickCount < 2 || shark.tickCount > 40) return;
            shark.setDeltaMovement(0, 0.5, 0);
            double bottom = shark.getBoundingBox().minY - h.absoluteVec(Vec3.ZERO).y;
            highest[0] = Math.max(highest[0], bottom);
            if (!shark.isInWater()) h.fail("the shark breached at " + h.relativeVec(shark.position()));
            if (bottom > HIGHEST_BOTTOM) h.fail("the shark rose to " + bottom + ", above " + HIGHEST_BOTTOM);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(highest[0] > HIGHEST_BOTTOM - 0.1, "the shark did not swim up to the surface: " + highest[0]);
            h.assertTrue(shark.isInWater(), "the shark is out of the water");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ hunting

    /** A shark notices a player swimming within range, circles, charges and bites: the player takes damage. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 400, batch = "pirates_n_ships_shark_bite")
    public static void sharkBitesASwimmingPlayer(GameTestHelper h) {
        basin(h);
        Player player = swimmer(h);
        Shark shark = shark(h, 4, 3, 4);
        h.succeedWhen(() -> {
            h.assertTrue(shark.getTarget() == player, "the shark did not target the swimmer");
            h.assertTrue(shark.bites() > 0, "no bite yet");
            h.assertTrue(player.getHealth() < player.getMaxHealth(), "the swimmer took no damage");
        });
    }

    /**
     * Blood frenzy: a swimmer below {@code frenzy_health_fraction} of its health is charged at once. Circling is set
     * longer than the test, so only the frenzy can explain a bite.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 250, batch = "pirates_n_ships_config_shark_frenzy")
    public static void frenzyChargesAWoundedSwimmerWithoutCircling(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.SHARK_CIRCLE_TICKS, 1200);
        basin(h);
        Player player = swimmer(h);
        player.setHealth(player.getMaxHealth() * 0.3f);
        float start = player.getHealth();
        Shark shark = shark(h, 6, 3, 6);
        h.succeedWhen(() -> {
            h.assertTrue(shark.bites() > 0, "no bite yet");
            h.assertTrue(player.getHealth() < start || !player.isAlive(), "the wounded swimmer took no damage");
        });
    }

    /** A player standing on planks above the water is never a target. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_shark_planks")
    public static void sharkIgnoresAPlayerOnPlanksAboveTheWater(GameTestHelper h) {
        basin(h);
        for (int x = 10; x <= 14; x++) {
            for (int z = 10; z <= 14; z++) h.setBlock(new BlockPos(x, WATER_TOP + 1, z), Blocks.OAK_PLANKS);
        }
        Player player = MobTestSupport.playerInLevel(h, new Vec3(12.5, WATER_TOP + 2, 12.5), 0);
        Shark shark = shark(h, 6, 3, 6);
        h.onEachTick(() -> {
            if (shark.getTarget() == player) h.fail("the shark targeted the player on the planks");
        });
        h.runAfterDelay(150, () -> {
            h.assertTrue(!player.isInWater(), "the player is in the water");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the player was hurt");
            h.succeed();
        });
    }

    /** A player riding a boat is never a target, even right above the shark. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_shark_boat")
    public static void sharkIgnoresAPlayerInABoat(GameTestHelper h) {
        basin(h);
        Boat boat = h.spawn(EntityType.BOAT, new Vec3(12.5, WATER_TOP + 1, 12.5));
        Player player = MobTestSupport.playerInLevel(h, new Vec3(12.5, WATER_TOP + 1.2, 12.5), 0);
        h.assertTrue(player.startRiding(boat, true), "the player could not board the boat");
        Shark shark = shark(h, 10, 3, 10);
        h.onEachTick(() -> {
            if (shark.getTarget() == player) h.fail("the shark targeted the player in the boat");
        });
        h.runAfterDelay(150, () -> {
            h.assertTrue(player.isPassenger(), "the player left the boat");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the player was hurt");
            h.succeed();
        });
    }

    /** {@code mobs.shark.peaceful}: it drops a forced target, never bites, and doesn't fight back when hit. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 200, batch = "pirates_n_ships_config_shark_peaceful")
    public static void peacefulSharkStaysPassive(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.SHARK_PEACEFUL, true);
        basin(h);
        Player player = swimmer(h);
        Shark shark = shark(h, 10, 3, 10);
        shark.setTarget(player);
        h.runAfterDelay(20, () -> shark.hurt(h.getLevel().damageSources().playerAttack(player), 1.0f));
        h.onEachTick(() -> {
            if (shark.bites() > 0) h.fail("the peaceful shark bit");
        });
        h.runAfterDelay(150, () -> {
            h.assertTrue(shark.getTarget() == null, "the peaceful shark kept a target");
            h.assertTrue(player.getHealth() >= player.getMaxHealth(), "the swimmer was hurt");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ land

    /** A shark on dry land (no water in reach) runs out of air and takes stranding damage, like a fish. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 420, batch = "pirates_n_ships_shark_stranded")
    public static void strandedSharkTakesDamage(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        Shark shark = shark(h, 12, 2, 12);
        h.succeedWhen(() -> {
            h.assertTrue(!shark.isInWater(), "the shark is in water");
            h.assertTrue(shark.getHealth() < shark.getMaxHealth(), "no stranding damage yet");
        });
    }

    /** A shark on the shore two blocks from the water flops into it. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 300, batch = "pirates_n_ships_shark_shore")
    public static void strandedSharkFlopsBackIntoTheWater(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean land = x < 12;
                boolean wall = x == SIZE - 1 || z == 0 || z == SIZE - 1;
                for (int y = 2; y <= 3; y++) {
                    h.setBlock(new BlockPos(x, y, z), land || wall ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
        Shark shark = shark(h, 9, 4, 12);
        h.succeedWhen(() -> h.assertTrue(shark.isInWater(), "the shark is still on land at " + h.relativeVec(shark.position())));
    }

    // ------------------------------------------------------------------ config, command, spawning

    /** {@code mobs.shark.enabled = false}: an existing shark disappears and the command refuses to spawn one. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = "pirates_n_ships_config_shark_disabled")
    public static void disabledSharkDisappearsAndCannotBeSpawned(GameTestHelper h) {
        ConfigOverrides.during(h, MobConfig.SHARK_ENABLED, false);
        basin(h);
        Shark shark = shark(h, 6, 3, 6);
        run(h, new Vec3(12.5, 3, 12.5), "pirates mob spawn shark 2");
        h.runAfterDelay(5, () -> {
            h.assertTrue(shark.isRemoved(), "the disabled shark is still there");
            h.assertValueEqual(h.getEntities(MobContent.SHARK.get()).size(), 0, "sharks left");
            h.succeed();
        });
    }

    /** {@code /pirates mob spawn shark 3} in the water spawns three sharks, all in the water. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, batch = "pirates_n_ships_shark_command")
    public static void spawnCommandSpawnsSharksInTheWater(GameTestHelper h) {
        basin(h);
        run(h, new Vec3(12.5, 3, 12.5), "pirates mob spawn shark 3");
        h.runAfterDelay(2, () -> {
            List<Shark> sharks = h.getEntities(MobContent.SHARK.get());
            h.assertValueEqual(sharks.size(), 3, "sharks spawned");
            for (Shark s : sharks) h.assertTrue(s.isInWater(), "a shark is not in the water: " + h.relativeVec(s.position()));
            h.succeed();
        });
    }

    /**
     * Natural spawning reaches the game: ocean and deep ocean biomes list the shark among their water creatures with
     * the configured weight and group (through the loader's biome hook), plains don't, and the spawn placement is
     * in-water on the ocean floor heightmap with the shark's predicate (refuses stone).
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void sharksSpawnNaturallyInOceans(GameTestHelper h) {
        Registry<Biome> biomes = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (ResourceKey<Biome> ocean : List.of(Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.DEEP_COLD_OCEAN)) {
            List<MobSpawnSettings.SpawnerData> sharks = sharkSpawns(biomes.getOrThrow(ocean));
            h.assertValueEqual(sharks.size(), 1, ocean.location() + " shark spawn entries");
            MobSpawnSettings.SpawnerData data = sharks.get(0);
            h.assertValueEqual(data.getWeight().asInt(), MobConfig.SHARK_SPAWN_WEIGHT.get(), ocean.location() + " weight");
            h.assertValueEqual(data.minCount, MobConfig.SHARK_MIN_GROUP.get(), ocean.location() + " min group");
            h.assertValueEqual(data.maxCount, MobConfig.SHARK_MAX_GROUP.get(), ocean.location() + " max group");
        }
        h.assertValueEqual(sharkSpawns(biomes.getOrThrow(Biomes.PLAINS)).size(), 0, "plains shark spawn entries");
        h.assertTrue(SpawnPlacements.getPlacementType(MobContent.SHARK.get()) == SpawnPlacementTypes.IN_WATER, "placement type");
        h.assertTrue(SpawnPlacements.getHeightmapType(MobContent.SHARK.get()) == Heightmap.Types.OCEAN_FLOOR, "heightmap");
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        h.assertTrue(!SpawnPlacements.checkSpawnRules(MobContent.SHARK.get(), h.getLevel(), MobSpawnType.NATURAL,
                h.absolutePos(new BlockPos(4, 1, 4)), h.getLevel().getRandom()), "a shark may spawn in stone");
        h.succeed();
    }

    private static List<MobSpawnSettings.SpawnerData> sharkSpawns(Biome biome) {
        return biome.getMobSettings().getMobs(MobCategory.WATER_CREATURE).unwrap().stream()
                .filter(d -> d.type == MobContent.SHARK.get()).toList();
    }
}
