package com.richardsenger.piratesnships.chart.tile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import net.minecraft.core.Direction;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Datagen of the map tile's placeholder model (work package MAP2; a Blockbench model replaces it later, design.md
 * §4.8): one element a pixel thick, parchment ({@code textures/block/map_tile.png}, drawn by
 * {@code tools/gen_gui_textures.py}) on top and spruce around and below. The block state turns it like a button:
 * {@code y} by the facing on the floor, {@code x 90} onto the wall. The item is a flat sprite
 * ({@code textures/item/map_tile.png}). The drawing itself is the block entity renderer's.
 */
public final class MapTileModels {

    private static final String FACE = "pirates_n_ships:block/map_tile";
    private static final String BACK = "minecraft:block/spruce_planks";

    private MapTileModels() {
    }

    public static void generate(ModelContext m) {
        MapTileBlock block = ChartContent.MAP_TILE.get();
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        m.models().accept(model, MapTileModels::modelJson);
        m.blockStates().accept(MultiVariantGenerator.multiVariant(block, Variant.variant().with(VariantProperties.MODEL, model))
                .with(PropertyDispatch.properties(MapTileBlock.FACE, MapTileBlock.FACING).generate((face, facing) -> {
                    Variant v = Variant.variant();
                    int y = (int) facing.toYRot();
                    if (face == AttachFace.WALL) v = v.with(VariantProperties.X_ROT, VariantProperties.Rotation.R90);
                    if (face == AttachFace.CEILING) {
                        v = v.with(VariantProperties.X_ROT, VariantProperties.Rotation.R180);
                        y = (y + 180) % 360;
                    }
                    return y == 0 ? v : v.with(VariantProperties.Y_ROT, rotation(y));
                })));
        m.flatItem(ChartContent.MAP_TILE_ITEM.get());
    }

    private static VariantProperties.Rotation rotation(int degrees) {
        return switch (degrees) {
            case 90 -> VariantProperties.Rotation.R90;
            case 180 -> VariantProperties.Rotation.R180;
            case 270 -> VariantProperties.Rotation.R270;
            default -> VariantProperties.Rotation.R0;
        };
    }

    private static JsonObject modelJson() {
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", FACE);
        textures.addProperty("face", FACE);
        textures.addProperty("back", BACK);
        json.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", array(0, 0, 0));
        element.add("to", array(16, 1, 16));
        JsonObject faces = new JsonObject();
        faces.add("up", face("#face", array(0, 0, 16, 16), null));
        faces.add("down", face("#back", array(0, 0, 16, 16), Direction.DOWN));
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            faces.add(d.getSerializedName(), face("#back", array(0, 15, 16, 16), null));
        }
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        json.add("elements", elements);
        return json;
    }

    private static JsonObject face(String texture, JsonArray uv, Direction cull) {
        JsonObject f = new JsonObject();
        f.add("uv", uv);
        f.addProperty("texture", texture);
        if (cull != null) f.addProperty("cullface", cull.getSerializedName());
        return f;
    }

    private static JsonArray array(int... values) {
        JsonArray a = new JsonArray();
        for (int v : values) a.add(v);
        return a;
    }
}
