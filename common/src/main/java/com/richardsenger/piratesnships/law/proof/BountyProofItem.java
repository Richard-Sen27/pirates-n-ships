package com.richardsenger.piratesnships.law.proof;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The bounty proof item. All data lives in {@link ProofContent#BOUNTY_PROOF_DATA}; a stack without it is blank. */
public class BountyProofItem extends Item {

    public static final String TOOLTIP_TARGET = "item." + Constants.MOD_ID + ".bounty_proof.target";
    public static final String TOOLTIP_KILLER = "item." + Constants.MOD_ID + ".bounty_proof.killer";
    public static final String TOOLTIP_BLANK = "item." + Constants.MOD_ID + ".bounty_proof.blank";

    public BountyProofItem(Properties properties) {
        super(properties);
    }

    /** The proof data of a stack, or {@code null} if it is not a (filled) proof. */
    public static @Nullable BountyProof proofOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BountyProofItem)) return null;
        return stack.get(ProofContent.BOUNTY_PROOF_DATA.get());
    }

    /** A new proof stack for a kill. */
    public static ItemStack create(BountyProof proof) {
        ItemStack stack = new ItemStack(ProofContent.BOUNTY_PROOF.get());
        stack.set(ProofContent.BOUNTY_PROOF_DATA.get(), proof);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BountyProof proof = stack.get(ProofContent.BOUNTY_PROOF_DATA.get());
        if (proof == null) {
            tooltip.add(Component.translatable(TOOLTIP_BLANK).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(TOOLTIP_TARGET, proof.targetName()).withStyle(ChatFormatting.GRAY));
        if (!proof.killerName().isEmpty()) {
            tooltip.add(Component.translatable(TOOLTIP_KILLER, proof.killerName()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
