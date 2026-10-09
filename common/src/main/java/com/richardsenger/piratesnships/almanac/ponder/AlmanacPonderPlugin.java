package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Our Ponder plugin (docs/design.md §3.4, ALM4), added to {@code PonderIndex} on the physical client by
 * {@link AlmanacPonder#register()}. It registers every scene of {@link AlmanacScenes#ALL} for its item (so Ponder's key
 * on the item, "hold W", plays it) and the scenes' shared text (the English defaults Ponder's editing mode shows; the
 * game reads the generated lang).
 *
 * <p>No tags: Ponder's index lists every item with scenes without one.
 */
public final class AlmanacPonderPlugin implements PonderPlugin {

    /** The storyboard of each of our scene ids (a scene of {@link AlmanacScenes#ALL} without one fails here). */
    static final Map<ResourceLocation, PonderStoryBoard> STORYBOARDS = Map.ofEntries(
            Map.entry(AlmanacScenes.CANNON_LOADING, CannonLoadingScene::program),
            Map.entry(AlmanacScenes.SHIP_ASSEMBLY, ShipAssemblyScene::program),
            Map.entry(AlmanacScenes.STEERING, SteeringScene::program),
            Map.entry(AlmanacScenes.SAILS_AND_WINCH, SailsAndWinchScene::program),
            Map.entry(AlmanacScenes.ANCHOR_AND_CAPSTAN, AnchorAndCapstanScene::program),
            Map.entry(AlmanacScenes.PUMPS_AND_FLOODING, PumpsAndFloodingScene::program),
            Map.entry(AlmanacScenes.HULL_REPAIR, HullRepairScene::program),
            Map.entry(AlmanacScenes.MOORING, MooringScene::program),
            Map.entry(AlmanacScenes.CARGO_AND_WEIGHT, CargoAndWeightScene::program),
            Map.entry(AlmanacScenes.CREW_ORDERS, CrewOrdersScene::program),
            Map.entry(AlmanacScenes.BOARDING, BoardingScene::program),
            Map.entry(AlmanacScenes.FLAGS_AND_ALLEGIANCE, FlagsAndAllegianceScene::program),
            Map.entry(AlmanacScenes.CROWS_NEST, CrowsNestScene::program),
            Map.entry(AlmanacScenes.SHIPS_BELL, ShipsBellScene::program),
            Map.entry(AlmanacScenes.GALLEY_AND_PROVISIONS, GalleyAndProvisionsScene::program),
            Map.entry(AlmanacScenes.HAMMOCKS_AND_REST, HammocksAndRestScene::program),
            Map.entry(AlmanacScenes.SWIVEL_GUN, SwivelGunScene::program),
            Map.entry(AlmanacScenes.OARS, OarsScene::program),
            Map.entry(AlmanacScenes.RATLINE_NETS, RatlineNetsScene::program),
            Map.entry(AlmanacScenes.RAILINGS, RailingsScene::program),
            Map.entry(AlmanacScenes.SHIP_IDENTITY, ShipIdentityScene::program),
            Map.entry(AlmanacScenes.ON_DECK, OnDeckScene::program));

    @Override
    public String getModId() {
        return Constants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            PonderStoryBoard board = STORYBOARDS.get(scene.id());
            if (board == null) {
                throw new IllegalStateException("no storyboard for the Ponder scene " + scene.id());
            }
            helper.addStoryBoard(scene.component(), scene.schematic(), board);
        }
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        for (SceneLine line : SceneText.allLines()) {
            helper.registerSharedText(line.key().getPath(), line.english());
        }
    }
}
