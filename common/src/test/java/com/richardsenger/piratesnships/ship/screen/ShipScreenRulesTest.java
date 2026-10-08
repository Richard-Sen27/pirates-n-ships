package com.richardsenger.piratesnships.ship.screen;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HGUI1: the ship screen's pure rules (who may, how the ship reads, which buttons are active). */
class ShipScreenRulesTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    static ShipScreenView.CrewLine crew(String name, Optional<BlockPos> station, boolean mayCommand) {
        return new ShipScreenView.CrewLine(UUID.nameUUIDFromBytes(name.getBytes()), name, 70,
                station.isPresent() ? ShipScreenRules.CrewState.STATION : ShipScreenRules.CrewState.FREE, station,
                station.isPresent() ? "block.pirates_n_ships.sail_winch" : "", "", Optional.empty(), false, false,
                ShipScreenRules.Desertion.NONE, 0, mayCommand);
    }

    static ShipScreenView.StationLine station(BlockPos pos, Optional<UUID> occupant, boolean player) {
        return new ShipScreenView.StationLine(pos, "block.pirates_n_ships.sail_winch", occupant, occupant.isPresent() ? "Anne" : "",
                player, "", "");
    }

    static ShipScreenView view(List<ShipScreenView.CrewLine> crew, List<ShipScreenView.StationLine> stations,
                               ShipScreenView.Toggles toggles) {
        return new ShipScreenView(UUID.randomUUID(), new BlockPos(1, 2, 3),
                new ShipScreenView.Header("Capt. Black Gull", "Black Gull", "jolly_roger", ShipScreenRules.Allegiance.PIRATE, false,
                        Optional.of("Steve"), false, Optional.of("pirates_n_ships.career.title.captain")),
                new ShipScreenView.Status(new ShipScreenRules.Hull(3, 1, 2, 1, 0.2f), 1, 2.5f, ShipScreenRules.Anchor.STOWED, 2, 1, 0),
                new ShipScreenView.Upkeep(2, 3, 3.5, Double.POSITIVE_INFINITY, 0.0, true, true, 2, 0, 4),
                crew, stations, toggles);
    }

    @Test
    void theOwnerOrAnyoneOnAnOwnerlessShipManages() {
        assertTrue(ShipScreenRules.mayManage(ME, Optional.of(ME)));
        assertFalse(ShipScreenRules.mayManage(ME, Optional.of(OTHER)));
        assertTrue(ShipScreenRules.mayManage(ME, Optional.empty()));
    }

    @Test
    void crewIsCommandedByItsHirerOrTheOwner() {
        assertTrue(ShipScreenRules.mayCommand(ME, Optional.of(ME), Optional.of(OTHER)), "the hirer");
        assertTrue(ShipScreenRules.mayCommand(ME, Optional.empty(), Optional.of(ME)), "the owner");
        assertFalse(ShipScreenRules.mayCommand(ME, Optional.of(OTHER), Optional.of(OTHER)), "nobody's hand of mine");
        assertTrue(ShipScreenRules.mayCommand(ME, Optional.of(OTHER), Optional.empty()), "ownerless ship");
    }

    @Test
    void reachIsInclusive() {
        assertTrue(ShipScreenRules.inReach(8.0, 8.0));
        assertFalse(ShipScreenRules.inReach(8.01, 8.0));
    }

    @Test
    void hullSummarisesTheHudStrip() {
        List<ShipStatusPayload.Cell> cells = List.of(
                new ShipStatusPayload.Cell(0, 10, 0f, 0, false),
                new ShipStatusPayload.Cell(1, 10, 0.4f, 0, false),   // 4 %: a puddle
                new ShipStatusPayload.Cell(2, 20, 10f, 2, true));
        ShipScreenRules.Hull h = ShipScreenRules.hull(cells);
        assertEquals(3, h.compartments());
        assertEquals(1, h.flooded());
        assertEquals(2, h.breaches());
        assertEquals(1, h.pumping());
        assertEquals(10.4f / 40f, h.water(), 1e-6);
        assertEquals(ShipScreenRules.Hull.NONE, ShipScreenRules.hull(List.of()));
    }

    @Test
    void anchorReadsThePhase() {
        assertEquals(ShipScreenRules.Anchor.UNKNOWN, ShipScreenRules.anchor(false, null, false));
        assertEquals(ShipScreenRules.Anchor.STOWED, ShipScreenRules.anchor(true, null, false));
        assertEquals(ShipScreenRules.Anchor.STOWED, ShipScreenRules.anchor(true, AnchorState.Phase.RAISED, false));
        assertEquals(ShipScreenRules.Anchor.DROPPING, ShipScreenRules.anchor(true, AnchorState.Phase.DROPPING, false));
        assertEquals(ShipScreenRules.Anchor.DOWN, ShipScreenRules.anchor(true, AnchorState.Phase.HOLDING, false));
        assertEquals(ShipScreenRules.Anchor.ANCHORED, ShipScreenRules.anchor(true, AnchorState.Phase.HOLDING, true));
        assertEquals(ShipScreenRules.Anchor.RAISING, ShipScreenRules.anchor(true, AnchorState.Phase.RAISING, false));
    }

    @Test
    void allegianceFollowsTheFlagShown() {
        assertEquals(ShipScreenRules.Allegiance.NONE, ShipScreenRules.allegiance(FlagReading.NO_FLAG));
        assertEquals(ShipScreenRules.Allegiance.PIRATE, ShipScreenRules.allegiance(FlagReading.flying(FlagKind.JOLLY_ROGER)));
        assertEquals(ShipScreenRules.Allegiance.NAVY, ShipScreenRules.allegiance(FlagReading.flying(FlagKind.NAVY)));
        assertEquals(ShipScreenRules.Allegiance.MERCHANT, ShipScreenRules.allegiance(FlagReading.flying(FlagKind.MERCHANT)));
        assertEquals(ShipScreenRules.Allegiance.CUSTOM, ShipScreenRules.allegiance(FlagReading.flying(FlagKind.CUSTOM)));
        assertEquals(ShipScreenRules.Allegiance.STRUCK, ShipScreenRules.allegiance(FlagReading.struck(FlagKind.NAVY)));
    }

    @Test
    void desertionWarnsBeforeTheLastDawn() {
        // desert below 20 after 2 dawns (the defaults)
        assertEquals(ShipScreenRules.Desertion.NONE, ShipScreenRules.desertion(true, 20, 2, 20, 0));
        assertEquals(ShipScreenRules.Desertion.LOW, ShipScreenRules.desertion(true, 20, 2, 19, 0));
        assertEquals(ShipScreenRules.Desertion.LEAVING, ShipScreenRules.desertion(true, 20, 2, 19, 1));
        assertEquals(ShipScreenRules.Desertion.LEAVING, ShipScreenRules.desertion(true, 20, 1, 5, 0));
        assertEquals(ShipScreenRules.Desertion.NONE, ShipScreenRules.desertion(false, 20, 2, 5, 5), "desertion off");
    }

    @Test
    void namesAreCleaned() {
        assertEquals(Optional.of("Sea Wolf"), ShipScreenRules.cleanName("  Sea Wolf "));
        assertEquals(Optional.empty(), ShipScreenRules.cleanName("   "));
        assertEquals(Optional.of("Red"), ShipScreenRules.cleanName("§cRed\n"));
        assertEquals(ShipScreenView.MAX_NAME, ShipScreenRules.cleanName("x".repeat(80)).orElseThrow().length());
    }

    @Test
    void buttonsFollowRightsTogglesAndState() {
        BlockPos winch = new BlockPos(5, 9, 5);
        BlockPos pump = new BlockPos(6, 9, 5);
        ShipScreenView.CrewLine anne = crew("Anne", Optional.of(winch), true);
        ShipScreenView.CrewLine bart = crew("Bart", Optional.empty(), true);
        ShipScreenView.CrewLine stranger = crew("Cole", Optional.empty(), false);
        ShipScreenView.StationLine manned = station(winch, Optional.of(anne.id()), false);
        ShipScreenView.StationLine free = station(pump, Optional.empty(), false);
        ShipScreenView.StationLine byPlayer = station(new BlockPos(7, 9, 5), Optional.of(UUID.randomUUID()), true);
        ShipScreenView all = view(List.of(anne, bart, stranger), List.of(manned, free, byPlayer), new ShipScreenView.Toggles(true, true, true));

        assertTrue(ShipScreenRules.canRelease(all, anne));
        assertFalse(ShipScreenRules.canRelease(all, bart), "not at a station");
        assertTrue(ShipScreenRules.canDismiss(all, bart));
        assertFalse(ShipScreenRules.canDismiss(all, stranger), "not mine to dismiss");
        assertEquals(List.of(bart), ShipScreenRules.freeCrew(all), "free and commandable");
        assertTrue(ShipScreenRules.canMan(all, free));
        assertFalse(ShipScreenRules.canMan(all, manned), "already manned");
        assertTrue(ShipScreenRules.canReleaseAt(all, manned));
        assertFalse(ShipScreenRules.canReleaseAt(all, byPlayer), "a player is not released from the screen");
        assertFalse(ShipScreenRules.canReleaseAt(all, free));
        assertTrue(ShipScreenRules.canOrder(all));
        assertTrue(ShipScreenRules.canDisassemble(all));
        assertTrue(ShipScreenRules.canRename(all, "Sea Wolf"));
        assertFalse(ShipScreenRules.canRename(all, "Black Gull"), "unchanged");
        assertFalse(ShipScreenRules.canRename(all, "  "), "empty");

        ShipScreenView noStations = view(List.of(anne, bart), List.of(manned, free), new ShipScreenView.Toggles(false, true, true));
        assertFalse(ShipScreenRules.canRelease(noStations, anne));
        assertFalse(ShipScreenRules.canMan(noStations, free));
        assertFalse(ShipScreenRules.canOrder(noStations));
        assertFalse(ShipScreenRules.canReleaseAt(noStations, manned));

        ShipScreenView noHiring = view(List.of(anne, bart), List.of(manned, free), new ShipScreenView.Toggles(true, false, true));
        assertFalse(ShipScreenRules.canDismiss(noHiring, bart));

        ShipScreenView nobodyFree = view(List.of(anne), List.of(manned, free), new ShipScreenView.Toggles(true, true, true));
        assertFalse(ShipScreenRules.canMan(nobodyFree, free), "no free hand to send");

        ShipScreenView notCaptain = view(List.of(anne, bart), List.of(manned, free), new ShipScreenView.Toggles(true, true, false));
        assertFalse(ShipScreenRules.canOrder(notCaptain));
        assertFalse(ShipScreenRules.canDisassemble(notCaptain));
        assertFalse(ShipScreenRules.canRename(notCaptain, "Sea Wolf"));
    }
}
