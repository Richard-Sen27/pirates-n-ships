package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.client.AlmanacSceneOpener;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** The Almanac's "Show me" buttons reach Ponder (ALM3 + ALM4): the module's client init installs the handler. */
class AlmanacSceneOpenerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        AlmanacSceneOpener.install(AlmanacSceneOpener.NO_OP);
    }

    @Test
    void theDefaultHandlerOpensNothing() {
        assertFalse(AlmanacSceneOpener.open("pirates_n_ships:cannon_loading"));
    }

    @Test
    void initClientInstallsPondersHandler() {
        new AlmanacPonderModule().initClient();
        assertNotSame(AlmanacSceneOpener.NO_OP, AlmanacSceneOpener.handler());
    }

    @Test
    void anUnknownOrMalformedIdOpensNothing() {
        new AlmanacPonderModule().initClient();
        assertFalse(AlmanacSceneOpener.open("pirates_n_ships:no_such_scene"));
        assertFalse(AlmanacSceneOpener.open("Not An Id!"));
    }
}
