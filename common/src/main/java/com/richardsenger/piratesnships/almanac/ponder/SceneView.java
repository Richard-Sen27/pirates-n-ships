package com.richardsenger.piratesnships.almanac.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What a watcher sees of one of our scenes at Ponder's default view (PND3, docs/design.md §3.4 "Ponder scenes, first
 * look"): where a scene point lands on the screen, which way the camera looks, where a text box is drawn and whether a
 * solid block stands between the camera and a point. Used headless by {@code SceneViewTest} so that a scene whose
 * action hides behind a wall, or whose text leaves the screen, fails a test; nothing here runs in the game.
 *
 * <p>The window is a 1920×1080 window at GUI scale 3, {@value #WIDTH}×{@value #HEIGHT} GUI pixels. Projection and
 * camera are Ponder's own: {@code PonderScene.SceneTransform} of the compiled scene (its untouched start values, x -35°
 * and y 145°, the scene's {@code scaleSceneView}, base plate and y offset) after {@code updateScreenParams(width,
 * height, 0)}, as {@code PonderUI.renderScene} sets it for the scene on screen. The text box follows
 * {@code TextWindowElement.render} (Ponder 1.0.87, mc1.21.1/dev) line by line, see {@link #textBox}; the font widths
 * are vanilla's default ASCII glyph advances, see {@link #advance}.
 */
final class SceneView {

    /** GUI pixels of a 1920×1080 window at GUI scale 3. */
    static final int WIDTH = 640;
    static final int HEIGHT = 360;
    /** {@code TextWindowElement}: a line is 9 pixels high, a box at most 180 wide. */
    static final int LINE_HEIGHT = 9;
    static final int MAX_TEXT_WIDTH = 180;
    /**
     * {@code BoxElement} draws its frame {@code borderOffset} (2) plus 2 pixels of borders outside the bounds it is
     * given.
     */
    static final int BOX_FRAME = 4;

    private SceneView() {
    }

    /** Sets the scene's transform to the default view of a scene on the screen (no slide, no drag). */
    static void defaultView(PonderScene scene) {
        scene.getTransform().updateScreenParams(WIDTH, HEIGHT, 0);
    }

    /** Where the scene point {@code p} is drawn, in GUI pixels (x right, y down), at the default view. */
    static Vec2 project(PonderScene scene, Vec3 p) {
        defaultView(scene);
        return scene.getTransform().sceneToScreen(p, 0f);
    }

    /**
     * The unit scene-space direction from the scene toward the watcher: Ponder draws orthographically with GUI z toward
     * the watcher, so it is the difference of two screen points of the same pixel at different depths
     * ({@code SceneTransform.screenToScene}).
     */
    static Vec3 towardViewer(PonderScene scene) {
        defaultView(scene);
        Vec3 near = scene.getTransform().screenToScene(WIDTH / 2.0, HEIGHT / 2.0, 1000, 0f);
        Vec3 far = scene.getTransform().screenToScene(WIDTH / 2.0, HEIGHT / 2.0, 0, 0f);
        return near.subtract(far).normalize();
    }

    /** A text box on the screen in GUI pixels, frame included; {@code lines} is its wrapped line count. */
    record Box(float x0, float y0, float x1, float y1, int lines) {

        /** Whether the whole box lies inside the window. */
        boolean onScreen() {
            return x0 >= 0 && y0 >= 0 && x1 <= WIDTH && y1 <= HEIGHT;
        }

        @Override
        public String toString() {
            return String.format("[%.0f..%.0f x %.0f..%.0f, %d lines]", x0, x1, y0, y1, lines);
        }
    }

    /**
     * The box Ponder draws for a text window, mirroring {@code TextWindowElement.render} once the camera has settled:
     * the anchor is the projected {@code pointAt} target, or for a window without one the point
     * {@code (width / 2, (height - 200) / 2 + independentY - 8)}; the box's left text edge is
     * {@code width · lerp(d², 6/8, 5/8)} with {@code d = (height / 2 - anchorY - 10) / 100}, pulled to at most
     * {@code anchorX + 50} by {@code placeNearTarget}; the text wraps at {@code min(width - left, 180)}; the box is
     * drawn at {@code (left - 10, anchorY + 3)}, as wide as its longest line and 9 pixels per line (minus one), with
     * {@link #BOX_FRAME} pixels of frame around it.
     */
    static Box textBox(PonderScene scene, @Nullable Vec3 pointAt, int independentY, boolean nearTarget, String text) {
        Vec2 anchor = pointAt != null ? project(scene, pointAt)
                : new Vec2(WIDTH / 2f, (HEIGHT - 200) / 2f + independentY - 8);
        float pY = (int) anchor.y;
        float yDiff = (HEIGHT / 2f - anchor.y - 10) / 100f;
        float targetX = WIDTH * Mth.lerp(yDiff * yDiff, 6f / 8, 5f / 8);
        if (nearTarget) {
            targetX = Math.min(targetX, anchor.x + 50);
        }
        targetX = (int) targetX;
        int textWidth = (int) Math.min(WIDTH - targetX, MAX_TEXT_WIDTH);
        List<String> lines = wrap(text, textWidth);
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, width(line));
        }
        int boxHeight = lines.size() * LINE_HEIGHT;
        float left = targetX - 10;
        float top = pY + 3;
        return new Box(left - BOX_FRAME, top - BOX_FRAME, left + boxWidth + BOX_FRAME, top + boxHeight - 1 + BOX_FRAME,
                lines.size());
    }

    /**
     * The speech box Ponder draws for a click ({@code showControls}), mirroring {@code InputWindowElement.render} and
     * {@code PonderUI.renderSpeechBox}: 24 pixels wide for the mouse icon and 24 for the item, plus the width of the
     * "Sneak +" / "Ctrl +" key text, 24 high, set off from the point by its 8-pixel divot and 2 pixels more on the
     * side {@code pointing} says (DOWN: the box above the point), with {@link #BOX_FRAME} pixels of frame.
     */
    static Box inputBox(PonderScene scene, Vec3 at, net.createmod.catnip.math.Pointing pointing, boolean icon,
                        boolean item, boolean key) {
        Vec2 p = project(scene, at);
        int w = (icon ? 24 : 0) + (item ? 24 : 0) + (key ? width("Sneak + ") : 0);
        int h = icon || item ? 24 : LINE_HEIGHT;
        int off = 8 + 2;
        float x0;
        float y0;
        switch (pointing) {
            case LEFT -> {
                x0 = p.x + off;
                y0 = p.y - h / 2f;
            }
            case RIGHT -> {
                x0 = p.x - off - w;
                y0 = p.y - h / 2f;
            }
            case UP -> {
                x0 = p.x - w / 2f;
                y0 = p.y + off;
            }
            default -> {
                x0 = p.x - w / 2f;
                y0 = p.y - off - h;
            }
        }
        return new Box(x0 - BOX_FRAME, y0 - BOX_FRAME, x0 + w + BOX_FRAME, y0 + h + BOX_FRAME, 1);
    }

    /**
     * The advance of a character in vanilla's default font: the glyph widths of {@code font/ascii.png} plus one pixel
     * of spacing (6 for most letters and digits); characters outside ASCII are taken a little wide, 6 for Latin
     * letters and 8 for punctuation such as the ellipsis or the dash.
     */
    static int advance(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '\'', ',', '.', ':', ';', '|', 'i' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> c < 0x2000 ? 6 : 8;
        };
    }

    /** The width of {@code s} in GUI pixels. */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += advance(s.charAt(i));
        }
        return w;
    }

    /**
     * {@code s} broken into lines at most {@code maxWidth} wide the way vanilla's {@code StringSplitter.splitLines}
     * does: at the last space before the line would overflow (the space is dropped), inside a word only when one word
     * alone is too wide, and at every newline.
     */
    static List<String> wrap(String s, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String paragraph : s.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate) <= maxWidth || line.isEmpty() && word.isEmpty()) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (width(rest) > maxWidth && rest.length() > 1) {
                    int n = 1;
                    while (n < rest.length() && width(rest.substring(0, n + 1)) <= maxWidth) {
                        n++;
                    }
                    out.add(rest.substring(0, n));
                    rest = rest.substring(n);
                }
                line.append(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    /**
     * The first solid block between {@code point} and the watcher, or null when the point can be seen. The ray starts
     * at the point and runs toward the watcher ({@code toward}, a unit vector) through every block it crosses; the
     * block the point lies in or on, seen from the camera's side (the cell just behind the point), is the target
     * itself and is skipped, so a point on a block's face is hidden only by what stands in front of that face.
     */
    static @Nullable BlockPos blocker(Vec3 point, Vec3 toward, Predicate<BlockPos> solid, double reach) {
        Vec3 start = point.subtract(toward.scale(1e-4));
        BlockPos target = BlockPos.containing(start);
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int stepX = toward.x > 0 ? 1 : toward.x < 0 ? -1 : 0;
        int stepY = toward.y > 0 ? 1 : toward.y < 0 ? -1 : 0;
        int stepZ = toward.z > 0 ? 1 : toward.z < 0 ? -1 : 0;
        double tMaxX = boundary(start.x, toward.x);
        double tMaxY = boundary(start.y, toward.y);
        double tMaxZ = boundary(start.z, toward.z);
        double dX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / toward.x);
        double dY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / toward.y);
        double dZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / toward.z);
        while (true) {
            double t;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                t = tMaxX;
                x += stepX;
                tMaxX += dX;
            } else if (tMaxY <= tMaxZ) {
                t = tMaxY;
                y += stepY;
                tMaxY += dY;
            } else {
                t = tMaxZ;
                z += stepZ;
                tMaxZ += dZ;
            }
            if (t > reach) {
                return null;
            }
            BlockPos cell = new BlockPos(x, y, z);
            if (!cell.equals(target) && solid.test(cell)) {
                return cell;
            }
        }
    }

    private static double boundary(double s, double d) {
        if (d > 0) {
            return (Math.floor(s) + 1 - s) / d;
        }
        if (d < 0) {
            return (s - Math.floor(s)) / -d;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * The points that show a block to the watcher: its centre and the centres of the faces that turn toward the
     * camera. A block counts as seen when any of them is free ({@link #blocker} null).
     */
    static List<Vec3> samples(BlockPos pos, Vec3 toward) {
        Vec3 c = Vec3.atCenterOf(pos);
        List<Vec3> out = new ArrayList<>();
        out.add(c);
        if (toward.x != 0) out.add(c.add(Math.signum(toward.x) * 0.5, 0, 0));
        if (toward.y != 0) out.add(c.add(0, Math.signum(toward.y) * 0.5, 0));
        if (toward.z != 0) out.add(c.add(0, 0, Math.signum(toward.z) * 0.5));
        return out;
    }
}
