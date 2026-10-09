package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * One line of a Ponder scene's text (docs/almanac.md "Scenes"), without any Ponder class: implemented by an enum per
 * scene ({@link CannonLoadingText.Line}, the enums of {@link ShipSceneText}), whose constant name is the line's name.
 *
 * <p>Every line is Ponder <i>shared text</i> with an explicit key, not Ponder's numbered {@code text_1, text_2, ...}
 * (those follow call order, so a reordered scene would swap translations).
 */
public interface SceneLine {

    /** The scene's path: its id is {@code pirates_n_ships:<scene>}. */
    String scene();

    /** The line's name (the enum constant). */
    String name();

    /** The English text (datagen writes it; Ponder shows it only in its editing mode, else the lang). */
    String english();

    /** Ponder's shared-text key: {@code pirates_n_ships:<scene>.<line>}. */
    default ResourceLocation key() {
        return Constants.id(scene() + "." + name().toLowerCase(Locale.ROOT));
    }
}
