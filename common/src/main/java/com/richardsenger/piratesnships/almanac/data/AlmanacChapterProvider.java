package com.richardsenger.piratesnships.almanac.data;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.cannon.CannonContent;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datagen of the Almanac's built-in chapters (ALM2): each chapter's JSON under {@code data/pirates_n_ships/almanac/chapters/}
 * and the English text of every lang key it uses, written side by side so a key never misses its text. ALM5's
 * generator replaces these examples with the chapters made from docs/guide.md.
 */
public final class AlmanacChapterProvider {

    public static final ResourceLocation WELCOME = Constants.id("welcome");
    public static final ResourceLocation CANNONS = Constants.id("cannons");
    /** The Ponder scene the cannon chapter's "Show me" button plays (ALM4). */
    public static final String CANNON_LOADING_SCENE = Constants.id("cannon_loading").toString();
    /** The Almanac's own item (ALM1); the book stands in while it is not registered. */
    public static final ResourceLocation ALMANAC_ITEM = Constants.id("captains_almanac");

    private AlmanacChapterProvider() {
    }

    public static void gather(DataContributions data) {
        Map<String, String> lang = new LinkedHashMap<>();
        for (Chapter chapter : chapters(lang)) {
            data.encoded(PackOutput.Target.DATA_PACK, AlmanacReloadListener.DIRECTORY, chapter.id(), Chapter.Body.CODEC, chapter.body());
        }
        data.lang(l -> lang.forEach(l::add));
    }

    /** The built-in chapters; every lang key they use is put into {@code lang} with its English text. */
    public static List<Chapter> chapters(Map<String, String> lang) {
        return List.of(welcome(lang), cannons(lang));
    }

    /** The Almanac item's id, or vanilla's book while ALM1's item is not registered. */
    static ResourceLocation almanacIcon() {
        return BuiltInRegistries.ITEM.containsKey(ALMANAC_ITEM) ? ALMANAC_ITEM : BuiltInRegistries.ITEM.getKey(Items.BOOK);
    }

    private static Chapter welcome(Map<String, String> lang) {
        Builder b = new Builder(WELCOME, lang);
        String title = b.title("Welcome Aboard");
        Page one = Page.of(
                new PageElement.Heading(b.key(1, "heading", "The Captain's Almanac")),
                PageElement.Text.of(b.key(1, "intro", "Every captain keeps an almanac. This one holds what you need to build a ship, "
                        + "sail her, fight for her and keep her afloat.")),
                new PageElement.Text(b.key(1, "hint", "Pick a chapter by its bookmark on the edge of the book."), false, true, Optional.empty()),
                new PageElement.ItemIcon(almanacIcon(), 0.45f, 0.75f, Optional.of(b.key(1, "almanac_caption", "Keep it close"))));
        Page two = Page.of(
                new PageElement.Heading(b.key(2, "heading", "Under Which Flag?")),
                PageElement.Text.of(b.key(2, "text", "Pirate or navy, merchant or rogue: the sea remembers what you do. "
                        + "Choose your colours with care.")),
                new PageElement.Illustration(Constants.id("textures/block/flag_jolly_roger.png"), 0.2f, 0.45f, 0.6f, 0.4f));
        return new Chapter(WELCOME, title, 0, 0x8B1A1A, almanacIcon(), List.of(one, two));
    }

    private static Chapter cannons(Map<String, String> lang) {
        Builder b = new Builder(CANNONS, lang);
        String title = b.title("Cannons");
        ResourceLocation cannon = CannonContent.CANNON.id();
        ResourceLocation ball = CombatContent.CANNONBALL.id();
        ResourceLocation powder = BuiltInRegistries.ITEM.getKey(Items.GUNPOWDER);
        Page loading = Page.of(
                new PageElement.Heading(b.key(1, "heading", "Loading")),
                PageElement.Text.of(b.key(1, "text", "A cannon is loaded from the muzzle, in this order:")),
                new PageElement.ListItems(List.of(
                        b.key(1, "step1", "Gunpowder first"),
                        b.key(1, "step2", "Then the cannonball"),
                        b.key(1, "step3", "Ram it home"))),
                new PageElement.ItemIcon(powder, 0.2f, 0.75f, Optional.of(b.key(1, "powder", "Gunpowder"))),
                new PageElement.ItemIcon(ball, 0.6f, 0.75f, Optional.of(b.key(1, "ball", "Cannonball"))));
        Page firing = Page.of(
                new PageElement.Heading(b.key(2, "heading", "Firing")),
                PageElement.Text.of(b.key(2, "text", "Aim the barrel, raise or lower it, then light the fuse. "
                        + "The ball flies with the ship's own speed.")),
                new PageElement.Text(b.key(2, "warning", "Stand clear of the recoil!"), true, false, Optional.of(0x8B1A1A)));
        Page showMe = Page.of(
                new PageElement.Heading(b.key(3, "heading", "See It Done")),
                PageElement.Text.of(b.key(3, "text", "Watch a gun crew load and fire a cannon, step by step.")),
                new PageElement.ShowMe(CANNON_LOADING_SCENE, b.key(3, "show_me", "Show me")),
                new PageElement.ItemIcon(cannon, 0.42f, 0.7f, Optional.empty()));
        return new Chapter(CANNONS, title, 10, 0x3B3B3B, cannon, List.of(loading, firing, showMe));
    }

    /** Lang keys of one chapter by the convention of {@link AlmanacLang}, recording their English text. */
    private record Builder(ResourceLocation chapter, Map<String, String> lang) {

        String title(String text) {
            return put(AlmanacLang.title(chapter), text);
        }

        String key(int page, String name, String text) {
            return put(AlmanacLang.page(chapter, page, name), text);
        }

        private String put(String key, String text) {
            if (lang.putIfAbsent(key, text) != null) throw new IllegalStateException("Duplicate almanac lang key " + key);
            return key;
        }
    }
}
