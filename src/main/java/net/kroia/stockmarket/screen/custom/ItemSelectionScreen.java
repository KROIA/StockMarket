package net.kroia.stockmarket.screen.custom;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ItemSelectionScreen extends CreativeModeInventoryScreen {

    private final Screen parentScreen;
    private final java.util.function.Consumer<String> onItemSelected;
    private final Set<ResourceLocation> allowedItems;

    public ItemSelectionScreen(Player player, FeatureFlagSet enabledFeatures, boolean displayOperatorCreativeTab,
                               Screen parentScreen, java.util.function.Consumer<String> onItemSelected, ArrayList<String> allowedItemsIDs) {
        super(player, enabledFeatures, displayOperatorCreativeTab);
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        this.allowedItems = new HashSet<>();
        for(String itemId : allowedItemsIDs) {
            this.allowedItems.add(new ResourceLocation(itemId));
        }
    }

    @Override
    public void init() {
        super.init();

        // Filter the slots based on allowed items
        for (Slot slot : this.menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!allowedItems.contains(itemId)) {
                    slot.set(ItemStack.EMPTY); // Clear the slot if not allowed
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left-click
            ItemStack clickedStack = this.hoveredSlot != null ? this.hoveredSlot.getItem() : ItemStack.EMPTY;
            if (!clickedStack.isEmpty()) {
                // Get the Item from the clicked slot
                Item clickedItem = clickedStack.getItem();

                // Check if the item is allowed
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(clickedItem);
                if (allowedItems.contains(itemId)) {
                    onItemSelected.accept(itemId.toString());

                    // Return to parent screen
                    this.minecraft.setScreen(parentScreen);
                    return true;
                } else {
                    // Disallowed item clicked; block interaction
                    return false;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render the standard creative inventory
        super.render(graphics, mouseX, mouseY, partialTick);

        // Highlight disallowed items with a red overlay
        for (Slot slot : this.menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!allowedItems.contains(itemId)) {
                    graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66FF0000); // Semi-transparent red overlay
                }
            }
        }

        // Draw instructions
        graphics.drawCenteredString(this.font, "Select an item by clicking on it", this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
