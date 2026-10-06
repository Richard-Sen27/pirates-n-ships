package com.richardsenger.piratesnships.crew.provisions;

/** The new store, the new crew state and what happened, returned by {@link ProvisionRules#advance}. */
public record ProvisionUpdate(ProvisionStore store, ProvisioningState state, ProvisionOutcome outcome) {
}
