package com.richardsenger.piratesnships.almanac.ponder;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.createmod.ponder.foundation.instruction.AnimateElementInstruction;
import net.createmod.ponder.foundation.instruction.AnimateWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.ReplaceBlocksInstruction;
import net.createmod.ponder.foundation.instruction.RotateSceneInstruction;
import net.createmod.ponder.foundation.instruction.ShowInputInstruction;
import net.createmod.ponder.foundation.instruction.TextInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.instruction.WorldModifyInstruction;
import net.createmod.ponder.foundation.registration.DefaultPonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PND3's view check (docs/design.md §3.4 "Ponder scenes, first look"): every scene of {@link AlmanacScenes#ALL} replayed
 * headless at Ponder's default camera in a 1920×1080 window at GUI scale 3 ({@link SceneView}).
 *
 * <p>The replay walks the compiled schedule in order, the scene's clock advancing by every blocking instruction, and
 * keeps the scene's blocks the way Ponder shows them: the schematic's blocks, visible once a {@code showSection} has
 * shown them and gone once hidden ({@link SceneKit#hide}, an independent section faded out), changed by every
 * {@code setBlock}/{@code modifyBlock}. Our own blocks are air here (no mod registry headless), so only vanilla blocks
 * hide anything, which is what the hull walls are made of. At every moment the scene acts:
 * <ul>
 *   <li>every point of interest, the target of a text ({@code pointAt}), a click ({@code showControls}), a block the
 *       scene changes (any one block of a changed selection) and every {@link SceneKit#focus}, must be visible from the
 *       camera: no solid shown block on the line from the point toward the watcher;</li>
 *   <li>every text box, laid out as {@code TextWindowElement} lays it out, must lie inside the 640×360 window.</li>
 * </ul>
 * Moving and rotating sections are taken at their start position (the ships move a few blocks at most).
 */
class SceneViewTest {

    private static final String MOD = "pirates_n_ships";
    private static final double REACH = 64;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theDefaultCameraLooksDownOnTheScene() {
        PonderScene scene = compile(AlmanacScenes.ALL.get(0));
        Vec3 toward = SceneView.towardViewer(scene);
        // Ponder's start view: x -35° (from above), y 145°
        assertTrue(toward.y > 0.5 && toward.y < 0.65, "35° above the horizon: " + toward);
        assertEquals(1.0, toward.length(), 1e-6);
        // a point straight toward the watcher lands on the same pixel
        Vec3 p = new Vec3(3, 2, 3);
        var a = SceneView.project(scene, p);
        var b = SceneView.project(scene, p.add(toward.scale(5)));
        assertEquals(a.x, b.x, 0.05);
        assertEquals(a.y, b.y, 0.05);
    }

    @Test
    void theCameraStandsNorthWestOfTheScene() {
        // seen from the north-west: the scene's north-west corner is the lowest on the screen, the far south-east one the
        // highest, east goes up to the left and south up to the right
        PonderScene scene = compile(AlmanacScenes.ALL.get(4));
        var nw = SceneView.project(scene, new Vec3(0, 0, 0));
        var se = SceneView.project(scene, new Vec3(9, 0, 9));
        var ne = SceneView.project(scene, new Vec3(9, 0, 0));
        var sw = SceneView.project(scene, new Vec3(0, 0, 9));
        assertTrue(nw.y > ne.y && nw.y > sw.y && se.y < ne.y && se.y < sw.y);
        assertTrue(ne.x < nw.x && sw.x > nw.x);
        Vec3 toward = SceneView.towardViewer(scene);
        assertTrue(toward.x < 0 && toward.z < 0, "toward the north-west: " + toward);
    }

    @Test
    void theTextWrapsLikeVanilla() {
        assertEquals(List.of("aaa bbb", "ccc"), SceneView.wrap("aaa bbb ccc", SceneView.width("aaa bbb")));
        assertEquals(1, SceneView.wrap("short", 180).size());
        assertEquals(21, SceneView.width("Hold"));
    }

    @Test
    void everySceneShowsItsActionAndKeepsItsTextOnScreen() throws Exception {
        StringBuilder all = new StringBuilder();
        int failing = 0;
        for (AlmanacScenes.Scene s : AlmanacScenes.ALL) {
            List<String> problems = check(s);
            if (!problems.isEmpty()) {
                failing++;
                all.append("\n").append(s.id().getPath()).append(":");
                problems.forEach(p -> all.append("\n  ").append(p));
            }
        }
        System.out.println("SceneView: " + failing + " of " + AlmanacScenes.ALL.size() + " scenes fail" + all
                + "\nchecked:" + STATS);
        assertEquals("", all.toString());
    }

    // ---------------------------------------------------------------------------------------------------- the replay

    private static List<String> check(AlmanacScenes.Scene s) throws Exception {
        PonderScene scene = compile(s);
        Picture picture = new Picture(schematic(s.schematic()));
        Vec3 toward = SceneView.towardViewer(scene);
        Predicate<BlockPos> solid = picture::solid;
        Map<ResourceLocation, SceneLine> lines = new HashMap<>();
        for (SceneLine line : SceneText.allLines()) {
            lines.put(line.key(), line);
        }

        List<String> problems = new ArrayList<>();
        int points = 0;
        int boxes = 0;
        int t = 0;
        for (PonderInstruction i : schedule(scene)) {
            if (i instanceof RotateSceneInstruction) {
                problems.add("t " + t + ": the camera turns (not modelled by the check)");
            } else if (i instanceof DisplayWorldSectionInstruction d) {
                List<BlockPos> cells = cells(get(DisplayWorldSectionInstruction.class, d, "initialSelection"));
                Object mergeOnto = get(DisplayWorldSectionInstruction.class, d, "mergeOnto");
                Object link = get(FadeIntoSceneInstruction.class, d, "elementLink");
                if (mergeOnto == null && link != null) {
                    picture.sections.put(link, new Section(cells));
                } else {
                    picture.base.addAll(cells);
                }
            } else if (i instanceof FadeOutOfSceneInstruction<?> f) {
                Section section = picture.sections.get(get(FadeOutOfSceneInstruction.class, f, "link"));
                if (section != null) {
                    section.visible = false;
                }
            } else if (i instanceof AnimateWorldSectionInstruction move) {
                Section section = picture.sections.get(get(AnimateElementInstruction.class, move, "link"));
                Vec3 delta = get(AnimateElementInstruction.class, move, "totalDelta");
                if (section != null && isMove(move)) {
                    section.offset = section.offset.offset((int) Math.round(delta.x), (int) Math.round(delta.y),
                            (int) Math.round(delta.z));
                }
            } else if (i instanceof SceneKit.Hidden h) {
                List<BlockPos> cells = cells(h.selection);
                cells.forEach(picture.base::remove);
                picture.sections.values().forEach(sec -> sec.cells.removeAll(cells));
            } else if (i instanceof ReplaceBlocksInstruction r) {
                List<BlockPos> cells = cells(get(WorldModifyInstruction.class, r, "selection"));
                UnaryOperator<BlockState> to = get(ReplaceBlocksInstruction.class, r, "stateToUse");
                boolean replaceAir = get(ReplaceBlocksInstruction.class, r, "replaceAir");
                for (BlockPos p : cells) {
                    BlockState prev = picture.blocks.getOrDefault(p, Blocks.AIR.defaultBlockState());
                    if (!replaceAir && prev.isAir()) {
                        continue;
                    }
                    try {
                        picture.blocks.put(p, to.apply(prev));
                    } catch (Throwable e) {
                        // a state of one of our blocks (not registered headless): keep it as it was
                    }
                }
                List<BlockPos> seen = cells.stream().filter(picture::shown).map(picture::display).toList();
                points += seen.isEmpty() ? 0 : 1;
                if (!seen.isEmpty() && seen.stream().noneMatch(p -> visible(p, toward, solid))) {
                    BlockPos p = seen.get(0);
                    problems.add("t " + t + ": the changed block " + p.toShortString() + " is hidden behind "
                            + picture.describe(SceneView.blocker(Vec3.atCenterOf(p), toward, solid, REACH)));
                }
            } else if (i instanceof ShowInputInstruction in) {
                InputWindowElement element = get(ShowInputInstruction.class, in, "element");
                Vec3 at = get(InputWindowElement.class, element, "sceneSpace");
                points++;
                boxes++;
                point(problems, t, "the click at " + fmt(at), at, toward, picture, scene);
                // our items read as air headless, so "an item was given" is any stack but the EMPTY default
                SceneView.Box box = SceneView.inputBox(scene, at, get(InputWindowElement.class, element, "direction"),
                        get(InputWindowElement.class, element, "icon") != null,
                        get(InputWindowElement.class, element, "item") != ItemStack.EMPTY,
                        get(InputWindowElement.class, element, "key") != null);
                if (!box.onScreen()) {
                    problems.add("t " + t + ": the click box at " + fmt(at) + " leaves the screen " + box);
                }
            } else if (i instanceof SceneKit.Focus f) {
                points++;
                if (f.block != null) {
                    if (!visible(f.block, toward, solid)) {
                        problems.add("t " + t + ": the focus block " + f.block.toShortString() + " is hidden behind "
                                + picture.describe(SceneView.blocker(Vec3.atCenterOf(f.block), toward, solid, REACH)));
                    }
                } else {
                    point(problems, t, "the focus " + fmt(f.point), f.point, toward, picture, scene);
                }
            } else if (i instanceof TextInstruction text) {
                TextWindowElement element = get(TextInstruction.class, text, "element");
                Vec3 vec = get(TextWindowElement.class, element, "vec");
                int y = get(TextWindowElement.class, element, "y");
                boolean near = get(TextWindowElement.class, element, "nearScene");
                SceneLine line = lines.get(sharedKey(element));
                assertNotNull(line, s.id() + ": a text at t " + t + " is not one of our shared lines");
                String name = ((Enum<?>) line).getDeclaringClass().getSimpleName() + "." + ((Enum<?>) line).name();
                SceneView.Box box = SceneView.textBox(scene, vec, y, near, line.english());
                boxes++;
                points += vec == null ? 0 : 1;
                if (!box.onScreen()) {
                    problems.add("t " + t + ": the text " + name + " leaves the screen " + box
                            + (vec == null ? " (independent " + y + ")" : " (pointing at " + fmt(vec) + ")"));
                }
                if (vec != null) {
                    point(problems, t, "the target of " + name + " " + fmt(vec), vec, toward, picture, scene);
                }
            }
            if (i instanceof TickingInstruction ticking && ticking.isBlocking()) {
                t += (int) get(TickingInstruction.class, ticking, "totalTicks");
            }
        }
        STATS.append(String.format("%n  %-22s %3d points, %2d text and click boxes, %4d ticks", s.id().getPath(), points, boxes, t));
        return problems;
    }

    private static final StringBuilder STATS = new StringBuilder();

    /** A section shown on its own (a ship that moves): its cells, where it has been moved to, whether it is shown. */
    private static final class Section {
        final List<BlockPos> cells;
        BlockPos offset = BlockPos.ZERO;
        boolean visible = true;

        Section(List<BlockPos> cells) {
            this.cells = new ArrayList<>(cells);
        }
    }

    /** What the watcher sees: the level's blocks, the cells the base section shows, and the independent sections. */
    private static final class Picture {
        final Map<BlockPos, BlockState> blocks;
        final Set<BlockPos> base = new HashSet<>();
        final Map<Object, Section> sections = new IdentityHashMap<>();

        Picture(Map<BlockPos, BlockState> blocks) {
            this.blocks = blocks;
        }

        private BlockState state(BlockPos cell) {
            return blocks.getOrDefault(cell, Blocks.AIR.defaultBlockState());
        }

        /** The level cell drawn at {@code display}, or null when nothing shown is drawn there. */
        BlockPos drawn(BlockPos display) {
            for (Section sec : sections.values()) {
                BlockPos cell = display.subtract(sec.offset);
                if (sec.visible && sec.cells.contains(cell)) {
                    return cell;
                }
            }
            return base.contains(display) ? display : null;
        }

        boolean solid(BlockPos display) {
            BlockPos cell = drawn(display);
            return cell != null && state(cell).isSolidRender(EmptyBlockGetter.INSTANCE, cell);
        }

        boolean shown(BlockPos cell) {
            return base.contains(cell) || sections.values().stream().anyMatch(sec -> sec.visible && sec.cells.contains(cell));
        }

        BlockPos display(BlockPos cell) {
            for (Section sec : sections.values()) {
                if (sec.visible && sec.cells.contains(cell)) {
                    return cell.offset(sec.offset);
                }
            }
            return cell;
        }

        String describe(BlockPos display) {
            if (display == null) {
                return "nothing (partly)";
            }
            BlockPos cell = drawn(display);
            return BuiltInRegistries.BLOCK.getKey(state(cell).getBlock()).getPath() + " at " + display.toShortString();
        }
    }

    /** Whether a section animation moves (rather than turns) its section: its getter read on a probe element. */
    private static boolean isMove(AnimateWorldSectionInstruction instruction) throws ReflectiveOperationException {
        java.util.function.Function<WorldSectionElement, Vec3> getter =
                get(AnimateElementInstruction.class, instruction, "getter");
        WorldSectionElementImpl probe = new WorldSectionElementImpl();
        probe.setAnimatedOffset(new Vec3(1, 2, 3), true);
        probe.setAnimatedRotation(new Vec3(4, 5, 6), true);
        return getter.apply(probe).equals(new Vec3(1, 2, 3));
    }

    private static void point(List<String> problems, int t, String what, Vec3 at, Vec3 toward, Picture picture,
                              PonderScene scene) {
        var onScreen = SceneView.project(scene, at);
        if (onScreen.x < 0 || onScreen.y < 0 || onScreen.x > SceneView.WIDTH || onScreen.y > SceneView.HEIGHT) {
            problems.add(String.format("t %d: %s is off the screen at (%.0f, %.0f)", t, what, onScreen.x, onScreen.y));
        }
        BlockPos blocker = SceneView.blocker(at, toward, picture::solid, REACH);
        if (blocker != null) {
            problems.add("t " + t + ": " + what + " is hidden behind " + picture.describe(blocker));
        }
    }

    private static boolean visible(BlockPos pos, Vec3 toward, Predicate<BlockPos> solid) {
        return SceneView.samples(pos, toward).stream().anyMatch(p -> SceneView.blocker(p, toward, solid, REACH) == null);
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static List<BlockPos> cells(Selection selection) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : selection) {
            out.add(p.immutable());
        }
        return out;
    }

    /** The key of a window's shared text: the one {@code ResourceLocation} its text supplier captured. */
    private static ResourceLocation sharedKey(TextWindowElement element) throws ReflectiveOperationException {
        Object getter = get(TextWindowElement.class, element, "textGetter");
        for (Field f : getter.getClass().getDeclaredFields()) {
            if (f.getType() == ResourceLocation.class) {
                f.setAccessible(true);
                return (ResourceLocation) f.get(getter);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Class<?> owner, Object o, String field) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    // ------------------------------------------------------------------------------------ the scene and its blocks

    /**
     * The scene built the way {@code PonderSceneRegistry.compileScene} builds it, but with a building util that knows the
     * schematic's bounds (headless there is no level, and Ponder's own util would make {@code layersFrom} and every
     * other bounds-based selection empty).
     */
    static PonderScene compile(AlmanacScenes.Scene s) {
        PonderSceneRegistry registry = new PonderSceneRegistry(new PonderLocalization());
        new AlmanacPonderPlugin().registerScenes(new DefaultPonderSceneRegistrationHelper(MOD, registry));
        StoryBoardEntry entry = registry.getRegisteredEntries().stream().map(Map.Entry::getValue)
                .filter(e -> e.getSchematicLocation().equals(s.schematic())).findFirst().orElseThrow();
        PonderScene scene = new PonderScene(null, new PonderLocalization(), entry.getNamespace(), entry.getComponent(),
                entry.getTags(), entry.getOrderingEntries());
        try {
            BlockPos size = size(s.schematic());
            var ctor = Class.forName("net.createmod.ponder.foundation.PonderSceneBuildingUtil")
                    .getDeclaredConstructor(BoundingBox.class);
            ctor.setAccessible(true);
            SceneBuildingUtil util = (SceneBuildingUtil) ctor.newInstance(
                    new BoundingBox(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1));
            entry.getBoard().program(scene.builder(), util);
        } catch (ReflectiveOperationException | IOException e) {
            throw new AssertionError(e);
        }
        return scene;
    }

    @SuppressWarnings("unchecked")
    private static List<PonderInstruction> schedule(PonderScene scene) throws ReflectiveOperationException {
        return (List<PonderInstruction>) get(PonderScene.class, scene, "schedule");
    }

    private static CompoundTag nbt(ResourceLocation id) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("common/src/main/resources"))) {
            root = root.getParent();
        }
        assertNotNull(root, "repository root not found");
        Path file = root.resolve("common/src/main/resources/assets/" + id.getNamespace() + "/ponder/" + id.getPath() + ".nbt");
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    private static BlockPos size(ResourceLocation id) throws IOException {
        ListTag size = nbt(id).getList("size", Tag.TAG_INT);
        return new BlockPos(size.getInt(0), size.getInt(1), size.getInt(2));
    }

    /** The scene's schematic as Ponder places it (at the origin); our blocks read as air headless. */
    static Map<BlockPos, BlockState> schematic(ResourceLocation id) throws IOException {
        CompoundTag nbt = nbt(id);
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }
        Map<BlockPos, BlockState> out = new HashMap<>();
        ListTag list = nbt.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            out.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states.get(b.getInt("state")));
        }
        return out;
    }
}
