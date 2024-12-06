package net.kroia.stockmarket.screen.custom;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;

public class ItemSelectionScreen extends CreativeModeInventoryScreen {

    private final Screen parentScreen;
    private final java.util.function.Consumer<String> onItemSelected;

    public ItemSelectionScreen(Player player, FeatureFlagSet enabledFeatures, boolean displayOperatorCreativeTab,
                               Screen parentScreen, java.util.function.Consumer<String> onItemSelected) {
        super(player, enabledFeatures, displayOperatorCreativeTab);
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left-click
            ItemStack clickedStack = this.hoveredSlot != null ? this.hoveredSlot.getItem() : ItemStack.EMPTY;
            if (!clickedStack.isEmpty()) {
                // Get the Item from the clicked slot
                Item clickedItem = clickedStack.getItem();

                String itemId = clickedItem.getDescriptionId();
                onItemSelected.accept(itemId);

                // Return to parent screen
                this.minecraft.setScreen(parentScreen);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render the standard creative inventory
        super.render(graphics, mouseX, mouseY, partialTick);

        // Optionally, draw additional UI elements (e.g., title, instructions)
        graphics.drawCenteredString(this.font, "Select an item by clicking on it", this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
