package com.richardsenger.piratesnships.combat.melee.client.anim;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule.Mode.*;
import static com.richardsenger.piratesnships.combat.melee.client.anim.FirstPersonRule.animateFirstPerson;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonRuleTest {

    static boolean rule(FirstPersonRule.Mode mode, String... loaded) {
        Set<String> mods = Set.of(loaded);
        return animateFirstPerson(mode, mods::contains);
    }

    @Test
    void autoIsOnWithoutBodyCameraMods() {
        assertTrue(rule(AUTO));
        assertTrue(rule(AUTO, "player_animation_library", "sable", "jei"));
    }

    @Test
    void autoTurnsOffForEachBodyCameraMod() {
        assertFalse(rule(AUTO, "firstperson"));
        assertFalse(rule(AUTO, "realcamera"));
        assertFalse(rule(AUTO, "jei", "realcamera", "firstperson"));
    }

    @Test
    void onAndOffIgnoreTheModList() {
        assertTrue(rule(ON, "firstperson", "realcamera"));
        assertTrue(rule(ON));
        assertFalse(rule(OFF));
        assertFalse(rule(OFF, "jei"));
    }
}
