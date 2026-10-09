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
    static final Map<ResourceLocation, PonderStoryBoard> STORYBOARDS = Map.of(
            AlmanacScenes.CANNON_LOADING, CannonLoadingScene::program);

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
        for (CannonLoadingText.Line line : CannonLoadingText.Line.values()) {
            helper.registerSharedText(line.key().getPath(), line.english());
        }
    }
}
