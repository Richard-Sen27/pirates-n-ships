package com.richardsenger.piratesnships.crew.provisions;

/** What a spoiled food item in a pantry turns into (config {@code provisions.spoiled_food_result}). */
public enum SpoiledFood {
    /** The item vanishes (bowls and other leftovers stay). */
    NOTHING,
    /** One rotten flesh per spoiled item, which the crew never eats and a hopper below the pantry takes out. */
    ROTTEN_FLESH
}
