package com.richardsenger.piratesnships.hazards.waves.client.surface;

import java.util.ArrayList;
import java.util.List;

/**
 * Which frame of an animated block sprite vanilla shows after a number of client ticks (WAV5): the rule of
 * {@code SpriteContents.Ticker} replayed from the sprite's animation metadata, so the sea surface's own copy of the
 * water texture ({@link SeaFrameTexture}) runs the same animation as the atlas. Pure: no client, no GL.
 *
 * <p>Vanilla's ticker starts on the list's first frame when the atlas is (re)loaded and, once per unpaused client tick
 * with a level, counts a sub-frame up; when the sub-frame reaches the frame's time it moves to the next frame (wrapping)
 * and restarts at 0. With {@code interpolate} the frames in between show a mix of the current and the next image,
 * weighted {@code 1 − subFrame / time} toward the current one. A sprite with at most one frame is not animated: vanilla
 * shows the image's first (top-left) frame.
 *
 * @param images      image index (in the sprite sheet) of each frame of the list
 * @param times       ticks each frame of the list lasts (all &gt; 0)
 * @param interpolate the metadata's {@code interpolate}
 */
public record WaterAnimation(int[] images, int[] times, boolean interpolate) {

    /** The state after some ticks: the frame of the list and the ticks spent in it. */
    public record Position(int frame, int subFrame) {
    }

    /** A sprite that does not animate. */
    public static final WaterAnimation STILL = new WaterAnimation(new int[] {0}, new int[] {1}, false);

    /**
     * Vanilla's frame list ({@code SpriteContents#createAnimatedTexture}): the metadata's explicit frames without the
     * invalid ones (time ≤ 0, index outside the sheet), or every image of the sheet in order with the default time when
     * the metadata lists none.
     *
     * @param imageCount  frames in the sprite sheet
     * @param defaultTime the metadata's {@code frametime}
     * @param explicit    the metadata's frames as {@code {index, time}} pairs (empty when it lists none)
     */
    public static WaterAnimation of(int imageCount, int defaultTime, List<int[]> explicit, boolean interpolate) {
        List<int[]> frames = new ArrayList<>();
        if (explicit.isEmpty()) {
            for (int i = 0; i < imageCount; i++) {
                frames.add(new int[] {i, defaultTime});
            }
        } else {
            for (int[] f : explicit) {
                if (f[1] > 0 && f[0] >= 0 && f[0] < imageCount) {
                    frames.add(f);
                }
            }
        }
        if (frames.size() <= 1) {
            return STILL;
        }
        int[] images = new int[frames.size()], times = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            images[i] = frames.get(i)[0];
            times[i] = frames.get(i)[1];
        }
        return new WaterAnimation(images, times, interpolate);
    }

    public boolean animated() {
        return images.length > 1;
    }

    /** Ticks of one full cycle. */
    public long period() {
        long sum = 0;
        for (int t : times) {
            sum += t;
        }
        return sum;
    }

    /** The ticker's state after {@code ticks} ticks since it started. */
    public Position at(long ticks) {
        if (!animated()) {
            return new Position(0, 0);
        }
        long t = Math.floorMod(ticks, period());
        for (int i = 0; i < times.length; i++) {
            if (t < times[i]) {
                return new Position(i, (int) t);
            }
            t -= times[i];
        }
        throw new IllegalStateException("unreachable");
    }

    /** Image (sheet index) shown at {@code p}, or blended toward {@link #nextImage} when {@link #blends}. */
    public int image(Position p) {
        return images[p.frame()];
    }

    /** Image of the frame after {@code p}'s. */
    public int nextImage(Position p) {
        return images[(p.frame() + 1) % images.length];
    }

    /** Whether vanilla shows a mix at {@code p} (interpolated, between two different images). */
    public boolean blends(Position p) {
        return interpolate && p.subFrame() > 0 && image(p) != nextImage(p);
    }

    /** Weight of {@link #image} in the mix at {@code p} (vanilla's {@code 1 − subFrame / time}). */
    public double weight(Position p) {
        return 1.0 - p.subFrame() / (double) times[p.frame()];
    }

    /** A key that changes exactly when the shown pixels change: the image, or the frame and sub-frame while blending. */
    public long key(Position p) {
        return blends(p) ? (1L << 62) | ((long) p.frame() << 32) | p.subFrame() : image(p);
    }
}
