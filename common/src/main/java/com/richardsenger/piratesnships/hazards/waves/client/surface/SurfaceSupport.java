package com.richardsenger.piratesnships.hazards.waves.client.surface;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * When the visible sea surface (WAV3) may run. Pure.
 *
 * <p>The surface needs vanilla's chunk mesher: {@code mixin.MixinLiquidBlockRenderer} hides vanilla's flat water top
 * inside {@code LiquidBlockRenderer#tesselate}. Renderers that replace the chunk mesher or the fluid renderer
 * ({@link #REPLACING_RENDERERS}) never call it, so with them the mesh would float above an unchanged flat sea; the
 * feature then stays off and the water is flat as before. Shader packs ({@code iris}, {@code oculus}) also replace the
 * world shaders the mesh relies on (water fog, Sable's dry-hull discard in {@code rendertype_translucent}).
 */
public final class SurfaceSupport {

    /** Mod ids that turn the surface off: Sodium, Embeddium, Iris, Oculus, VulkanMod. */
    public static final List<String> REPLACING_RENDERERS = List.of("sodium", "embeddium", "iris", "oculus", "vulkanmod");

    private SurfaceSupport() {
    }

    /** The first loaded mod of {@link #REPLACING_RENDERERS}, or empty when none is loaded. */
    public static Optional<String> blockingMod(Predicate<String> isLoaded) {
        for (String id : REPLACING_RENDERERS) {
            if (isLoaded.test(id)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a dimension has an open sea the surface may move: a sky and no ceiling (the overworld and its likes; not
     * the Nether, not the End).
     */
    public static boolean dimensionHasSea(boolean hasSkyLight, boolean hasCeiling) {
        return hasSkyLight && !hasCeiling;
    }

    /** The whole decision: the client toggle, no replacing renderer, a dimension with a sea. */
    public static boolean active(boolean configured, Optional<String> blockingMod, boolean dimensionHasSea) {
        return configured && blockingMod.isEmpty() && dimensionHasSea;
    }
}
