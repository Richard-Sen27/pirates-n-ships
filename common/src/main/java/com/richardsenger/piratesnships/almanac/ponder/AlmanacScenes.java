package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Our Ponder scenes (docs/design.md §3.4, docs/almanac.md "Scenes"), as plain data without any Ponder class, so the
 * Almanac's "Show me" ids resolve and are tested headless. The {@link AlmanacPonderPlugin} registers exactly these.
 *
 * <p>A scene's id is the id its storyboard gives itself ({@code scene.title(path, ...)} in our namespace) and the id a
 * "Show me" element of a chapter carries; its schematic is {@code assets/<ns>/ponder/<path>.nbt} (written by
 * {@code art/structures/ponder/<path>.py} through tools/build_structures.py); its component is the item whose Ponder
 * key (hold W over the item) lists it.
 */
public final class AlmanacScenes {

    /** One scene: its id (= "Show me" id), the item it is registered for, and its schematic. */
    public record Scene(ResourceLocation id, ResourceLocation component, ResourceLocation schematic) {
    }

    /** "Loading and firing the cannon" (combat/cannon, design.md §8.2). */
    public static final ResourceLocation CANNON_LOADING = Constants.id(CannonLoadingText.SCENE);
    /** The cannon's item id ({@code CannonContent.CANNON}, a block item). */
    public static final ResourceLocation CANNON_ITEM = Constants.id("cannon");

    /** PND1, the ship mechanics (docs/almanac.md "Scenes"): each scene's id is also its schematic. */
    public static final ResourceLocation SHIP_ASSEMBLY = Constants.id(ShipSceneText.SHIP_ASSEMBLY);
    public static final ResourceLocation SAILS_AND_WINCH = Constants.id(ShipSceneText.SAILS_AND_WINCH);
    public static final ResourceLocation STEERING = Constants.id(ShipSceneText.STEERING);
    public static final ResourceLocation ANCHOR_AND_CAPSTAN = Constants.id(ShipSceneText.ANCHOR_AND_CAPSTAN);
    public static final ResourceLocation PUMPS_AND_FLOODING = Constants.id(ShipSceneText.PUMPS_AND_FLOODING);
    public static final ResourceLocation HULL_REPAIR = Constants.id(ShipSceneText.HULL_REPAIR);
    public static final ResourceLocation MOORING = Constants.id(ShipSceneText.MOORING);
    public static final ResourceLocation CARGO_AND_WEIGHT = Constants.id(ShipSceneText.CARGO_AND_WEIGHT);

    /** The items whose Ponder key plays those scenes (block items share their block's id). */
    public static final ResourceLocation HELM_ITEM = Constants.id("helm");
    public static final ResourceLocation SAIL_WINCH_ITEM = Constants.id("sail_winch");
    public static final ResourceLocation CAPSTAN_ITEM = Constants.id("capstan");
    public static final ResourceLocation BILGE_PUMP_ITEM = Constants.id("bilge_pump");
    public static final ResourceLocation HULL_PATCH_ITEM = Constants.id("hull_patch");
    public static final ResourceLocation MOORING_RING_ITEM = Constants.id("mooring_ring");
    public static final ResourceLocation CARGO_CRATE_ITEM = Constants.id("cargo_crate");

    /** PND2, crew, combat and life aboard: each scene's id is also its schematic. */
    public static final ResourceLocation CREW_ORDERS = Constants.id(CrewSceneText.CREW_ORDERS);
    public static final ResourceLocation BOARDING = Constants.id(CrewSceneText.BOARDING);
    public static final ResourceLocation FLAGS_AND_ALLEGIANCE = Constants.id(CrewSceneText.FLAGS_AND_ALLEGIANCE);
    public static final ResourceLocation CROWS_NEST = Constants.id(CrewSceneText.CROWS_NEST);
    public static final ResourceLocation SHIPS_BELL = Constants.id(CrewSceneText.SHIPS_BELL);
    public static final ResourceLocation GALLEY_AND_PROVISIONS = Constants.id(CrewSceneText.GALLEY_AND_PROVISIONS);
    public static final ResourceLocation HAMMOCKS_AND_REST = Constants.id(CrewSceneText.HAMMOCKS_AND_REST);
    public static final ResourceLocation SWIVEL_GUN = Constants.id(CrewSceneText.SWIVEL_GUN);
    /** OAR1: rowing, at the rail and on the oar bench. */
    public static final ResourceLocation OARS = Constants.id(CrewSceneText.OARS);

    /** Their items. */
    public static final ResourceLocation CAPTAINS_WHISTLE_ITEM = Constants.id("captains_whistle");
    public static final ResourceLocation BOARDING_PLANK_ITEM = Constants.id("boarding_plank");
    public static final ResourceLocation JOLLY_ROGER_FLAG_ITEM = Constants.id("jolly_roger_flag");
    public static final ResourceLocation CROWS_NEST_ITEM = Constants.id("crows_nest");
    public static final ResourceLocation SHIPS_BELL_ITEM = Constants.id("ships_bell");
    public static final ResourceLocation PANTRY_ITEM = Constants.id("pantry");
    public static final ResourceLocation HAMMOCK_ITEM = Constants.id("hammock");
    public static final ResourceLocation SWIVEL_GUN_ITEM = Constants.id("swivel_gun");
    /** The oak oar bench, the oar bench family's lead ({@code sailing.oars.OarsContent#BENCHES}). */
    public static final ResourceLocation OAR_BENCH_ITEM = Constants.id("oak_oar_bench");

    /** Every scene, in the order the plugin registers them (an item's scenes play in this order). */
    public static final List<Scene> ALL = List.of(
            new Scene(CANNON_LOADING, CANNON_ITEM, CANNON_LOADING),
            new Scene(SHIP_ASSEMBLY, HELM_ITEM, SHIP_ASSEMBLY),
            new Scene(STEERING, HELM_ITEM, STEERING),
            new Scene(SAILS_AND_WINCH, SAIL_WINCH_ITEM, SAILS_AND_WINCH),
            new Scene(ANCHOR_AND_CAPSTAN, CAPSTAN_ITEM, ANCHOR_AND_CAPSTAN),
            new Scene(PUMPS_AND_FLOODING, BILGE_PUMP_ITEM, PUMPS_AND_FLOODING),
            new Scene(HULL_REPAIR, HULL_PATCH_ITEM, HULL_REPAIR),
            new Scene(MOORING, MOORING_RING_ITEM, MOORING),
            new Scene(CARGO_AND_WEIGHT, CARGO_CRATE_ITEM, CARGO_AND_WEIGHT),
            new Scene(CREW_ORDERS, CAPTAINS_WHISTLE_ITEM, CREW_ORDERS),
            new Scene(BOARDING, BOARDING_PLANK_ITEM, BOARDING),
            new Scene(FLAGS_AND_ALLEGIANCE, JOLLY_ROGER_FLAG_ITEM, FLAGS_AND_ALLEGIANCE),
            new Scene(CROWS_NEST, CROWS_NEST_ITEM, CROWS_NEST),
            new Scene(SHIPS_BELL, SHIPS_BELL_ITEM, SHIPS_BELL),
            new Scene(GALLEY_AND_PROVISIONS, PANTRY_ITEM, GALLEY_AND_PROVISIONS),
            new Scene(HAMMOCKS_AND_REST, HAMMOCK_ITEM, HAMMOCKS_AND_REST),
            new Scene(SWIVEL_GUN, SWIVEL_GUN_ITEM, SWIVEL_GUN),
            new Scene(OARS, OAR_BENCH_ITEM, OARS));

    private AlmanacScenes() {
    }

    /** The scene with this id, if it is ours. */
    public static Optional<Scene> byId(ResourceLocation id) {
        return ALL.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** Our scenes registered for this item, in registration order. */
    public static List<Scene> forComponent(ResourceLocation component) {
        return ALL.stream().filter(s -> s.component().equals(component)).toList();
    }

    /**
     * What {@link AlmanacPonder#show} opens for an id: {@code scene} non-null opens that one scene of
     * {@code component}; null opens every scene of the item {@code component} (Ponder's own item view).
     */
    public record Target(ResourceLocation component, @Nullable Scene scene) {
    }

    /**
     * Resolves a "Show me" id: one of our scene ids opens that scene; an item id with our scenes opens the item's
     * scene list; anything else is unknown (empty). Ponder may know scenes of other mods for an item id;
     * {@link AlmanacPonder#show} asks Ponder about those.
     */
    public static Optional<Target> resolve(ResourceLocation id) {
        Optional<Scene> scene = byId(id);
        if (scene.isPresent()) {
            return Optional.of(new Target(scene.get().component(), scene.get()));
        }
        return forComponent(id).isEmpty() ? Optional.empty() : Optional.of(new Target(id, null));
    }
}
