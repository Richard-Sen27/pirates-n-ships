package com.richardsenger.piratesnships.almanac.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Optional;

/**
 * One element of an Almanac page (docs/design.md §3.4, ALM2; JSON format in docs/almanac.md). Written in a page's
 * {@code elements} list with a {@code "type"} field. Text, headings, lists and "Show me" buttons flow from the top in
 * the order given (ALM3 lays them out), and so do recipe panels and item spotlights (ALM8, fixed-height blocks that
 * never split across pages); illustrations and item icons are placed freely in page units, where the page
 * is the unit square (0,0 top left, 1,1 bottom right), so the screen can scale it. All text is lang keys.
 */
public sealed interface PageElement {

    Kind kind();

    /** Dispatches on {@code "type"}. */
    Codec<PageElement> CODEC = Kind.CODEC.dispatch("type", PageElement::kind, Kind::codec);

    StreamCodec<ByteBuf, PageElement> STREAM_CODEC = Kind.STREAM_CODEC.dispatch(PageElement::kind, Kind::streamCodec);

    /** The element types and their {@code "type"} names. */
    enum Kind implements StringRepresentable {
        TEXT("text"),
        HEADING("heading"),
        LIST("list"),
        ILLUSTRATION("illustration"),
        ITEM("item"),
        SHOW_ME("show_me"),
        RECIPE("recipe"),
        SPOTLIGHT("spotlight");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Kind::ordinal);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        MapCodec<? extends PageElement> codec() {
            return switch (this) {
                case TEXT -> Text.CODEC;
                case HEADING -> Heading.CODEC;
                case LIST -> ListItems.CODEC;
                case ILLUSTRATION -> Illustration.CODEC;
                case ITEM -> ItemIcon.CODEC;
                case SHOW_ME -> ShowMe.CODEC;
                case RECIPE -> RecipePanel.CODEC;
                case SPOTLIGHT -> Spotlight.CODEC;
            };
        }

        StreamCodec<ByteBuf, ? extends PageElement> streamCodec() {
            return switch (this) {
                case TEXT -> Text.STREAM_CODEC;
                case HEADING -> Heading.STREAM_CODEC;
                case LIST -> ListItems.STREAM_CODEC;
                case ILLUSTRATION -> Illustration.STREAM_CODEC;
                case ITEM -> ItemIcon.STREAM_CODEC;
                case SHOW_ME -> ShowMe.STREAM_CODEC;
                case RECIPE -> RecipePanel.STREAM_CODEC;
                case SPOTLIGHT -> Spotlight.STREAM_CODEC;
            };
        }
    }

    /**
     * A paragraph.
     *
     * @param key    lang key of the text
     * @param bold   drawn bold
     * @param italic drawn italic
     * @param color  RGB ink colour, empty for the screen's default ink
     */
    record Text(String key, boolean bold, boolean italic, Optional<Integer> color) implements PageElement {

        public static final MapCodec<Text> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AlmanacCodecs.LANG_KEY.fieldOf("key").forGetter(Text::key),
                Codec.BOOL.optionalFieldOf("bold", false).forGetter(Text::bold),
                Codec.BOOL.optionalFieldOf("italic", false).forGetter(Text::italic),
                AlmanacCodecs.COLOR.optionalFieldOf("color").forGetter(Text::color)
        ).apply(i, Text::new));

        public static final StreamCodec<ByteBuf, Text> STREAM_CODEC = StreamCodec.composite(
                AlmanacCodecs.STRING, Text::key,
                ByteBufCodecs.BOOL, Text::bold,
                ByteBufCodecs.BOOL, Text::italic,
                ByteBufCodecs.optional(ByteBufCodecs.INT), Text::color,
                Text::new);

        /** Plain text in the default ink. */
        public static Text of(String key) {
            return new Text(key, false, false, Optional.empty());
        }

        @Override
        public Kind kind() {
            return Kind.TEXT;
        }
    }

    /** A heading line. */
    record Heading(String key) implements PageElement {

        public static final MapCodec<Heading> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AlmanacCodecs.LANG_KEY.fieldOf("key").forGetter(Heading::key)
        ).apply(i, Heading::new));

        public static final StreamCodec<ByteBuf, Heading> STREAM_CODEC = AlmanacCodecs.STRING.map(Heading::new, Heading::key);

        @Override
        public Kind kind() {
            return Kind.HEADING;
        }
    }

    /** A bulleted list, one lang key per entry (at least one). */
    record ListItems(List<String> keys) implements PageElement {

        public static final MapCodec<ListItems> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AlmanacCodecs.LANG_KEY.listOf(1, 64).fieldOf("items").forGetter(ListItems::keys)
        ).apply(i, ListItems::new));

        public static final StreamCodec<ByteBuf, ListItems> STREAM_CODEC =
                AlmanacCodecs.STRING.apply(ByteBufCodecs.list(64)).map(ListItems::new, ListItems::keys);

        public ListItems {
            keys = List.copyOf(keys);
        }

        @Override
        public Kind kind() {
            return Kind.LIST;
        }
    }

    /**
     * A picture placed freely on the page.
     *
     * @param texture full texture path, e.g. {@code pirates_n_ships:textures/gui/almanac/ship.png}
     * @param x       left edge in page units
     * @param y       top edge in page units
     * @param w       width in page units (more than 0, {@code x + w <= 1})
     * @param h       height in page units (more than 0, {@code y + h <= 1})
     */
    record Illustration(ResourceLocation texture, float x, float y, float w, float h) implements PageElement {

        public static final MapCodec<Illustration> CODEC = RecordCodecBuilder.<Illustration>mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Illustration::texture),
                AlmanacCodecs.UNIT.fieldOf("x").forGetter(Illustration::x),
                AlmanacCodecs.UNIT.fieldOf("y").forGetter(Illustration::y),
                AlmanacCodecs.UNIT.fieldOf("w").forGetter(Illustration::w),
                AlmanacCodecs.UNIT.fieldOf("h").forGetter(Illustration::h)
        ).apply(i, Illustration::new)).flatXmap(Illustration::check, Illustration::check);

        public static final StreamCodec<ByteBuf, Illustration> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Illustration::texture,
                ByteBufCodecs.FLOAT, Illustration::x,
                ByteBufCodecs.FLOAT, Illustration::y,
                ByteBufCodecs.FLOAT, Illustration::w,
                ByteBufCodecs.FLOAT, Illustration::h,
                Illustration::new);

        private static final float EPSILON = 1e-4f;

        private static DataResult<Illustration> check(Illustration ill) {
            if (ill.w <= 0f || ill.h <= 0f) return DataResult.error(() -> "illustration with no size: " + ill);
            if (ill.x + ill.w > 1f + EPSILON || ill.y + ill.h > 1f + EPSILON) {
                return DataResult.error(() -> "illustration reaches past the page (x + w and y + h must be <= 1): " + ill);
            }
            return DataResult.success(ill);
        }

        @Override
        public Kind kind() {
            return Kind.ILLUSTRATION;
        }
    }

    /**
     * An item drawn at a point of the page.
     *
     * @param item    the item id; an unknown item fails the chapter
     * @param x       left edge in page units
     * @param y       top edge in page units
     * @param caption lang key of a caption drawn beside it, if any
     */
    record ItemIcon(ResourceLocation item, float x, float y, Optional<String> caption) implements PageElement {

        public static final MapCodec<ItemIcon> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AlmanacCodecs.ITEM_ID.fieldOf("item").forGetter(ItemIcon::item),
                AlmanacCodecs.UNIT.fieldOf("x").forGetter(ItemIcon::x),
                AlmanacCodecs.UNIT.fieldOf("y").forGetter(ItemIcon::y),
                AlmanacCodecs.LANG_KEY.optionalFieldOf("caption").forGetter(ItemIcon::caption)
        ).apply(i, ItemIcon::new));

        public static final StreamCodec<ByteBuf, ItemIcon> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, ItemIcon::item,
                ByteBufCodecs.FLOAT, ItemIcon::x,
                ByteBufCodecs.FLOAT, ItemIcon::y,
                ByteBufCodecs.optional(AlmanacCodecs.STRING), ItemIcon::caption,
                ItemIcon::new);

        @Override
        public Kind kind() {
            return Kind.ITEM;
        }
    }

    /**
     * A "Show me" button that plays a Ponder scene (ALM4 wires the scene).
     *
     * @param scene the Ponder scene id, e.g. {@code pirates_n_ships:cannon_loading}
     * @param label lang key of the button label
     */
    record ShowMe(String scene, String label) implements PageElement {

        public static final MapCodec<ShowMe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.string(1, AlmanacCodecs.MAX_STRING).fieldOf("scene").forGetter(ShowMe::scene),
                AlmanacCodecs.LANG_KEY.fieldOf("label").forGetter(ShowMe::label)
        ).apply(i, ShowMe::new));

        public static final StreamCodec<ByteBuf, ShowMe> STREAM_CODEC = StreamCodec.composite(
                AlmanacCodecs.STRING, ShowMe::scene,
                AlmanacCodecs.STRING, ShowMe::label,
                ShowMe::new);

        @Override
        public Kind kind() {
            return Kind.SHOW_ME;
        }
    }

    /**
     * A crafting panel (ALM8): the grid, the arrow and the result of one recipe, looked up by id in the client's
     * recipe manager when the page is drawn (so a datapack's change shows). An unknown id draws an empty panel.
     *
     * @param recipe the recipe id, e.g. {@code pirates_n_ships:helm}
     */
    record RecipePanel(ResourceLocation recipe) implements PageElement {

        public static final MapCodec<RecipePanel> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("recipe").forGetter(RecipePanel::recipe)
        ).apply(i, RecipePanel::new));

        public static final StreamCodec<ByteBuf, RecipePanel> STREAM_CODEC =
                ResourceLocation.STREAM_CODEC.map(RecipePanel::new, RecipePanel::recipe);

        @Override
        public Kind kind() {
            return Kind.RECIPE;
        }
    }

    /**
     * An item spotlight (ALM8): the item framed at twice its size with its name under it, as a block in the flow.
     *
     * @param item the item id; an unknown item fails the chapter
     */
    record Spotlight(ResourceLocation item) implements PageElement {

        public static final MapCodec<Spotlight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AlmanacCodecs.ITEM_ID.fieldOf("item").forGetter(Spotlight::item)
        ).apply(i, Spotlight::new));

        public static final StreamCodec<ByteBuf, Spotlight> STREAM_CODEC =
                ResourceLocation.STREAM_CODEC.map(Spotlight::new, Spotlight::item);

        @Override
        public Kind kind() {
            return Kind.SPOTLIGHT;
        }
    }
}
