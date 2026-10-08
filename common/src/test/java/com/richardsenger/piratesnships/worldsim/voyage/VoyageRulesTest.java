package com.richardsenger.piratesnships.worldsim.voyage;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoyageRulesTest {

    private static final VoyageRules.Speed SPEED = new VoyageRules.Speed(4.0, true, 1.2, 0.6);
    private static final ResourceLocation SUGAR = id("sugar"), IRON = id("iron"), FISH = id("fish"), RUM = id("rum");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("pirates_n_ships", path);
    }

    private static VoyageRules.PortView port(String name, int x, List<ResourceLocation> produces, List<ResourceLocation> demands) {
        return port(name, "minecraft:overworld", x, produces, demands);
    }

    private static VoyageRules.PortView port(String name, String dim, int x, List<ResourceLocation> produces, List<ResourceLocation> demands) {
        Set<ResourceLocation> traded = new java.util.HashSet<>(produces);
        traded.addAll(demands);
        traded.add(FISH);
        return new VoyageRules.PortView(id(name), dim, x, 0, produces, demands, traded);
    }

    private static Voyage voyage(List<Lane.Point> points) {
        return Voyage.depart(UUID.randomUUID(), VoyageKind.CONVOY, Faction.MERCHANTS, id("starter_sloop"), id("a"), id("b"),
                points, Map.of(SUGAR, 64), 100L);
    }

    @Test
    void windFactorRunsFromHeadwindToDownwind() {
        assertEquals(1.2, VoyageRules.windFactor(90, 90, 1.0, SPEED), 1e-9, "wind astern");
        assertEquals(0.6, VoyageRules.windFactor(90, 270, 1.0, SPEED), 1e-9, "wind ahead");
        assertEquals(0.9, VoyageRules.windFactor(0, 90, 1.0, SPEED), 1e-9, "beam reach");
        assertEquals(1.0, VoyageRules.windFactor(90, 270, 0.0, SPEED), 1e-9, "calm");
        assertEquals(1.0, VoyageRules.windFactor(90, 270, 1.0, new VoyageRules.Speed(4, false, 1.2, 0.6)), 1e-9, "toggle off");
    }

    @Test
    void blocksPerCheck() {
        assertEquals(4.0, VoyageRules.blocksIn(SPEED, 20, 1.0), 1e-9);
        assertEquals(4.8, VoyageRules.blocksIn(SPEED, 20, 1.2), 1e-9);
        assertEquals(40.0, VoyageRules.blocksIn(SPEED, 200, 1.0), 1e-9);
    }

    @Test
    void advanceClampsAtTheEnd() {
        Voyage v = voyage(List.of(new Lane.Point(0, 0), new Lane.Point(100, 0)));
        Voyage a = VoyageRules.advance(v, 30);
        assertEquals(30, a.progress(), 1e-9);
        assertFalse(a.arrived());
        assertEquals(30, a.position().x(), 1e-9);
        Voyage b = VoyageRules.advance(a, 500);
        assertEquals(100, b.progress(), 1e-9);
        assertTrue(b.arrived());
        assertEquals(30, VoyageRules.advance(a, -5).progress(), 1e-9, "never backwards");
    }

    @Test
    void spawnChanceAndCap() {
        assertEquals(2.0 * 20 / 24000, VoyageRules.chancePerCheck(2.0, 20), 1e-12);
        assertEquals(0.0, VoyageRules.chancePerCheck(0.0, 20));
        assertEquals(1.0, VoyageRules.chancePerCheck(100000, 20));
        assertTrue(VoyageRules.rollSpawn(0.1, 0.05, 3, 4));
        assertFalse(VoyageRules.rollSpawn(0.1, 0.05, 4, 4), "cap reached");
        assertFalse(VoyageRules.rollSpawn(0.1, 0.2, 0, 4), "roll too high");
        assertFalse(VoyageRules.rollSpawn(0.0, 0.0, 0, 4), "zero chance never spawns");
    }

    @Test
    void convoyGoesWhereItsGoodsAreDemanded() {
        VoyageRules.PortView a = port("a", 0, List.of(SUGAR), List.of(IRON));
        VoyageRules.PortView b = port("b", 1000, List.of(IRON), List.of(SUGAR));
        VoyageRules.PortView c = port("c", 2000, List.of(), List.of(RUM));
        RandomSource rng = RandomSource.create(1);
        for (int i = 0; i < 50; i++) {
            VoyageRules.ConvoyPlan p = VoyageRules.planConvoy(List.of(a, b, c), 200, 3000, 64, rng).orElseThrow();
            assertTrue(p.from().equals(a.id()) && p.to().equals(b.id()) || p.from().equals(b.id()) && p.to().equals(a.id()), p.toString());
            ResourceLocation expected = p.from().equals(a.id()) ? SUGAR : IRON;
            assertEquals(Map.of(expected, 64), p.cargo());
        }
    }

    @Test
    void distanceLimitsAndDimensionsExcludePorts() {
        VoyageRules.PortView a = port("a", 0, List.of(SUGAR), List.of());
        VoyageRules.PortView near = port("near", 100, List.of(), List.of(SUGAR));
        VoyageRules.PortView far = port("far", 5000, List.of(), List.of(SUGAR));
        VoyageRules.PortView nether = port("nether", "minecraft:the_nether", 1000, List.of(), List.of(SUGAR));
        assertEquals(Optional.empty(), VoyageRules.planConvoy(List.of(a, near, far, nether), 200, 3000, 64, RandomSource.create(2)));
        assertTrue(VoyageRules.planConvoy(List.of(a, port("ok", 1500, List.of(), List.of(SUGAR))), 200, 3000, 64, RandomSource.create(2)).isPresent());
    }

    @Test
    void nearerDestinationsAreFavoured() {
        VoyageRules.PortView a = port("a", 0, List.of(SUGAR), List.of());
        VoyageRules.PortView b = port("b", 300, List.of(), List.of(SUGAR));
        VoyageRules.PortView c = port("c", 2700, List.of(), List.of(SUGAR));
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        RandomSource rng = RandomSource.create(3);
        for (int i = 0; i < 2000; i++) {
            counts.merge(VoyageRules.planConvoy(List.of(a, b, c), 200, 3000, 64, rng).orElseThrow().to(), 1, Integer::sum);
        }
        // Weights 1/300 : 1/2700 = 9 : 1
        double share = counts.getOrDefault(b.id(), 0) / 2000.0;
        assertEquals(0.9, share, 0.03);
    }

    @Test
    void cargoPutsDemandedGoodsFirst() {
        VoyageRules.PortView a = port("a", 0, List.of(SUGAR, IRON, RUM), List.of());
        VoyageRules.PortView b = new VoyageRules.PortView(id("b"), "minecraft:overworld", 1000, 0, List.of(), List.of(RUM),
                Set.of(RUM, SUGAR));
        RandomSource rng = RandomSource.create(4);
        boolean sawTwo = false;
        for (int i = 0; i < 100; i++) {
            Map<ResourceLocation, Integer> cargo = VoyageRules.cargo(a, b, 32, rng);
            assertTrue(cargo.containsKey(RUM), "the demanded good always goes: " + cargo);
            assertFalse(cargo.containsKey(IRON), "not traded at the destination");
            assertTrue(cargo.size() >= 1 && cargo.size() <= 2);
            assertTrue(cargo.values().stream().allMatch(u -> u == 32));
            sawTwo |= cargo.size() == 2;
        }
        assertTrue(sawTwo, "sometimes a second good");
    }

    @Test
    void cargoFallsBackToSharedGoods() {
        VoyageRules.PortView a = new VoyageRules.PortView(id("a"), "d", 0, 0, List.of(), List.of(), Set.of(FISH));
        VoyageRules.PortView b = new VoyageRules.PortView(id("b"), "d", 900, 0, List.of(), List.of(), Set.of(FISH, RUM));
        assertEquals(Map.of(FISH, 10), VoyageRules.cargo(a, b, 10, RandomSource.create(5)));
        VoyageRules.PortView c = new VoyageRules.PortView(id("c"), "d", 900, 0, List.of(), List.of(), Set.of(RUM));
        assertTrue(VoyageRules.cargo(a, c, 10, RandomSource.create(5)).isEmpty());
    }

    @Test
    void voyageSurvivesTheCodec() {
        Voyage v = voyage(List.of(new Lane.Point(0, 0), new Lane.Point(100, 0), new Lane.Point(100, -40)))
                .withProgress(57.5).withCrew(3, 2).withHealth(0.75)
                .withState(Voyage.State.MATERIALISED, Optional.of(UUID.randomUUID()))
                .withPursuit(Optional.of(UUID.randomUUID()));
        Tag tag = Voyage.CODEC.encodeStart(NbtOps.INSTANCE, v).getOrThrow();
        assertEquals(v, Voyage.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());

        Voyage fresh = voyage(List.of(new Lane.Point(5, 5), new Lane.Point(-5, 5)));
        Tag list = VoyageData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.of(v, fresh)).getOrThrow();
        assertEquals(List.of(v, fresh), VoyageData.LIST_CODEC.parse(NbtOps.INSTANCE, list).getOrThrow());
        assertEquals(Voyage.UNMANNED, fresh.crew());
        assertEquals(Faction.MERCHANTS, fresh.faction());
    }
}
