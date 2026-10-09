package com.richardsenger.piratesnships.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.hammock.HammockBlock;
import java.lang.reflect.Method;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.junit.jupiter.api.Test;

/**
 * SLP1: {@link HammockBlock} declares NeoForge's {@link IBlockExtension} bed methods by name and parameters only (common
 * code cannot import NeoForge), so nothing would notice if NeoForge changed a signature and they silently stopped
 * overriding. Without {@code isBed} vanilla's bed check wakes a sleeper in a hammock every tick. This pins each one
 * against the NeoForge classes the mod runs with.
 */
class HammockBedExtensionTest {

    private static void assertOverrides(String name) {
        Method ext = null;
        for (Method m : IBlockExtension.class.getMethods()) {
            if (m.getName().equals(name)) {
                ext = m;
                break;
            }
        }
        assertTrue(ext != null, "IBlockExtension has no method " + name);
        assertTrue(IBlockExtension.class.isAssignableFrom(Block.class), "Block does not implement IBlockExtension");
        Method mine;
        try {
            mine = HammockBlock.class.getMethod(name, ext.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new AssertionError("HammockBlock does not override IBlockExtension#" + name + " " + ext, e);
        }
        assertEquals(HammockBlock.class, mine.getDeclaringClass(), "IBlockExtension#" + name + " is not overridden");
        assertEquals(ext.getReturnType(), mine.getReturnType(), "return type of " + name);
    }

    @Test
    void overridesIsBed() {
        assertOverrides("isBed");
    }

    @Test
    void overridesSetBedOccupied() {
        assertOverrides("setBedOccupied");
    }

    @Test
    void overridesGetRespawnPosition() {
        assertOverrides("getRespawnPosition");
    }
}
