package com.richardsenger.piratesnships.ship.decor.wood;

/**
 * A block of a {@link WoodFamily} that knows its wood (WOOD1, docs/woods.md): renderers pick the wood's child model
 * ({@code WoodModels}), recipes and behaviour can ask which wood a placed member is.
 */
public interface WoodBlock {

    /** The wood this member is made of. */
    ShipWood wood();
}
