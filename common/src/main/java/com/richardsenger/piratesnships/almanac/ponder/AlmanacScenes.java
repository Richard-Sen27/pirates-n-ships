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

    /** Every scene, in the order the plugin registers them. */
    public static final List<Scene> ALL = List.of(
            new Scene(CANNON_LOADING, CANNON_ITEM, CANNON_LOADING));

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
