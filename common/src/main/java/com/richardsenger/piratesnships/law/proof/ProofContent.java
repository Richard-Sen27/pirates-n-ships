package com.richardsenger.piratesnships.law.proof;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registry content of the bounty proof (docs/design.md §13.2). */
public final class ProofContent {

    /** Who was killed, when and by whom. Synced so the tooltip can show it. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<BountyProof>> BOUNTY_PROOF_DATA =
            ModRegistry.dataComponent("bounty_proof", b -> b.persistent(BountyProof.CODEC).networkSynchronized(BountyProof.STREAM_CODEC));

    /** A sealed death warrant: proof that a bounty target was killed. No recipe; only given on a kill. */
    public static final RegistryEntry<Item, BountyProofItem> BOUNTY_PROOF = ModRegistry.item("bounty_proof",
            () -> new BountyProofItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ProofContent() {
    }

    /** Called from {@code LawModule.registerContent()}. */
    public static void init() {
    }
}
