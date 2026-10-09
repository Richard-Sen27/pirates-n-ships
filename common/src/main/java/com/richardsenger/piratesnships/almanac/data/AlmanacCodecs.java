package com.richardsenger.piratesnships.almanac.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/**
 * Field codecs shared by the Almanac's chapter model (docs/design.md §3.4, ALM2; format in docs/almanac.md). Pure:
 * the item, block and entity type checks read the static registries only, so JUnit can use it after {@code Bootstrap.bootStrap()}.
 */
public final class AlmanacCodecs {

    /** Longest lang key or scene id accepted (file and wire). */
    public static final int MAX_STRING = 256;

    /** A lang key: non-empty, no spaces. Unknown keys are allowed (lang lives on the client). */
    public static final Codec<String> LANG_KEY = Codec.STRING.validate(AlmanacCodecs::checkKey);

    /** An RGB colour written as {@code "#rrggbb"}. */
    public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(AlmanacCodecs::parseColor, AlmanacCodecs::formatColor);

    /** A coordinate or size in page units, 0..1 (the page is a unit square). */
    public static final Codec<Float> UNIT = Codec.floatRange(0f, 1f);

    /** An item id that must name a registered item (not air): an unknown item fails the chapter. */
    public static final Codec<ResourceLocation> ITEM_ID = ResourceLocation.CODEC.validate(AlmanacCodecs::checkItem);

    /** Lang keys and scene ids on the wire. */
    public static final StreamCodec<ByteBuf, String> STRING = ByteBufCodecs.stringUtf8(MAX_STRING);

    private AlmanacCodecs() {
    }

    static DataResult<String> checkKey(String key) {
        if (key.isEmpty()) return DataResult.error(() -> "empty lang key");
        if (key.length() > MAX_STRING) return DataResult.error(() -> "lang key longer than " + MAX_STRING + ": " + key);
        if (key.chars().anyMatch(Character::isWhitespace)) return DataResult.error(() -> "lang key with whitespace: '" + key + "'");
        return DataResult.success(key);
    }

    static DataResult<ResourceLocation> checkItem(ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            return DataResult.error(() -> "unknown item " + id);
        }
        return DataResult.success(id);
    }

    /** A registered block other than air (ALM10's 3D spotlights). */
    static DataResult<ResourceLocation> checkBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR) {
            return DataResult.error(() -> "unknown block " + id);
        }
        return DataResult.success(id);
    }

    /** A registered entity type (ALM10's 3D spotlights). */
    static DataResult<ResourceLocation> checkEntity(ResourceLocation id) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return DataResult.error(() -> "unknown entity type " + id);
        return DataResult.success(id);
    }

    static DataResult<Integer> parseColor(String s) {
        if (s.length() != 7 || s.charAt(0) != '#') return DataResult.error(() -> "colour must be \"#rrggbb\": " + s);
        try {
            return DataResult.success(Integer.parseInt(s.substring(1), 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "colour must be \"#rrggbb\": " + s);
        }
    }

    static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }
}
