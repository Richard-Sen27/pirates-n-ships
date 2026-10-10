package com.richardsenger.piratesnships.ship.decor.wood;

import com.richardsenger.piratesnships.ship.decor.wood.ShipWood.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

/**
 * The woods of known wood mods (WOOD1, docs/woods.md): each row names the mod, the wood and its map colour; ids and
 * textures follow the mod's convention unless the row says otherwise. Every row is verified against the mod's real
 * 1.21.1 jar on every loader it is listed for by {@code tools/check_wood_compat.py} (report: docs/wood-compat.md):
 * textures, blocks, the planks item and the map colour (the planks texture's nearest vanilla map colour).
 *
 * <p>Convention for {@code w(MOD, "name", colour)} with namespace {@code ns} = the mod id: wood id
 * {@code <modid>_<name>}; planks block/item {@code ns:<name>_planks}; log {@code ns:<name>_log}, stripped log
 * {@code ns:stripped_<name>_log}; textures {@code ns:block/<name>_planks}, {@code ns:block/stripped_<name>_log} and its
 * {@code _top}; English name from the name in title case. Overrides on the same line: {@code .stem()} (stems instead of
 * logs), {@code .dir()} (textures in {@code block/<name>/planks}, {@code block/<name>/stripped_log}),
 * {@code .planksTex}, {@code .logTex} (the top follows as {@code <log>_top}), {@code .topTex}, {@code .log},
 * {@code .strippedLog} (paths in the mod's namespace), {@code .named}, {@code .sound}, {@code .fireproof()}.
 * Keep one row per line: the tool parses this file.
 *
 * <p>Adding a mod: a {@link Mod} constant and its rows, the jars under {@code <folder>/<slug>/<loader>/}, then
 * {@code python3 tools/check_wood_compat.py <folder> --markdown docs/wood-compat.md} until it reports no problems.
 */
public final class WoodCompat {

    /** A wood mod: its mod id (also the namespace of its blocks), English name, Modrinth slug, the loaders it ships on. */
    public record Mod(String id, String name, String slug, String loaders) {
    }

    static final Mod BOP = new Mod("biomesoplenty", "Biomes O' Plenty", "biomes-o-plenty", "neoforge,fabric");
    static final Mod RU = new Mod("regions_unexplored", "Regions Unexplored", "regions-unexplored", "neoforge,fabric");
    static final Mod BWG = new Mod("biomeswevegone", "Oh The Biomes We've Gone", "oh-the-biomes-weve-gone", "neoforge,fabric");
    static final Mod NS = new Mod("natures_spirit", "Nature's Spirit", "natures-spirit", "neoforge,fabric");
    static final Mod AETHER = new Mod("aether", "The Aether", "aether", "neoforge,fabric");
    static final Mod ECO = new Mod("ecologics", "Ecologics", "ecologics", "neoforge,fabric");
    static final Mod QUARK = new Mod("quark", "Quark", "quark", "neoforge");
    static final Mod DD = new Mod("deeperdarker", "Deeper and Darker", "deeperdarker", "neoforge,fabric");
    static final Mod ARS = new Mod("ars_nouveau", "Ars Nouveau", "ars-nouveau", "neoforge");
    static final Mod UG = new Mod("undergarden", "The Undergarden", "the-undergarden", "neoforge");
    static final Mod TF = new Mod("twilightforest", "The Twilight Forest", "twilightforest", "neoforge,fabric");

    /** Every mod of the table. */
    public static final List<Mod> MODS = List.of(BOP, RU, BWG, NS, AETHER, ECO, QUARK, DD, ARS, UG, TF);

    /** Every compat wood, in table order ({@link ShipWood#shipped()} sorts them by mod, then name). */
    public static final List<ShipWood> WOODS = build(List.of(
            // BOP
            w(BOP, "dead", MapColor.STONE),
            w(BOP, "empyreal", MapColor.TERRACOTTA_LIGHT_BLUE),
            w(BOP, "fir", MapColor.COLOR_LIGHT_GRAY),
            w(BOP, "hellbark", MapColor.TERRACOTTA_GRAY),
            w(BOP, "jacaranda", MapColor.WOOL),
            w(BOP, "magic", MapColor.COLOR_CYAN),
            w(BOP, "mahogany", MapColor.TERRACOTTA_MAGENTA),
            w(BOP, "maple", MapColor.DIRT),
            w(BOP, "palm", MapColor.TERRACOTTA_YELLOW),
            w(BOP, "pine", MapColor.TERRACOTTA_LIGHT_GREEN),
            w(BOP, "redwood", MapColor.CRIMSON_NYLIUM),
            w(BOP, "umbran", MapColor.TERRACOTTA_BLUE),
            w(BOP, "willow", MapColor.TERRACOTTA_LIGHT_GRAY),
            // RU
            w(RU, "baobab", MapColor.RAW_IRON),
            w(RU, "blackwood", MapColor.COLOR_BLACK),
            w(RU, "blue_bioshroom", MapColor.COLOR_LIGHT_BLUE).stem(),
            w(RU, "brimwood", MapColor.TERRACOTTA_BLACK),
            w(RU, "cobalt", MapColor.COLOR_BLACK),
            w(RU, "cypress", MapColor.TERRACOTTA_LIGHT_GREEN),
            w(RU, "dead", MapColor.COLOR_GRAY),
            w(RU, "eucalyptus", MapColor.DIRT),
            w(RU, "green_bioshroom", MapColor.GRASS).stem(),
            w(RU, "joshua", MapColor.DIRT),
            w(RU, "kapok", MapColor.DIRT),
            w(RU, "larch", MapColor.COLOR_LIGHT_GRAY),
            w(RU, "magnolia", MapColor.COLOR_RED),
            w(RU, "maple", MapColor.WOOD),
            w(RU, "palm", MapColor.RAW_IRON),
            w(RU, "pine", MapColor.RAW_IRON),
            w(RU, "pink_bioshroom", MapColor.COLOR_MAGENTA).stem(),
            w(RU, "redwood", MapColor.TERRACOTTA_RED),
            w(RU, "socotra", MapColor.DIRT),
            w(RU, "willow", MapColor.COLOR_LIGHT_GRAY),
            w(RU, "wisteria", MapColor.TERRACOTTA_LIGHT_BLUE),
            w(RU, "yellow_bioshroom", MapColor.COLOR_ORANGE).stem(),
            // BWG
            w(BWG, "aspen", MapColor.RAW_IRON).dir(),
            w(BWG, "baobab", MapColor.WOOD).dir(),
            w(BWG, "blue_enchanted", MapColor.COLOR_BLUE).dir(),
            w(BWG, "cika", MapColor.PODZOL).dir(),
            w(BWG, "cypress", MapColor.TERRACOTTA_LIGHT_GRAY).dir(),
            w(BWG, "ebony", MapColor.COLOR_BLACK).dir(),
            w(BWG, "fir", MapColor.DIRT).dir(),
            w(BWG, "florus", MapColor.TERRACOTTA_LIGHT_GREEN).stem().dir(),
            w(BWG, "green_enchanted", MapColor.COLOR_GREEN).dir(),
            w(BWG, "holly", MapColor.COLOR_LIGHT_GRAY).dir(),
            w(BWG, "ironwood", MapColor.STONE).dir(),
            w(BWG, "jacaranda", MapColor.TERRACOTTA_LIGHT_GRAY).dir(),
            w(BWG, "mahogany", MapColor.TERRACOTTA_MAGENTA).dir(),
            w(BWG, "maple", MapColor.DEEPSLATE).dir(),
            w(BWG, "palm", MapColor.TERRACOTTA_LIGHT_GRAY).dir(),
            w(BWG, "pine", MapColor.COLOR_LIGHT_GRAY).dir(),
            w(BWG, "rainbow_eucalyptus", MapColor.DEEPSLATE).dir(),
            w(BWG, "redwood", MapColor.TERRACOTTA_PINK).dir(),
            w(BWG, "sakura", MapColor.TERRACOTTA_RED).dir(),
            w(BWG, "skyris", MapColor.GLOW_LICHEN).dir(),
            w(BWG, "spirit", MapColor.METAL).dir(),
            w(BWG, "white_mangrove", MapColor.CLAY).dir(),
            w(BWG, "willow", MapColor.TERRACOTTA_GREEN).dir(),
            w(BWG, "witch_hazel", MapColor.COLOR_GRAY).dir(),
            w(BWG, "zelkova", MapColor.PODZOL).dir(),
            // NS
            w(NS, "aspen", MapColor.COLOR_ORANGE),
            w(NS, "cedar", MapColor.DIRT),
            w(NS, "coconut", MapColor.RAW_IRON),
            w(NS, "cypress", MapColor.TERRACOTTA_LIGHT_GRAY),
            w(NS, "fir", MapColor.COLOR_BROWN),
            w(NS, "ghaf", MapColor.PODZOL),
            w(NS, "joshua", MapColor.COLOR_GRAY),
            w(NS, "larch", MapColor.TERRACOTTA_CYAN),
            w(NS, "mahogany", MapColor.COLOR_BROWN),
            w(NS, "maple", MapColor.DIRT),
            w(NS, "olive", MapColor.COLOR_BROWN),
            w(NS, "palo_verde", MapColor.WOOD),
            w(NS, "redwood", MapColor.CRIMSON_HYPHAE),
            w(NS, "saxaul", MapColor.COLOR_BROWN),
            w(NS, "sugi", MapColor.TERRACOTTA_BROWN),
            w(NS, "willow", MapColor.TERRACOTTA_GRAY),
            w(NS, "wisteria", MapColor.COLOR_LIGHT_GRAY),
            // ECO
            w(ECO, "azalea", MapColor.DIRT),
            w(ECO, "coconut", MapColor.COLOR_ORANGE),
            w(ECO, "walnut", MapColor.COLOR_BROWN),
            // QUARK
            w(QUARK, "ancient", MapColor.WOOL),
            w(QUARK, "azalea", MapColor.GRASS),
            w(QUARK, "blossom", MapColor.TERRACOTTA_BROWN),
            // DD
            w(DD, "echo", MapColor.COLOR_LIGHT_GRAY),
            // AETHER
            w(AETHER, "skyroot", MapColor.TERRACOTTA_CYAN).planksTex("block/construction/skyroot_planks").logTex("block/natural/stripped_skyroot_log"),
            // DD
            // ARS (archwood logs come in four colours, all giving archwood planks; the blue log stands for them)
            w(ARS, "archwood", MapColor.STONE).log("blue_archwood_log"),
            // UG (side and end textures)
            w(UG, "grongle", MapColor.TERRACOTTA_GREEN).logTex("block/stripped_grongle_log_side").topTex("block/stripped_grongle_log_end"),
            w(UG, "smogstem", MapColor.COLOR_GRAY).logTex("block/stripped_smogstem_log_side").topTex("block/stripped_smogstem_log_end"),
            w(UG, "wigglewood", MapColor.TERRACOTTA_BROWN).logTex("block/stripped_wigglewood_log_side").topTex("block/stripped_wigglewood_log_end"),
            // TF (planks drawn from the first of four random variants)
            w(TF, "canopy", MapColor.TERRACOTTA_BROWN).planksTex("block/wood/planks_canopy_0"),
            w(TF, "dark", MapColor.PODZOL).planksTex("block/wood/planks_darkwood_0").named("Darkwood"),
            w(TF, "mangrove", MapColor.RAW_IRON).planksTex("block/wood/planks_mangrove_0").named("Twilight Mangrove"),
            w(TF, "mining", MapColor.COLOR_LIGHT_GRAY).planksTex("block/wood/planks_mine_0").named("Minewood"),
            w(TF, "sorting", MapColor.TERRACOTTA_BROWN).planksTex("block/wood/planks_sort_0").named("Sortingwood"),
            w(TF, "time", MapColor.PODZOL).planksTex("block/wood/planks_time_0").named("Timewood"),
            w(TF, "transformation", MapColor.TERRACOTTA_LIGHT_GRAY).planksTex("block/wood/planks_trans_0").named("Transwood"),
            w(TF, "twilight_oak", MapColor.WOOD).planksTex("block/wood/planks_twilight_oak_0")
    ));

    private WoodCompat() {
    }

    private static Row w(Mod mod, String name, MapColor color) {
        return new Row(mod, name, color);
    }

    private static List<ShipWood> build(List<Row> rows) {
        List<ShipWood> out = new ArrayList<>(rows.size());
        for (Row r : rows) out.add(r.build());
        return List.copyOf(out);
    }

    /** One row of the table: the convention, with the overrides the row names. */
    static final class Row {
        private final Mod mod;
        private final String name;
        private final MapColor color;
        private String kind = "log";
        private boolean dir;
        private String planksTex;
        private String logTex;
        private String topTex;
        private String log;
        private String strippedLog;
        private String display;
        private Sound sound = Sound.WOOD;
        private boolean flammable = true;

        Row(Mod mod, String name, MapColor color) {
            this.mod = mod;
            this.name = name;
            this.color = color;
        }

        Row stem() { kind = "stem"; return this; }
        Row dir() { dir = true; return this; }
        Row planksTex(String path) { planksTex = path; return this; }
        Row logTex(String path) { logTex = path; return this; }
        Row topTex(String path) { topTex = path; return this; }
        Row log(String path) { log = path; return this; }
        Row strippedLog(String path) { strippedLog = path; return this; }
        Row named(String english) { display = english; return this; }
        Row sound(Sound s) { sound = s; return this; }
        Row fireproof() { flammable = false; return this; }

        ShipWood build() {
            String planksT = planksTex != null ? planksTex : dir ? "block/" + name + "/planks" : "block/" + name + "_planks";
            String logT = logTex != null ? logTex : dir ? "block/" + name + "/stripped_" + kind : "block/stripped_" + name + "_" + kind;
            String topT = topTex != null ? topTex : logT + "_top";
            String english = display != null ? display : title(name);
            return new ShipWood(mod.id() + "_" + name, english, mod.id(), rl(planksT), rl(logT), rl(topT), rl(name + "_planks"),
                    rl(log != null ? log : name + "_" + kind), rl(strippedLog != null ? strippedLog : "stripped_" + name + "_" + kind),
                    color, sound, flammable);
        }

        private ResourceLocation rl(String path) {
            return ResourceLocation.fromNamespaceAndPath(mod.id(), path);
        }
    }

    /** {@code palo_verde} to "Palo Verde". */
    static String title(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
