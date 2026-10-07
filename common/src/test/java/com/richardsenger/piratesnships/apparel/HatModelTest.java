package com.richardsenger.piratesnships.apparel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hat item models sit on a head where the matching mob's hat sits (run from {@code common/}).
 *
 * <p>Each model's corners go through the chain vanilla's {@code CustomHeadLayer} uses for a non-armour head item
 * (element rotation, the model's {@code head} display transform, {@code translateToHead}: 4 px up, 180 degrees about y,
 * scale 0.625 / -0.625 / -0.625) into head pixels, in the geo file's frame above the neck pivot (y 24 = the bottom of
 * the head, front -z; GeckoLib mirrors the file's x, so geo x = -model x). The mob's hat cubes go through GeckoLib's
 * own loading rules (x mirrored, x and y rotations negated) into the same frame. Their bounding boxes must agree within
 * 1 px on every side, and the hat must stay above the bottom of the head.
 */
class HatModelTest {

    private static final Path ITEM_MODELS = Path.of("src/main/resources/assets/pirates_n_ships/models/item");
    private static final Path GEO = Path.of("src/main/resources/assets/pirates_n_ships/geo");
    private static final float TOLERANCE = 1.0f;

    /** Hat item, mob geometry, and the painted rows of the mob's hat layer (0 = the layer is transparent). */
    private record Pair(String item, String mob, int hatLayerRows) {
    }

    private static final List<Pair> PAIRS = List.of(
            new Pair("navy_hat", "navy_soldier", 0),
            new Pair("pirate_hat", "navy_soldier", 0),
            new Pair("officer_hat", "navy_officer", 0),
            // The bandana is painted on the top three rows of the hat layer (seafarer_skins.js) plus knot and tails
            new Pair("bandana", "pirate", 3));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyHatHasAHeadDisplayAndSitsWhereTheMobsHatSits() throws IOException {
        for (Pair pair : PAIRS) {
            float[] item = itemBox(pair.item());
            float[] mob = mobBox(pair.mob(), pair.hatLayerRows());
            String[] names = {"x min", "y min", "z min", "x max", "y max", "z max"};
            for (int i = 0; i < 6; i++) {
                assertTrue(Math.abs(item[i] - mob[i]) <= TOLERANCE, pair.item() + " " + names[i] + " " + item[i]
                        + " is more than " + TOLERANCE + " px from " + pair.mob() + "'s hat (" + mob[i] + ")");
            }
            assertTrue(item[1] >= 24f, pair.item() + " reaches below the head: y " + item[1]);
        }
    }

    @Test
    void hatsGoOnTheHeadWithTheConfiguredArmour() {
        assertEquals(EquipmentSlot.HEAD, HatArmor.SLOT);
        assertTrue(Equipable.class.isAssignableFrom(HatItem.class), "a hat is Equipable");
        ItemAttributeModifiers one = HatArmor.modifiers(1);
        assertEquals(1, one.modifiers().size());
        ItemAttributeModifiers.Entry entry = one.modifiers().getFirst();
        assertTrue(entry.attribute().is(Attributes.ARMOR), "armour attribute");
        assertEquals(EquipmentSlotGroup.HEAD, entry.slot());
        assertEquals(1.0, entry.modifier().amount());
        assertEquals(3.0, HatArmor.modifiers(3).modifiers().getFirst().modifier().amount());
        assertTrue(HatArmor.modifiers(0).modifiers().isEmpty(), "0 gives no modifier");
    }

    // --- item model through CustomHeadLayer ---------------------------------------------------------------------

    private static float[] itemBox(String name) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(ITEM_MODELS.resolve(name + ".json"))).getAsJsonObject();
        JsonObject head = json.getAsJsonObject("display").getAsJsonObject("head");
        assertNotNull(head, name + ": no head display");
        Vector3f rotation = vec(head.getAsJsonArray("rotation"));
        Vector3f translation = vec(head.getAsJsonArray("translation")).mul(1 / 16f);
        Vector3f scale = vec(head.getAsJsonArray("scale"));
        Quaternionf displayRotation = new Quaternionf().rotationXYZ((float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z));
        List<Vector3f> points = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            for (Vector3f p : corners(vec(e.getAsJsonArray("from")), vec(e.getAsJsonArray("to")))) {
                if (e.has("rotation")) {
                    JsonObject r = e.getAsJsonObject("rotation");
                    p = rotate(p, r.get("axis").getAsString(), r.get("angle").getAsFloat(), vec(r.getAsJsonArray("origin")));
                }
                // ItemRenderer: display transform (translate, rotate, scale), then translate(-0.5)
                Vector3f v = new Vector3f(p).mul(1 / 16f).sub(0.5f, 0.5f, 0.5f).mul(scale);
                displayRotation.transform(v);
                v.add(translation);
                // translateToHead: translate(0, -0.25, 0), rotate 180 about y, scale(0.625, -0.625, -0.625): in the
                // head's model space (pixels, y down) the point is (-10 v.x, -10 v.y - 4, 10 v.z). Geo files use the
                // vanilla model's x (art/README.md, "Mirrored x") with y up and the neck at y 24.
                points.add(new Vector3f(-10 * v.x, 24 + 4 + 10 * v.y, 10 * v.z));
            }
        }
        return box(points);
    }

    // --- mob hat through GeckoLib's loader rules ----------------------------------------------------------------

    private static float[] mobBox(String mob, int hatLayerRows) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(GEO.resolve(mob + ".geo.json"))).getAsJsonObject();
        JsonObject hat = null;
        for (JsonElement b : json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            if (b.getAsJsonObject().get("name").getAsString().equals("hat")) {
                hat = b.getAsJsonObject();
            }
        }
        assertNotNull(hat, mob + ": no hat bone");
        List<Vector3f> points = new ArrayList<>();
        for (JsonElement c : hat.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
            Vector3f origin = vec(cube.getAsJsonArray("origin"));
            Vector3f size = vec(cube.getAsJsonArray("size"));
            Vector3f from = new Vector3f(origin).sub(inflate, inflate, inflate);
            Vector3f to = new Vector3f(origin).add(size).add(inflate, inflate, inflate);
            if (inflate > 0) {
                // The contract hat layer: only its painted top rows show (each row is (8 + 2 inflate) / 8 px high)
                if (hatLayerRows == 0) continue;
                from.y = to.y - hatLayerRows * (size.y + 2 * inflate) / size.y;
            }
            // GeckoLib: x mirrored, rotations (-x, -y, z) about the mirrored pivot, applied z, then y, then x last
            Vector3f mFrom = new Vector3f(-to.x, from.y, from.z);
            Vector3f mTo = new Vector3f(-from.x, to.y, to.z);
            for (Vector3f p : corners(mFrom, mTo)) {
                if (cube.has("rotation")) {
                    Vector3f r = vec(cube.getAsJsonArray("rotation"));
                    Vector3f pv = vec(cube.getAsJsonArray("pivot"));
                    Vector3f pivot = new Vector3f(-pv.x, pv.y, pv.z);
                    Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(r.z))
                            .rotateY((float) Math.toRadians(-r.y)).rotateX((float) Math.toRadians(-r.x));
                    p = q.transform(new Vector3f(p).sub(pivot)).add(pivot);
                }
                points.add(new Vector3f(-p.x, p.y, p.z));
            }
        }
        return box(points);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    /** Vanilla's element rotation (FaceBakery): a right-handed rotation about the axis through the origin. */
    private static Vector3f rotate(Vector3f p, String axis, float degrees, Vector3f origin) {
        float a = (float) Math.toRadians(degrees);
        Quaternionf q = switch (axis) {
            case "x" -> new Quaternionf().rotationX(a);
            case "y" -> new Quaternionf().rotationY(a);
            default -> new Quaternionf().rotationZ(a);
        };
        return q.transform(new Vector3f(p).sub(origin)).add(origin);
    }

    private static List<Vector3f> corners(Vector3f from, Vector3f to) {
        List<Vector3f> out = new ArrayList<>();
        for (float x : new float[]{from.x, to.x}) {
            for (float y : new float[]{from.y, to.y}) {
                for (float z : new float[]{from.z, to.z}) {
                    out.add(new Vector3f(x, y, z));
                }
            }
        }
        return out;
    }

    private static float[] box(List<Vector3f> points) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Vector3f p : points) {
            b[0] = Math.min(b[0], p.x);
            b[1] = Math.min(b[1], p.y);
            b[2] = Math.min(b[2], p.z);
            b[3] = Math.max(b[3], p.x);
            b[4] = Math.max(b[4], p.y);
            b[5] = Math.max(b[5], p.z);
        }
        return b;
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
}
