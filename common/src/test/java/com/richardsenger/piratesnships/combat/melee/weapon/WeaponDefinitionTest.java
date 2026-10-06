package com.richardsenger.piratesnships.combat.melee.weapon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.core.data.DefinitionParser;
import com.richardsenger.piratesnships.core.data.Definitions;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WeaponDefinitionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static final WeaponDefinition R = DefaultWeapons.RAPIER, C = DefaultWeapons.CUTLASS, S = DefaultWeapons.SABER;

    static JsonObject json(WeaponDefinition w) {
        return WeaponDefinition.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow().getAsJsonObject();
    }

    static String errorOf(Consumer<JsonObject> breakIt) {
        JsonObject j = json(S);
        breakIt.accept(j);
        DataResult<WeaponDefinition> r = WeaponDefinition.CODEC.parse(JsonOps.INSTANCE, j);
        assertTrue(r.isError(), "expected an error for " + j);
        return r.error().orElseThrow().message();
    }

    @Test
    void roundTripOfAllDefaults() {
        DefaultWeapons.all().values().forEach(w -> assertEquals(w, WeaponDefinition.CODEC.parse(JsonOps.INSTANCE, json(w)).getOrThrow()));
    }

    @Test
    void optionalFieldsHaveDefaults() {
        JsonObject j = json(S);
        j.getAsJsonObject("slash").remove("vertical_tolerance");
        j.getAsJsonObject("thrust").remove("miss_recovery_ticks");
        j.getAsJsonObject("parry").remove("riposte_multiplier");
        WeaponDefinition w = WeaponDefinition.CODEC.parse(JsonOps.INSTANCE, j).getOrThrow();
        assertEquals(0.6, w.slash().verticalTolerance());
        assertEquals(0, w.thrust().missRecoveryTicks());
        assertEquals(1.0, w.parry().riposteMultiplier());
    }

    @Test
    void validationErrorsAreClear() {
        assertTrue(errorOf(j -> j.getAsJsonObject("slash").addProperty("arc_degrees", 270)).contains("270"));
        assertTrue(errorOf(j -> j.getAsJsonObject("guard").addProperty("damage_reduction", 1.5)).contains("1.5"));
        assertTrue(errorOf(j -> j.getAsJsonObject("thrust").addProperty("active_ticks", 0)).contains("outside of range"));
        assertTrue(errorOf(j -> j.getAsJsonObject("slash").addProperty("damage", -1)).contains("-1"));
        assertTrue(errorOf(j -> j.remove("guard")).contains("guard"));
        assertTrue(errorOf(j -> j.getAsJsonObject("thrust").remove("reach")).contains("reach"));
        assertTrue(errorOf(j -> {
            JsonObject s = j.getAsJsonObject("slash");
            s.addProperty("windup_ticks", 0);
            s.addProperty("active_ticks", 1);
            s.addProperty("recovery_ticks", 0);
        }).contains("at least 2 ticks"));
    }

    @Test
    void brokenFileIsSkippedByTheDefinitionParser() {
        JsonObject broken = json(S);
        broken.getAsJsonObject("guard").addProperty("arc_degrees", 0);
        Map<ResourceLocation, JsonElement> files = Map.of(DefaultWeapons.SABER_ID, json(S), DefaultWeapons.RAPIER_ID, broken);
        var parsed = DefinitionParser.parse(WeaponDefinition.CODEC, JsonOps.INSTANCE, files);
        assertEquals(Map.of(DefaultWeapons.SABER_ID, S), parsed.values());
        assertEquals(1, parsed.errors().size());
        assertEquals(DefaultWeapons.RAPIER_ID, parsed.errors().get(0).id());
        assertTrue(parsed.errors().get(0).message().contains("arc_degrees") || parsed.errors().get(0).message().contains("0"),
                parsed.errors().get(0).message());
    }

    @Test
    void stackLookupByItemId() {
        var defs = Definitions.of("weapon", Map.of(BuiltInRegistries.ITEM.getKey(Items.GOLDEN_SWORD), S));
        assertEquals(S, MeleeWeapons.forStack(new ItemStack(Items.GOLDEN_SWORD), defs).orElseThrow());
        assertTrue(MeleeWeapons.forStack(new ItemStack(Items.IRON_SWORD), defs).isEmpty());
        assertTrue(MeleeWeapons.forStack(ItemStack.EMPTY, defs).isEmpty());
        assertEquals(S, MeleeWeapons.forItem(BuiltInRegistries.ITEM.getKey(Items.GOLDEN_SWORD), defs).orElseThrow());
    }

    @Test
    void defaultIdsMatchTheSwordItems() {
        assertEquals("pirates_n_ships:rapier", DefaultWeapons.RAPIER_ID.toString());
        assertEquals("pirates_n_ships:cutlass", DefaultWeapons.CUTLASS_ID.toString());
        assertEquals("pirates_n_ships:saber", DefaultWeapons.SABER_ID.toString());
    }

    // --- §8.1 character -----------------------------------------------------------------------------------------

    static void between(double low, double mid, double high, String what) {
        assertTrue(Math.min(low, high) <= mid && mid <= Math.max(low, high), what + ": saber " + mid + " not between " + low + " and " + high);
    }

    @Test
    void rapierIsFastLongReachStrongThrustWeakSlash() {
        for (WeaponDefinition o : new WeaponDefinition[]{C, S}) {
            assertTrue(R.thrust().damage() > o.thrust().damage(), "strongest thrust");
            assertTrue(R.slash().damage() < o.slash().damage(), "weakest slash");
            assertTrue(R.thrust().reach() > o.thrust().reach() && R.slash().reach() > o.slash().reach(), "longest reach");
            assertTrue(R.totalTicks(AttackKind.SLASH) < o.totalTicks(AttackKind.SLASH), "fastest slash");
            assertTrue(R.totalTicks(AttackKind.THRUST) < o.totalTicks(AttackKind.THRUST), "fastest thrust");
        }
        assertTrue(R.thrust().damage() > 2 * R.slash().damage(), "the rapier is a thrusting weapon");
    }

    @Test
    void cutlassIsShorterHeavierStrongSlashWeakerThrust() {
        for (WeaponDefinition o : new WeaponDefinition[]{R, S}) {
            assertTrue(C.slash().damage() > o.slash().damage(), "strongest slash");
            assertTrue(C.thrust().damage() < o.thrust().damage(), "weakest thrust");
            assertTrue(C.slash().reach() < o.slash().reach() && C.thrust().reach() < o.thrust().reach(), "shortest");
            assertTrue(C.poise() > o.poise(), "heaviest");
            assertTrue(C.totalTicks(AttackKind.SLASH) > o.totalTicks(AttackKind.SLASH), "slowest");
            assertTrue(C.slash().arcDegrees() > o.slash().arcDegrees(), "widest arc");
        }
        assertTrue(C.slash().damage() > C.thrust().damage(), "the cutlass is a slashing weapon");
    }

    @Test
    void saberIsBetweenInEveryNumber() {
        between(R.slash().damage(), S.slash().damage(), C.slash().damage(), "slash damage");
        between(R.thrust().damage(), S.thrust().damage(), C.thrust().damage(), "thrust damage");
        between(R.slash().reach(), S.slash().reach(), C.slash().reach(), "slash reach");
        between(R.thrust().reach(), S.thrust().reach(), C.thrust().reach(), "thrust reach");
        between(R.slash().arcDegrees(), S.slash().arcDegrees(), C.slash().arcDegrees(), "arc");
        between(R.thrust().thickness(), S.thrust().thickness(), C.thrust().thickness(), "thickness");
        between(R.totalTicks(AttackKind.SLASH), S.totalTicks(AttackKind.SLASH), C.totalTicks(AttackKind.SLASH), "slash time");
        between(R.totalTicks(AttackKind.THRUST), S.totalTicks(AttackKind.THRUST), C.totalTicks(AttackKind.THRUST), "thrust time");
        between(R.slash().staminaCost(), S.slash().staminaCost(), C.slash().staminaCost(), "slash cost");
        between(R.thrust().staminaCost(), S.thrust().staminaCost(), C.thrust().staminaCost(), "thrust cost");
        between(R.guard().damageReduction(), S.guard().damageReduction(), C.guard().damageReduction(), "guard");
        between(R.guard().arcDegrees(), S.guard().arcDegrees(), C.guard().arcDegrees(), "guard arc");
        between(R.guard().staminaPerTick(), S.guard().staminaPerTick(), C.guard().staminaPerTick(), "guard drain");
        between(R.parry().failedStaminaCost(), S.parry().failedStaminaCost(), C.parry().failedStaminaCost(), "failed parry");
        between(R.parry().riposteMultiplier(), S.parry().riposteMultiplier(), C.parry().riposteMultiplier(), "riposte");
        between(R.poise(), S.poise(), C.poise(), "poise");
    }

    @Test
    void slashIsFasterThanThrustAndThrustHitsHarderForTheThrustingWeapons() {
        for (WeaponDefinition w : new WeaponDefinition[]{R, C, S}) {
            assertTrue(w.recoveryTicks(AttackKind.SLASH, true) < w.recoveryTicks(AttackKind.THRUST, true), "fast slash recovery");
            assertTrue(w.recoveryTicks(AttackKind.THRUST, false) > w.recoveryTicks(AttackKind.THRUST, true), "long recovery on a miss");
            assertTrue(w.thrust().reach() > w.slash().reach(), "thrust reaches further");
            assertTrue(w.thrust().windupTicks() > w.slash().windupTicks(), "thrust is slower");
        }
    }
}
