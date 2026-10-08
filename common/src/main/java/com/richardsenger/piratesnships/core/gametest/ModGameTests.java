package com.richardsenger.piratesnships.core.gametest;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Builds vanilla {@link TestFunction}s from {@link ModGameTest} methods. */
public final class ModGameTests {

    private ModGameTests() {
    }

    /**
     * All {@link ModGameTest} methods of {@code testClass}, named {@code pirates_n_ships.<class>.<method>}
     * (lower case). Use from a {@code @GameTestGenerator} method.
     */
    /**
     * Scoped runs: the system property {@code pirates_n_ships.gametest.only} (set it on the forked server through
     * {@code JAVA_TOOL_OPTIONS="-Dpirates_n_ships.gametest.only=FlagGameTests,FlagStackGameTests"}) lists the test
     * classes to register, by simple name, case-insensitive; every other class registers nothing. Unset: everything
     * runs. Agents verify their own classes this way; the merge stage always runs the whole suite.
     */
    public static final String ONLY_PROPERTY = "pirates_n_ships.gametest.only";

    static boolean selected(String simpleClassName, String only) {
        if (only == null || only.isBlank()) return true;
        for (String part : only.split(",")) {
            if (part.trim().equalsIgnoreCase(simpleClassName)) return true;
        }
        return false;
    }

    public static Collection<TestFunction> of(Class<?> testClass) {
        List<TestFunction> tests = new ArrayList<>();
        if (!selected(testClass.getSimpleName(), System.getProperty(ONLY_PROPERTY))) return tests;
        Method[] methods = testClass.getDeclaredMethods();
        java.util.Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method m : methods) {
            ModGameTest a = m.getAnnotation(ModGameTest.class);
            if (a == null) continue;
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1 || m.getParameterTypes()[0] != GameTestHelper.class) {
                throw new IllegalStateException("@ModGameTest must be public static void " + m.getName() + "(GameTestHelper): " + testClass.getName());
            }
            String name = Constants.MOD_ID + "." + testClass.getSimpleName().toLowerCase() + "." + m.getName().toLowerCase();
            tests.add(new TestFunction(a.batch(), name, a.template(), Rotation.NONE, a.timeoutTicks(), a.setupTicks(), a.required(),
                    helper -> invoke(m, helper)));
        }
        return tests;
    }

    private static void invoke(Method m, GameTestHelper helper) {
        try {
            m.invoke(null, helper);
        } catch (InvocationTargetException e) {
            // Unwrap so GameTestAssertExceptions are reported as test failures with their message
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
