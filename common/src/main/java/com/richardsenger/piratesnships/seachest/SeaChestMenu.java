package com.richardsenger.piratesnships.seachest;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The vanilla six-row chest menu ({@code generic_9x6}, so the client shows vanilla's double-chest screen and needs no
 * menu type of ours), with the chest's 54 slots refusing other sea chests: a chest inside a chest would nest
 * contents without limit. The client builds a plain {@link ChestMenu}; a refused click there is corrected by the
 * server's slot sync.
 */
public class SeaChestMenu extends ChestMenu {

    public SeaChestMenu(int containerId, Inventory inventory, Container chest) {
        super(MenuType.GENERIC_9x6, containerId, inventory, chest, 6);
        for (int i = 0; i < SeaChestContents.SIZE; i++) {
            Slot old = slots.get(i);
            Slot guarded = new Slot(chest, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SeaChestContents.canHold(stack);
                }
            };
            guarded.index = i;
            slots.set(i, guarded);
        }
    }
}
