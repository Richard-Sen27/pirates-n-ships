package com.richardsenger.piratesnships.crew.galley;

import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;

/**
 * A block entity that holds ship provisions: the pantry and the water barrel. {@link ShipProvisions} combines several
 * of them into one store and writes the result back through this interface. Server side only.
 */
public interface ProvisionContainer {

    /**
     * Brings the container's own clock to {@code gameTime}: spoilage up to then is applied to the real items, and the
     * store is in step with them. Returns the store at {@code gameTime}.
     */
    ProvisionStore catchUp(long gameTime, ProvisionSettings s);

    /**
     * The store with its lot ages as they were at {@code anchorTime}, for a combined update over a period starting
     * there. Catches up first when the container's clock is behind {@code anchorTime}; when it is ahead (the
     * container already aged through part of the period on its own), the ages are wound back so nothing ages twice.
     */
    ProvisionStore storeAt(long anchorTime, ProvisionSettings s);

    /**
     * Takes a share of a combined update out of the real contents and keeps {@code remaining} as the new store, aged up
     * to {@code gameTime}.
     */
    void applyShare(ProvisionPool.Share share, ProvisionStore remaining, long gameTime, ProvisionSettings s);

    /** Cargo weight of the contents (design.md §4.9). */
    default double provisionsWeight(long gameTime, ProvisionSettings s) {
        return catchUp(gameTime, s).totalWeight();
    }
}
