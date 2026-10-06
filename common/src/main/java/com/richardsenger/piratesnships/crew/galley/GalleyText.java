package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.crew.provisions.ProvisionEffects;
import com.richardsenger.piratesnships.crew.provisions.ProvisionOutcome;
import com.richardsenger.piratesnships.crew.provisions.SuppliesLeft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Translatable texts of the pantry, the water barrel and the provisions debug command. English in {@link #LANG}. */
public final class GalleyText {

    public static final String MSG = "message." + Constants.MOD_ID + ".";
    public static final String CMD = "commands." + Constants.MOD_ID + ".provisions.";

    public static final String PANTRY_INFO = MSG + "pantry.info";
    public static final String PANTRY_EMPTY = MSG + "pantry.empty";
    public static final String NEXT_SPOIL = MSG + "pantry.next_spoil";
    public static final String NO_SPOIL = MSG + "pantry.no_spoil";
    public static final String BARREL_INFO = MSG + "water_barrel.info";
    public static final String BARREL_WATER = MSG + "barrel_water";
    public static final String NOT_A_CONTAINER = CMD + "not_a_container";
    public static final String CMD_CONTAINERS = CMD + "containers";
    public static final String CMD_DAYS = CMD + "days";
    public static final String CMD_ADVANCED = CMD + "advanced";
    public static final String CMD_CONSUMED = CMD + "consumed";
    public static final String CMD_SPOILED = CMD + "spoiled";
    public static final String CMD_NONE = CMD + "none";
    public static final String CMD_EFFECTS = CMD + "effects";
    public static final String CMD_DISABLED = CMD + "disabled";

    /** English texts for datagen. */
    public static final Map<String, String> LANG = Map.ofEntries(
            Map.entry(PANTRY_INFO, "Pantry: %s food (%s nutrition), %s water rations, %s rum, weight %s"),
            Map.entry(PANTRY_EMPTY, "The pantry holds no provisions"),
            Map.entry(NEXT_SPOIL, "Next to spoil: %s %s in %s days"),
            Map.entry(NO_SPOIL, "Nothing in here spoils"),
            Map.entry(BARREL_INFO, "Water barrel: %s / %s water rations"),
            Map.entry(BARREL_WATER, "barrel water rations"),
            Map.entry(NOT_A_CONTAINER, "That is not a pantry or water barrel"),
            Map.entry(CMD_CONTAINERS, "%s pantries and %s water barrels within %s blocks"),
            Map.entry(CMD_DAYS, "Supplies for a crew of %s: food %s days, water %s days, rum %s days"),
            Map.entry(CMD_ADVANCED, "Advanced %s days for %s crew, %s prisoners, rum ration %s"),
            Map.entry(CMD_CONSUMED, "Eaten: %s"),
            Map.entry(CMD_SPOILED, "Spoiled: %s"),
            Map.entry(CMD_NONE, "nothing"),
            Map.entry(CMD_EFFECTS, "Effects: morale %s, work speed %s, desertion risk %s, scurvy %s, hungry %s, thirsty %s, drunk %s"),
            Map.entry(CMD_DISABLED, "Provision consumption is disabled in the server config: nothing changes"));

    private GalleyText() {
    }

    public static String number(double v) {
        if (Double.isInfinite(v)) {
            return "∞";
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** The lines a player sees when inspecting a pantry. */
    public static List<Component> pantryInfo(PantryInfo info) {
        List<Component> lines = new ArrayList<>();
        if (info.isEmpty()) {
            lines.add(Component.translatable(PANTRY_EMPTY));
            return lines;
        }
        lines.add(Component.translatable(PANTRY_INFO, info.foodItems(), number(info.nutrition()), number(info.waterRations()),
                info.rumItems(), number(info.weight())));
        lines.add(info.nextSpoil()
                .<Component>map(n -> Component.translatable(NEXT_SPOIL, n.units(), itemName(n.id()), number(n.days())))
                .orElse(Component.translatable(NO_SPOIL)));
        return lines;
    }

    public static Component barrelInfo(int rations, int capacity) {
        return Component.translatable(BARREL_INFO, rations, capacity);
    }

    public static Component days(int crew, SuppliesLeft left) {
        return Component.translatable(CMD_DAYS, crew, number(left.foodDays()), number(left.waterDays()), number(left.rumDays()));
    }

    /** The debug command report of one update. */
    public static List<Component> outcome(ProvisionOutcome o) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(CMD_CONSUMED, units(o.consumed())));
        lines.add(Component.translatable(CMD_SPOILED, units(o.spoiled())));
        ProvisionEffects e = o.effects();
        lines.add(Component.translatable(CMD_EFFECTS, number(e.moraleChange()), String.format(Locale.ROOT, "%.2f", e.workSpeedMultiplier()),
                String.format(Locale.ROOT, "%.2f", e.desertionRisk()), e.scurvy(), o.hungry(), o.thirsty(), o.drunk()));
        return lines;
    }

    private static Component units(Map<String, Integer> units) {
        if (units.isEmpty()) {
            return Component.translatable(CMD_NONE);
        }
        var out = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(units).entrySet()) {
            if (!first) {
                out.append(", ");
            }
            out.append(e.getValue() + " ").append(itemName(e.getKey()));
            first = false;
        }
        return out;
    }

    /** The item's name for a provision type id; barrel water and unknown ids show as text. */
    static Component itemName(String id) {
        if (id.equals(WaterBarrelRules.WATER_ID)) {
            return Component.translatable(BARREL_WATER);
        }
        if (id.equals("minecraft:potion")) {
            return Component.translatable("item.minecraft.potion.effect.water");
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            return BuiltInRegistries.ITEM.get(rl).getDescription();
        }
        return Component.literal(id);
    }
}
