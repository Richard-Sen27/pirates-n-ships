package com.richardsenger.piratesnships.core.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static void name(GameTestHelper helper)} method as a GameTest.
 *
 * <p>Why not vanilla's {@code @GameTest}: the template namespace of an annotated vanilla test comes from NeoForge's
 * {@code @GameTestHolder}, which common can't use, so it would default to {@code minecraft} and be filtered out.
 * Instead every test class has one generator method that turns these annotations into test functions in our
 * namespace:
 *
 * <pre>{@code
 * public final class ShipGameTests {
 *     @GameTestGenerator
 *     public static Collection<TestFunction> tests() { return ModGameTests.of(ShipGameTests.class); }
 *
 *     @ModGameTest(template = GameTestTemplates.EMPTY_9)
 *     public static void hullFloats(GameTestHelper helper) { ...; helper.succeed(); }
 * }
 * }</pre>
 *
 * Then return the class from the module's {@code gameTestClasses()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ModGameTest {

    /** Structure template id (see {@link GameTestTemplates}). */
    String template() default GameTestTemplates.EMPTY_3;

    int timeoutTicks() default 100;

    long setupTicks() default 0L;

    /** Optional tests may fail without failing the run. */
    boolean required() default true;

    String batch() default "pirates_n_ships";
}
