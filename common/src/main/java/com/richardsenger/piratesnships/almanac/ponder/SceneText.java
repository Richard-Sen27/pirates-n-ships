package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The text of one Ponder scene as plain data (docs/almanac.md "Scenes"): its path, English title and lines. {@link #ALL}
 * holds every scene's text; {@link AlmanacPonderPlugin#registerSharedText} registers the lines and
 * {@link AlmanacPonderModule#gatherData} writes the lang.
 */
public record SceneText(String scene, String title, List<SceneLine> lines) {

    public SceneText {
        lines = List.copyOf(lines);
        for (SceneLine line : lines) {
            if (!line.scene().equals(scene)) {
                throw new IllegalArgumentException("line " + line.name() + " of " + line.scene() + " in " + scene);
            }
        }
    }

    static SceneText of(String scene, String title, SceneLine[] lines) {
        return new SceneText(scene, title, List.of(lines));
    }

    /** Every scene's text, in the order of {@link AlmanacScenes#ALL}. */
    public static final List<SceneText> ALL = List.of(
            of(CannonLoadingText.SCENE, CannonLoadingText.TITLE, CannonLoadingText.Line.values()),
            of(ShipSceneText.SHIP_ASSEMBLY, ShipSceneText.Assembly.TITLE, ShipSceneText.Assembly.values()),
            of(ShipSceneText.STEERING, ShipSceneText.Steering.TITLE, ShipSceneText.Steering.values()),
            of(ShipSceneText.SAILS_AND_WINCH, ShipSceneText.Sails.TITLE, ShipSceneText.Sails.values()),
            of(ShipSceneText.ANCHOR_AND_CAPSTAN, ShipSceneText.Anchor.TITLE, ShipSceneText.Anchor.values()),
            of(ShipSceneText.PUMPS_AND_FLOODING, ShipSceneText.Pumps.TITLE, ShipSceneText.Pumps.values()),
            of(ShipSceneText.HULL_REPAIR, ShipSceneText.Repair.TITLE, ShipSceneText.Repair.values()),
            of(ShipSceneText.MOORING, ShipSceneText.Mooring.TITLE, ShipSceneText.Mooring.values()),
            of(ShipSceneText.CARGO_AND_WEIGHT, ShipSceneText.Cargo.TITLE, ShipSceneText.Cargo.values()),
            of(CrewSceneText.CREW_ORDERS, CrewSceneText.Orders.TITLE, CrewSceneText.Orders.values()),
            of(CrewSceneText.BOARDING, CrewSceneText.Boarding.TITLE, CrewSceneText.Boarding.values()),
            of(CrewSceneText.FLAGS_AND_ALLEGIANCE, CrewSceneText.Flags.TITLE, CrewSceneText.Flags.values()),
            of(CrewSceneText.CROWS_NEST, CrewSceneText.Nest.TITLE, CrewSceneText.Nest.values()),
            of(CrewSceneText.SHIPS_BELL, CrewSceneText.Bell.TITLE, CrewSceneText.Bell.values()),
            of(CrewSceneText.GALLEY_AND_PROVISIONS, CrewSceneText.Galley.TITLE, CrewSceneText.Galley.values()),
            of(CrewSceneText.HAMMOCKS_AND_REST, CrewSceneText.Rest.TITLE, CrewSceneText.Rest.values()),
            of(CrewSceneText.SWIVEL_GUN, CrewSceneText.Swivel.TITLE, CrewSceneText.Swivel.values()));

    /** The scene's id, {@code pirates_n_ships:<scene>}. */
    public ResourceLocation id() {
        return Constants.id(scene);
    }

    /** The text of the scene with this path, if there is one. */
    public static Optional<SceneText> forScene(String scene) {
        return ALL.stream().filter(t -> t.scene.equals(scene)).findFirst();
    }

    /** Every line of every scene. */
    public static List<SceneLine> allLines() {
        List<SceneLine> out = new ArrayList<>();
        ALL.forEach(t -> out.addAll(t.lines));
        return out;
    }

    /** This scene's lang entries (English): the title first, then the lines in order. */
    public Map<String, String> lang() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(CannonLoadingText.titleLangKey(id()), title);
        for (SceneLine line : lines) {
            out.put(CannonLoadingText.sharedLangKey(line.key()), line.english());
        }
        return out;
    }

    /** Every scene's lang entries (English). */
    public static Map<String, String> allLang() {
        Map<String, String> out = new LinkedHashMap<>();
        ALL.forEach(t -> out.putAll(t.lang()));
        return out;
    }
}
