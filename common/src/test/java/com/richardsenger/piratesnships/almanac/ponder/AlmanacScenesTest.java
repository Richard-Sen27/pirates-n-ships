package com.richardsenger.piratesnships.almanac.ponder;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The "Show me" id mapping of our Ponder scenes (ALM4), without Ponder. */
class AlmanacScenesTest {

    @Test
    void theCannonSceneHasTheIdTheAlmanacCarries() {
        // ALM2's ShowMe element carries exactly this id
        assertEquals(ResourceLocation.parse("pirates_n_ships:cannon_loading"), AlmanacScenes.CANNON_LOADING);
        AlmanacScenes.Scene scene = AlmanacScenes.byId(AlmanacScenes.CANNON_LOADING).orElseThrow();
        assertEquals(ResourceLocation.parse("pirates_n_ships:cannon"), scene.component());
        assertEquals(AlmanacScenes.CANNON_LOADING, scene.schematic());
    }

    @Test
    void aSceneIdOpensThatScene() {
        AlmanacScenes.Target target = AlmanacScenes.resolve(AlmanacScenes.CANNON_LOADING).orElseThrow();
        assertEquals(AlmanacScenes.CANNON_ITEM, target.component());
        assertSame(AlmanacScenes.byId(AlmanacScenes.CANNON_LOADING).orElseThrow(), target.scene());
    }

    @Test
    void anItemIdWithScenesOpensTheItemsList() {
        AlmanacScenes.Target target = AlmanacScenes.resolve(AlmanacScenes.CANNON_ITEM).orElseThrow();
        assertEquals(AlmanacScenes.CANNON_ITEM, target.component());
        assertNull(target.scene());
        assertEquals(1, AlmanacScenes.forComponent(AlmanacScenes.CANNON_ITEM).size());
    }

    @Test
    void unknownIdsResolveToNothing() {
        assertEquals(Optional.empty(), AlmanacScenes.resolve(ResourceLocation.parse("pirates_n_ships:no_such_scene")));
        assertEquals(Optional.empty(), AlmanacScenes.resolve(ResourceLocation.parse("minecraft:stone")));
        assertTrue(AlmanacScenes.forComponent(ResourceLocation.parse("minecraft:stone")).isEmpty());
    }

    @Test
    void sceneIdsAndSchematicsAreUnique() {
        Set<ResourceLocation> ids = new HashSet<>();
        Set<ResourceLocation> schematics = new HashSet<>();
        for (AlmanacScenes.Scene scene : AlmanacScenes.ALL) {
            assertTrue(ids.add(scene.id()), "duplicate scene id " + scene.id());
            // AlmanacPonder.show finds a scene's storyboard by its item and schematic
            assertTrue(schematics.add(scene.schematic()), "duplicate schematic " + scene.schematic());
            assertEquals("pirates_n_ships", scene.id().getNamespace());
        }
    }
}
