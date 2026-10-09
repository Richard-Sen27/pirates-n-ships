package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The text of the "loading and firing the cannon" scene ({@link CannonLoadingScene}), without any Ponder class: the
 * English lines, their keys and the lang entries datagen writes ({@link AlmanacPonderModule#gatherData}).
 *
 * <p>Ponder numbers a scene's plain {@code text(...)} lines {@code text_1, text_2, ...} by call order, so a reordered
 * scene would silently swap translations. Every line here is Ponder <i>shared text</i> instead (explicit keys,
 * registered by {@link AlmanacPonderPlugin#registerSharedText}); only the title is per scene. Ponder reads both from the
 * lang files unless its editing mode is on ({@code <ns>.ponder.shared.<path>}, {@code <ns>.ponder.<scene>.header}).
 */
public final class CannonLoadingText {

    /** The scene's path: its id is {@code pirates_n_ships:cannon_loading}, its schematic the same. */
    public static final String SCENE = "cannon_loading";
    /** The scene title (Ponder's per-scene {@code header}). */
    public static final String TITLE = "Loading and Firing the Cannon";

    /** The scene's lines, in the order they appear. */
    public enum Line implements SceneLine {
        GUN("The cannon is two blocks long: the muzzle points out through the gun port, the carriage sits behind it"),
        ORDER("Load it in order: powder first, then the shot, rammed home, then fire"),
        POWDER("Use gunpowder on the cannon to put the powder in"),
        BALL("Then use a cannonball on it (chain shot and grapeshot load the same way)"),
        RAM("The rammer seats the charge as you load: once the shot is in, the gun is ready"),
        AIM("Sneak and use the upper half of the gun to raise the barrel, the lower half to lower it"),
        FIRE("Use the cannon with an empty hand to fire"),
        RECOIL("The recoil pushes your ship back, and the barrel needs a moment before it can be loaded again");

        private final String english;

        Line(String english) {
            this.english = english;
        }

        @Override
        public String scene() {
            return SCENE;
        }

        @Override
        public String english() {
            return english;
        }

        /** Ponder's shared-text key: {@code pirates_n_ships:cannon_loading.<line>}. */
        @Override
        public ResourceLocation key() {
            return Constants.id(SCENE + "." + name().toLowerCase(Locale.ROOT));
        }
    }

    private CannonLoadingText() {
    }

    /** The lang key Ponder reads for a shared text (its {@code PonderLocalization.langKeyForShared}). */
    public static String sharedLangKey(ResourceLocation key) {
        return key.getNamespace() + ".ponder.shared." + key.getPath();
    }

    /** The lang key Ponder reads for a scene's title (its {@code langKeyForSpecific(sceneId, "header")}). */
    public static String titleLangKey(ResourceLocation sceneId) {
        return sceneId.getNamespace() + ".ponder." + sceneId.getPath() + ".header";
    }

    /** Every lang entry of the scene (English), title first, then the lines in order. */
    public static Map<String, String> lang() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(titleLangKey(Constants.id(SCENE)), TITLE);
        for (Line line : Line.values()) {
            out.put(sharedLangKey(line.key()), line.english());
        }
        return out;
    }
}
