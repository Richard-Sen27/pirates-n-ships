package com.richardsenger.piratesnships.core.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-made Blockbench models (design.md §4.8) under {@code src/main/resources/assets/pirates_n_ships/models}
 * (run from {@code common/}): each one parses with vanilla's model loader, keeps its elements inside the vanilla limits
 * (coordinates −16..32, one rotation axis with an angle of 0, ±22.5 or ±45 degrees) and resolves every face texture
 * through its own texture map, so nothing renders as the missing texture.
 */
class HandMadeModelsTest {

    static final Path MAIN_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models");
    private static final Set<Float> ANGLES = Set.of(0f, 22.5f, -22.5f, 45f, -45f);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** Every hand-made model by name; a new Blockbench model is added here, a missing or stray file fails. */
    static final List<String> BLOCK_MODELS = List.of("bilge_pump", "brig_bars", "brig_bars_post", "brig_bars_side",
            "brig_bars_side_alt", "brig_door_bottom_left", "brig_door_bottom_left_locked",
            "brig_door_bottom_left_open_locked", "brig_door_bottom_right", "brig_door_bottom_right_locked",
            "brig_door_bottom_right_open_locked", "brig_door_top_left", "brig_door_top_right", "cannon",
            "cannon_loaded", "cannon_powder", "capstan", "cargo_barrel", "cargo_crate", "cleat", "figurehead_eagle",
            "figurehead_lion", "figurehead_mermaid", "figurehead_skull", "flagpole", "harbor_desk", "helm", "helm_item", "helm_wheel", "hull_patch", "nameplate",
            "notice_board", "pantry", "sail_winch", "sea_chest", "swivel_gun", "swivel_gun_barrel", "swivel_gun_barrel_loaded", "swivel_gun_yoke",
            "water_barrel", "water_barrel_fill0", "water_barrel_fill1", "water_barrel_fill2",
            "water_barrel_fill3", "yard");
    static final List<String> ITEM_MODELS = List.of("bandana", "bounty_proof", "brig_door", "brig_key", "cannonball", "captains_whistle", "chart", "cloth", "cutlass", "doubloon",
            "grappling_hook", "hardtack", "hull_patch", "lead_shot", "lime", "musket", "musket_loaded", "navy_hat", "officer_hat", "pirate_hat", "pistol",
            "pistol_loaded", "rapier", "rope", "rum", "saber", "salt_pork",
            "salted_fish", "shackles", "spices", "tobacco");

    /** The display slots a hand-made item model copies from vanilla's {@code item/handheld} and {@code item/generated}. */
    private static final List<String> ITEM_DISPLAY_SLOTS = List.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "ground", "head", "fixed");

    @Test
    void everyHandMadeModelIsListedByName() throws IOException {
        assertEquals(BLOCK_MODELS, names("block"), "hand-made block models");
        assertEquals(ITEM_MODELS, names("item"), "hand-made item models");
    }

    private static List<String> names(String folder) throws IOException {
        return jsonFiles(MAIN_MODELS.resolve(folder)).stream()
                .map(p -> p.getFileName().toString().replace(".json", "")).sorted().toList();
    }

    /**
     * A hand-made item model must not inherit from {@code item/generated} or {@code item/handheld}: vanilla's
     * {@code ModelBakery} bakes every model whose root parent is {@code builtin/generated} from its {@code layer0}
     * sprite and ignores the elements. So the model has no parent and carries the vanilla handheld display transforms
     * itself, plus {@code gui_light: front} like a flat item.
     */
    @Test
    void handMadeItemModelsBringTheirOwnHandheldTransforms() throws IOException {
        for (Path file : jsonFiles(MAIN_MODELS.resolve("item"))) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertFalse(json.has("parent"), file + ": a parent would root in builtin/generated and drop the elements");
            assertEquals("front", json.get("gui_light").getAsString(), file + ": gui_light");
            JsonObject display = json.getAsJsonObject("display");
            assertNotNull(display, file + ": no display transforms");
            for (String slot : ITEM_DISPLAY_SLOTS) {
                assertTrue(display.has(slot), file + ": missing display slot " + slot);
            }
        }
    }

    /**
     * HELM1: the helm is split into the static pedestal ({@code helm}, the block model) and the wheel
     * ({@code helm_wheel}, turned by {@code HelmWheelRenderer}); {@code helm_item} is both for the item. The item model
     * holds exactly the wheel's elements followed by the pedestal's, so the three files stay in step with each other
     * and with the {@code wheel} and {@code pedestal} groups of {@code art/models/helm.bbmodel}.
     */
    @Test
    void helmSplitsIntoPedestalAndWheel() throws IOException {
        JsonArray pedestal = blockModel("helm").getAsJsonArray("elements");
        JsonArray wheel = blockModel("helm_wheel").getAsJsonArray("elements");
        JsonArray item = blockModel("helm_item").getAsJsonArray("elements");
        JsonArray both = new JsonArray();
        both.addAll(wheel);
        both.addAll(pedestal);
        assertEquals(item, both, "helm_item is not helm_wheel + helm");
        assertTrue(wheel.size() > 0 && pedestal.size() > 0, "empty helm part");
    }

    private static JsonObject blockModel(String name) throws IOException {
        return JsonParser.parseString(Files.readString(MAIN_MODELS.resolve("block").resolve(name + ".json"))).getAsJsonObject();
    }

    /** The guns that show a cocked-hammer variant while loaded (P6), and the lock parts the variant moves or adds. */
    static final List<String> LOADED_VARIANTS = List.of("musket", "pistol");
    private static final Set<String> LOCK_PARTS = Set.of("cock", "cock_jaw", "flint", "frizzen", "pan_cover");

    /**
     * The pistol and the musket switch to their {@code _loaded} model through an item model override on the
     * {@code pirates_n_ships:loaded} property ({@code FirearmsClient}). The variant keeps the base's display entries
     * verbatim (the F8h grip) and its textures, and differs only in the lock: every other element is identical.
     */
    @Test
    void loadedGunsOverrideToTheirCockedVariant() throws IOException {
        for (String gun : LOADED_VARIANTS) {
            JsonObject base = itemModel(gun);
            JsonObject loaded = itemModel(gun + "_loaded");
            JsonArray overrides = base.getAsJsonArray("overrides");
            assertNotNull(overrides, gun + ": no overrides");
            // the musket also shows a loaded grappling hook (GR3), through a second override after this one
            assertEquals(gun.equals("musket") ? 2 : 1, overrides.size(), gun + ": overrides");
            JsonObject override = overrides.get(0).getAsJsonObject();
            JsonObject predicate = override.getAsJsonObject("predicate");
            assertEquals(Set.of("pirates_n_ships:loaded"), predicate.keySet(), gun + ": predicate");
            assertEquals(1f, predicate.get("pirates_n_ships:loaded").getAsFloat(), gun + ": predicate value");
            assertEquals("pirates_n_ships:item/" + gun + "_loaded", override.get("model").getAsString(), gun + ": override model");
            assertFalse(loaded.has("overrides"), gun + "_loaded: overrides of an override target are never read");
            assertEquals(base.get("display"), loaded.get("display"), gun + "_loaded: display entries differ from the base");
            assertEquals(base.get("textures"), loaded.get("textures"), gun + "_loaded: textures differ from the base");
            assertEquals(otherElements(base, gun), otherElements(loaded, gun + "_loaded"),
                    gun + "_loaded: elements outside the lock differ from the base");
            List<String> baseNames = elementNames(base);
            List<String> loadedNames = elementNames(loaded);
            assertTrue(loadedNames.containsAll(List.of("cock", "cock_jaw", "flint", "frizzen")), gun + "_loaded: lock parts");
            for (String name : loadedNames) {
                assertTrue(baseNames.contains(name) || LOCK_PARTS.contains(name), gun + "_loaded: unexpected element " + name);
            }
        }
    }

    /** Generated models (datagen) that hand-made overrides may point at: placeholders until their Blockbench model. */
    static final Path GENERATED_MODELS = Path.of("src/generated/resources/assets/pirates_n_ships/models");

    /**
     * The musket's hook override (GR3) comes after the cocked-hammer one, so it wins on a musket that is both loaded
     * and holding a hook (vanilla resolves the last matching override).
     */
    @Test
    void musketShowsALoadedHookLast() throws IOException {
        JsonArray overrides = itemModel("musket").getAsJsonArray("overrides");
        JsonObject last = overrides.get(overrides.size() - 1).getAsJsonObject();
        assertEquals(Set.of("pirates_n_ships:grapple_loaded"), last.getAsJsonObject("predicate").keySet());
        assertEquals("pirates_n_ships:item/musket_hook", last.get("model").getAsString());
    }

    /** Every override of a hand-made item model points at an existing model of ours (hand-made or generated). */
    @Test
    void everyItemModelOverrideTargetExists() throws IOException {
        for (Path file : jsonFiles(MAIN_MODELS.resolve("item"))) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!json.has("overrides")) continue;
            for (JsonElement o : json.getAsJsonArray("overrides")) {
                String model = o.getAsJsonObject().get("model").getAsString();
                assertTrue(model.startsWith("pirates_n_ships:item/"), file + ": override target " + model);
                String path = model.substring("pirates_n_ships:".length()) + ".json";
                assertTrue(Files.isRegularFile(MAIN_MODELS.resolve(path)) || Files.isRegularFile(GENERATED_MODELS.resolve(path)),
                        file + ": override target " + model + " has no hand-made or generated file");
                assertFalse(o.getAsJsonObject().getAsJsonObject("predicate").isEmpty(), file + ": empty predicate");
            }
        }
    }

    private static JsonObject itemModel(String name) throws IOException {
        return JsonParser.parseString(Files.readString(MAIN_MODELS.resolve("item/" + name + ".json"))).getAsJsonObject();
    }

    private static List<String> elementNames(JsonObject model) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : model.getAsJsonArray("elements")) out.add(e.getAsJsonObject().get("name").getAsString());
        return out;
    }

    private static Map<String, JsonElement> otherElements(JsonObject model, String what) {
        Map<String, JsonElement> out = new HashMap<>();
        for (JsonElement e : model.getAsJsonArray("elements")) {
            String name = e.getAsJsonObject().get("name").getAsString();
            if (LOCK_PARTS.contains(name)) continue;
            assertFalse(out.containsKey(name), what + ": duplicate element name " + name);
            out.put(name, e);
        }
        return out;
    }

    @Test
    void everyHandMadeModelParsesAndStaysInsideTheVanillaLimits() throws IOException {
        List<Path> models = jsonFiles(MAIN_MODELS);
        assertFalse(models.isEmpty(), "no hand-made models found under " + MAIN_MODELS.toAbsolutePath());
        for (Path file : models) {
            String text = Files.readString(file);
            BlockModel model = assertDoesNotThrow(() -> BlockModel.fromString(text), file + " does not parse");
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            assertTrue(json.has("elements"), file + ": a hand-made model brings its own elements");
            List<BlockElement> elements = model.getElements();
            assertFalse(elements.isEmpty(), file + ": no elements");
            for (BlockElement e : elements) {
                for (float v : new float[]{e.from.x(), e.from.y(), e.from.z(), e.to.x(), e.to.y(), e.to.z()}) {
                    assertTrue(v >= -16f && v <= 32f, file + ": coordinate " + v + " outside -16..32");
                }
                if (e.rotation != null) {
                    assertTrue(ANGLES.contains(e.rotation.angle()), file + ": rotation " + e.rotation.angle());
                }
            }
            Map<String, JsonElement> textures = json.has("textures") ? json.getAsJsonObject("textures").asMap() : Map.of();
            assertTrue(textures.containsKey("particle"), file + ": no particle texture (breaking particles)");
            for (BlockElement e : elements) {
                for (BlockElementFace face : e.faces.values()) {
                    assertResolves(file, textures, face.texture(), new ArrayList<>());
                }
            }
        }
    }

    /**
     * No visible z-fighting (V1, art/README.md "Z-fighting lint"): no two faces of different elements may lie in the
     * same plane (within {@value ZFight#TOLERANCE} px) facing the same way and overlap in area while they show
     * something different there (another texture, palette patch, mapping, tint or shading). The rule is the one of
     * {@code tools/lint_models.py}, which prints the same report and fixes cases with {@code --fix}. Faces in the same
     * plane that look the same, and faces covered by an opposite face (a part sitting on another), do not flicker:
     * they are counted as warnings only.
     */
    @Test
    void noVisibleZFighting() throws IOException {
        List<String> visible = new ArrayList<>();
        int sameLook = 0;
        int hidden = 0;
        for (Path file : jsonFiles(MAIN_MODELS)) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ZFight.Result result = ZFight.check(json);
            for (String fight : result.visible()) visible.add(file.getParent().getFileName() + "/" + name + ": " + fight);
            sameLook += result.sameLook();
            hidden += result.hidden();
        }
        System.out.println("z-fighting lint: " + sameLook + " same-look fights, " + hidden
                + " hidden-face pairs (warnings; python3 tools/lint_models.py --warnings lists them)");
        assertTrue(visible.isEmpty(), visible.size() + " visible z-fights (python3 tools/lint_models.py --fix):\n"
                + String.join("\n", visible));
    }

    /** The z-fighting rule of {@code tools/lint_models.py}, on raw model JSON. */
    static final class ZFight {
        static final double TOLERANCE = 0.03;
        private static final double AREA_EPS = 1e-3;
        private static final double PARALLEL_EPS = 1e-6;
        private static final double UV_EPS = 0.02;
        /** Striped patches of the palette sheets (sheet, u, v); every other 4x4 patch is one colour. */
        private static final Set<String> STRIPED = Set.of("palette/12/12", "palette_2/12/4", "palette_4/8/0",
                "palette_4/12/12");
        private static final List<String> DIRECTIONS = List.of("north", "south", "east", "west", "up", "down");

        record Result(List<String> visible, int sameLook, int hidden) {
        }

        record Face(int index, String element, String dir, double[][] corners, double[] normal, double plane,
                    String texture, double[] uv, double[][] vertexUv, int tint, boolean shade, double area) {
        }

        static Result check(JsonObject model) {
            List<Face> faces = faces(model);
            List<String> visible = new ArrayList<>();
            int sameLook = 0;
            int hidden = 0;
            for (int i = 0; i < faces.size(); i++) {
                Face a = faces.get(i);
                for (int j = i + 1; j < faces.size(); j++) {
                    Face b = faces.get(j);
                    if (a.index() == b.index()) continue;
                    double nd = dot(a.normal(), b.normal());
                    if (Math.abs(nd) < 1 - PARALLEL_EPS) continue;
                    double gap = dot(a.normal(), b.corners()[0]) - a.plane();
                    if (Math.abs(gap) > TOLERANCE) continue;
                    double[] n = a.normal();
                    double[] ref = Math.abs(n[0]) < 0.9 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
                    double[] eu = norm(cross(n, ref));
                    double[] ev = cross(n, eu);
                    List<double[]> pa = ccw(project(a.corners(), eu, ev));
                    List<double[]> pb = ccw(project(b.corners(), eu, ev));
                    if (Math.abs(area(pa)) < AREA_EPS || Math.abs(area(pb)) < AREA_EPS) continue;
                    List<double[]> inter = clip(pa, pb);
                    if (inter.size() < 3) continue;
                    double area = Math.abs(area(inter));
                    if (area < AREA_EPS) continue;
                    List<double[]> points = new ArrayList<>();
                    for (double[] q : inter) {
                        points.add(new double[]{a.plane() * n[0] + q[0] * eu[0] + q[1] * ev[0],
                                a.plane() * n[1] + q[0] * eu[1] + q[1] * ev[1],
                                a.plane() * n[2] + q[0] * eu[2] + q[1] * ev[2]});
                    }
                    if (nd > 0) {
                        if (looksSame(a, b, points)) {
                            sameLook++;
                        } else {
                            visible.add(String.format(java.util.Locale.ROOT,
                                    "%s[%d].%s / %s[%d].%s gap %.4f area %.4f near (%.2f, %.2f, %.2f), %s / %s",
                                    a.element(), a.index(), a.dir(), b.element(), b.index(), b.dir(), gap, area,
                                    points.get(0)[0], points.get(0)[1], points.get(0)[2], a.texture(), b.texture()));
                        }
                    } else if (area >= a.area() - AREA_EPS || area >= b.area() - AREA_EPS) {
                        hidden++;
                    }
                }
            }
            return new Result(visible, sameLook, hidden);
        }

        private static List<Face> faces(JsonObject model) {
            Map<String, String> textures = new HashMap<>();
            if (model.has("textures")) {
                model.getAsJsonObject("textures").entrySet().forEach(e -> textures.put(e.getKey(), e.getValue().getAsString()));
            }
            List<Face> out = new ArrayList<>();
            JsonArray elements = model.getAsJsonArray("elements");
            for (int i = 0; i < elements.size(); i++) {
                JsonObject e = elements.get(i).getAsJsonObject();
                double[] from = vec(e.getAsJsonArray("from"));
                double[] to = vec(e.getAsJsonArray("to"));
                Rotation rot = Rotation.of(e.getAsJsonObject("rotation"));
                boolean shade = !e.has("shade") || e.get("shade").getAsBoolean();
                String name = e.has("name") ? e.get("name").getAsString() : "#" + i;
                JsonObject faces = e.getAsJsonObject("faces");
                for (String d : DIRECTIONS) {
                    if (faces == null || !faces.has(d)) continue;
                    JsonObject data = faces.getAsJsonObject(d);
                    double[][] corners = corners(d, from, to);
                    for (int k = 0; k < 4; k++) corners[k] = rot.point(corners[k]);
                    double[] normal = rot.normal(direction(d));
                    double[] uv = data.has("uv") ? vec(data.getAsJsonArray("uv")) : defaultUv(d, from, to);
                    double[][] uvCorners = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
                    int shift = Math.floorMod(data.has("rotation") ? data.get("rotation").getAsInt() : 0, 360) / 90;
                    double[][] vertexUv = new double[4][];
                    for (int k = 0; k < 4; k++) vertexUv[k] = uvCorners[(k + shift) % 4];
                    double[] e1 = sub(corners[3], corners[0]);
                    double[] e2 = sub(corners[1], corners[0]);
                    out.add(new Face(i, name, d, corners, normal, dot(normal, corners[0]),
                            resolve(textures, data.has("texture") ? data.get("texture").getAsString() : ""), uv,
                            vertexUv, data.has("tintindex") ? data.get("tintindex").getAsInt() : -1, shade,
                            Math.sqrt(dot(e1, e1) * dot(e2, e2))));
                }
            }
            return out;
        }

        private static boolean looksSame(Face a, Face b, List<double[]> points) {
            if (!a.texture().equals(b.texture()) || a.tint() != b.tint() || a.shade() != b.shade()) return false;
            String patch = palettePatch(a);
            if (patch != null && patch.equals(palettePatch(b))) return true;
            for (double[] p : points) {
                double[] ua = uvAt(a, p);
                double[] ub = uvAt(b, p);
                if (Math.abs(ua[0] - ub[0]) > UV_EPS || Math.abs(ua[1] - ub[1]) > UV_EPS) return false;
            }
            return true;
        }

        private static String palettePatch(Face f) {
            if (!f.texture().startsWith("pirates_n_ships:item/palette")) return null;
            String sheet = f.texture().substring(f.texture().lastIndexOf('/') + 1);
            double[] uv = f.uv();
            int pu = (int) Math.floor(Math.min(uv[0], uv[2]) / 4) * 4;
            int pv = (int) Math.floor(Math.min(uv[1], uv[3]) / 4) * 4;
            if (Math.max(uv[0], uv[2]) > pu + 4 + 1e-6 || Math.max(uv[1], uv[3]) > pv + 4 + 1e-6) return null;
            String key = sheet + "/" + pu + "/" + pv;
            return STRIPED.contains(key) ? null : key;
        }

        private static double[] uvAt(Face f, double[] p) {
            double[] a = f.corners()[0];
            double[] e1 = sub(f.corners()[3], a);
            double[] e2 = sub(f.corners()[1], a);
            double[] rel = sub(p, a);
            double s = dot(e1, e1) > 0 ? dot(rel, e1) / dot(e1, e1) : 0;
            double t = dot(e2, e2) > 0 ? dot(rel, e2) / dot(e2, e2) : 0;
            double[] ua = f.vertexUv()[0], ub = f.vertexUv()[1], uc = f.vertexUv()[3];
            return new double[]{ua[0] + s * (uc[0] - ua[0]) + t * (ub[0] - ua[0]),
                    ua[1] + s * (uc[1] - ua[1]) + t * (ub[1] - ua[1])};
        }

        /** Vanilla {@code FaceBakery.applyElementRotation}: right-handed about the axis through the origin, then rescale. */
        record Rotation(char axis, double cos, double sin, double[] origin, double[] scale) {
            static final Rotation NONE = new Rotation('-', 1, 0, new double[3], new double[]{1, 1, 1});

            static Rotation of(JsonObject json) {
                if (json == null || json.get("angle").getAsDouble() == 0) return NONE;
                double angle = Math.toRadians(json.get("angle").getAsDouble());
                char axis = json.get("axis").getAsString().charAt(0);
                double k = json.has("rescale") && json.get("rescale").getAsBoolean() ? 1 / Math.cos(angle) : 1;
                double[] scale = axis == 'x' ? new double[]{1, k, k} : axis == 'y' ? new double[]{k, 1, k} : new double[]{k, k, 1};
                double[] origin = json.has("origin") ? vec(json.getAsJsonArray("origin")) : new double[]{8, 8, 8};
                return new Rotation(axis, Math.cos(angle), Math.sin(angle), origin, scale);
            }

            private double[] turn(double x, double y, double z) {
                return switch (axis) {
                    case 'x' -> new double[]{x, cos * y - sin * z, sin * y + cos * z};
                    case 'y' -> new double[]{cos * x + sin * z, y, -sin * x + cos * z};
                    case 'z' -> new double[]{cos * x - sin * y, sin * x + cos * y, z};
                    default -> new double[]{x, y, z};
                };
            }

            double[] point(double[] p) {
                double[] r = turn(p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]);
                return new double[]{r[0] * scale[0] + origin[0], r[1] * scale[1] + origin[1], r[2] * scale[2] + origin[2]};
            }

            double[] normal(double[] n) {
                double[] r = turn(n[0], n[1], n[2]);
                return norm(new double[]{r[0] / scale[0], r[1] / scale[1], r[2] / scale[2]});
            }
        }

        /** The face corners in vanilla's vertex order ({@code FaceInfo}), which take the UV corners at rotation 0. */
        private static double[][] corners(String d, double[] f, double[] t) {
            double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
            return switch (d) {
                case "north" -> new double[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                case "south" -> new double[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                case "east" -> new double[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
                case "west" -> new double[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                case "up" -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                default -> new double[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            };
        }

        private static double[] defaultUv(String d, double[] f, double[] t) {
            return switch (d) {
                case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
                case "up" -> new double[]{f[0], f[2], t[0], t[2]};
                case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
                case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
                case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
                default -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
            };
        }

        private static double[] direction(String d) {
            return switch (d) {
                case "north" -> new double[]{0, 0, -1};
                case "south" -> new double[]{0, 0, 1};
                case "east" -> new double[]{1, 0, 0};
                case "west" -> new double[]{-1, 0, 0};
                case "up" -> new double[]{0, 1, 0};
                default -> new double[]{0, -1, 0};
            };
        }

        private static String resolve(Map<String, String> textures, String ref) {
            for (int depth = 0; depth < 10 && ref.startsWith("#") && textures.containsKey(ref.substring(1)); depth++) {
                ref = textures.get(ref.substring(1));
            }
            return ref;
        }

        private static List<double[]> project(double[][] corners, double[] eu, double[] ev) {
            List<double[]> out = new ArrayList<>();
            for (double[] p : corners) out.add(new double[]{dot(p, eu), dot(p, ev)});
            return out;
        }

        private static double area(List<double[]> poly) {
            double sum = 0;
            for (int i = 0; i < poly.size(); i++) {
                double[] p = poly.get(i), q = poly.get((i + 1) % poly.size());
                sum += p[0] * q[1] - q[0] * p[1];
            }
            return sum / 2;
        }

        private static List<double[]> ccw(List<double[]> poly) {
            if (area(poly) >= 0) return poly;
            List<double[]> out = new ArrayList<>(poly);
            java.util.Collections.reverse(out);
            return out;
        }

        /** Sutherland-Hodgman: the subject clipped by a convex counter-clockwise clipper. */
        private static List<double[]> clip(List<double[]> subject, List<double[]> clipper) {
            List<double[]> out = subject;
            for (int i = 0; i < clipper.size() && !out.isEmpty(); i++) {
                double[] a = clipper.get(i), b = clipper.get((i + 1) % clipper.size());
                List<double[]> in = out;
                out = new ArrayList<>();
                for (int j = 0; j < in.size(); j++) {
                    double[] p = in.get(j), q = in.get((j + 1) % in.size());
                    double sp = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
                    double sq = (b[0] - a[0]) * (q[1] - a[1]) - (b[1] - a[1]) * (q[0] - a[0]);
                    if (sp >= 0) out.add(p);
                    if ((sp >= 0) != (sq >= 0)) {
                        double t = sp / (sp - sq);
                        out.add(new double[]{p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])});
                    }
                }
            }
            return out;
        }

        private static double[] vec(JsonArray array) {
            double[] out = new double[array.size()];
            for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsDouble();
            return out;
        }

        private static double[] sub(double[] a, double[] b) {
            return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
        }

        private static double dot(double[] a, double[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }

        private static double[] cross(double[] a, double[] b) {
            return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        }

        private static double[] norm(double[] a) {
            double length = Math.sqrt(dot(a, a));
            return new double[]{a[0] / length, a[1] / length, a[2] / length};
        }
    }

    /** The rule catches a decal flush with its plate and a 0.02 px inset, and passes a 0.05 px step. */
    @Test
    void zFightRuleSeesFlushAndNearlyFlushFaces() {
        assertEquals(1, ZFight.check(plateWithInlay(0)).visible().size(), "flush");
        assertEquals(1, ZFight.check(plateWithInlay(-0.02)).visible().size(), "0.02 px inside");
        assertEquals(0, ZFight.check(plateWithInlay(0.05)).visible().size(), "0.05 px proud");
    }

    private static JsonObject plateWithInlay(double front) {
        return JsonParser.parseString("""
                {"textures": {"0": "minecraft:block/iron_block", "1": "minecraft:block/black_concrete"},
                 "elements": [
                  {"from": [4, 4, 0], "to": [12, 12, 2], "faces": {"north": {"uv": [4, 4, 12, 12], "texture": "#0"}}},
                  {"from": [7, 7, %s], "to": [9, 9, 1], "faces": {"north": {"uv": [7, 7, 9, 9], "texture": "#1"}}}
                 ]}""".formatted(-front)).getAsJsonObject();
    }

    /** Follows {@code #key} references through the model's own texture map down to a texture id. */
    private static void assertResolves(Path file, Map<String, JsonElement> textures, String ref, List<String> seen) {
        if (!ref.startsWith("#")) return;
        String key = ref.substring(1);
        assertFalse(seen.contains(key), file + ": texture reference loop " + seen);
        seen.add(key);
        JsonElement value = textures.get(key);
        assertTrue(value != null && value.isJsonPrimitive(), file + ": unresolved texture " + ref);
        assertResolves(file, textures, value.getAsString(), seen);
    }
}
