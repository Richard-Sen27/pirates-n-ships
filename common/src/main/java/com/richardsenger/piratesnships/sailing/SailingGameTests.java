package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.wind.WindParams;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import com.richardsenger.piratesnships.sailing.wind.WindService;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/** World-side checks of the wind service (the force model itself is covered by JUnit). */
public final class SailingGameTests {

    /** Weather tests change the level's weather, so they run in their own batch, apart from other tests. */
    private static final String WEATHER_BATCH = "pirates_n_ships_weather";

    private SailingGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTests.class);
    }

    @ModGameTest
    public static void windServiceReturnsSaneSample(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5));
        WindParams p = SailingConfig.windParams();
        WindSample a = WindService.sample(level, pos);
        WindSample b = WindService.sample(level, pos);
        helper.assertTrue(a.equals(b), "Wind must be deterministic for the same inputs");
        helper.assertTrue(Double.isFinite(a.strength()) && a.strength() >= 0.0, "Wind strength must be finite and non-negative");
        double max = p.maxStrength() * Math.max(Math.max(1.0, p.rainMultiplier()), p.thunderMultiplier())
                * (1.0 + p.gustStrength()) * (1.0 + p.regionalStrengthFraction()) + 1e-6;
        helper.assertTrue(a.strength() <= max, "Wind strength " + a.strength() + " above the possible maximum " + max);
        double len = Math.hypot(a.dirX(), a.dirZ());
        helper.assertTrue(Math.abs(len - 1.0) < 1e-6, "Wind direction must be a unit vector");
        helper.assertTrue(a.towardDegrees() >= 0.0 && a.towardDegrees() < 360.0, "Compass bearing out of range");
        helper.succeed();
    }

    @ModGameTest(batch = WEATHER_BATCH)
    public static void windFollowsLevelWeather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        float rain = level.getRainLevel(1.0f);
        float thunder = level.getThunderLevel(1.0f) / Math.max(rain, 1e-6f);
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5));
        WindParams p = SailingConfig.windParams();
        try {
            level.setRainLevel(0.0f);
            level.setThunderLevel(0.0f);
            WindSample clear = WindService.sample(level, pos);
            level.setRainLevel(1.0f);
            WindSample rainy = WindService.sample(level, pos);
            level.setThunderLevel(1.0f);
            WindSample storm = WindService.sample(level, pos);

            helper.assertTrue(Math.abs(clear.weatherMultiplier() - 1.0) < 1e-6, "Clear weather multiplier should be 1");
            helper.assertTrue(Math.abs(rainy.weatherMultiplier() - p.rainMultiplier()) < 1e-6,
                    "Rain multiplier should be " + p.rainMultiplier() + " but was " + rainy.weatherMultiplier());
            helper.assertTrue(Math.abs(storm.weatherMultiplier() - p.thunderMultiplier()) < 1e-6,
                    "Thunder multiplier should be " + p.thunderMultiplier() + " but was " + storm.weatherMultiplier());
            helper.assertTrue(Math.abs(rainy.strength() - clear.strength() * p.rainMultiplier()) < 1e-6,
                    "Rain should scale the strength");
            helper.assertTrue(storm.strength() >= clear.strength() * p.thunderMultiplier() - 1e-6,
                    "Thunder should scale the strength (gusts only add)");
            helper.assertTrue(clear.gust() == 0.0 && rainy.gust() == 0.0, "No gusts outside thunderstorms");
        } finally {
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
        }
        helper.succeed();
    }
}
