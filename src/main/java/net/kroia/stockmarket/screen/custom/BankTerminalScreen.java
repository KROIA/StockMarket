package net.kroia.stockmarket.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.util.CandleStickChart;
import net.kroia.stockmarket.util.OrderListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BankTerminalScreen extends Screen {
    private class ItemSlot
    {
        public int x;
        public int y;
        public ItemStack stack;
    }

    private static final Component TITLE = Component.translatable("gui." + StockMarketMod.MODID + ".bank_terminal_block_screen");

    private static final ResourceLocation BACKGROUND_IMAGE = new ResourceLocation(StockMarketMod.MODID, "textures/block/stock_market_block.png");
    private int imageWidth = 256; // Width of the image in pixels
    private int imageHeight = 256; // Height of the image in pixels


    private final int slotSize = 18; // Standard slot size
    private int playerInventoryXPos;
    private int playerInventoryYPos;
    private int playerInventoryYHotbarOffset = 58;

    TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(new ResourceLocation(StockMarketMod.MODID, "block/stock_market_clock"));

    private final BankTerminalBlockEntity blockEntity;
    private final Inventory playerInventory;
    private ItemStack draggedStack = ItemStack.EMPTY;


    private boolean mouseClickToggle = false;




    public BankTerminalScreen(BankTerminalBlockEntity blockEntity, Inventory inventory) {
        super(TITLE);
        this.blockEntity = blockEntity;
        this.playerInventory = inventory;


    }

    @Override
    protected void init() {
        super.init();
        playerInventoryXPos = (this.width-9*slotSize) / 2; // Center the inventory horizontally
        playerInventoryYPos = (this.height-4*slotSize-40); // Center the inventory vertically

    }

    @Override
    public void onClose() {
        super.onClose();
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics);

        super.render(graphics, mouseX, mouseY, delta);


        renderPlayerInventory(graphics, mouseX, mouseY);
    }

    private void renderPlayerInventory(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int startX = playerInventoryXPos;
        int startY = playerInventoryYPos;

        // Render the main inventory (3 rows × 9 columns)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + row * 9 + 9; // Main inventory slots start at index 9
                renderSlot(graphics, startX + col * slotSize, startY + row * slotSize, slotIndex);
            }
        }

        // Render the hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            renderSlot(graphics, startX + col * slotSize, startY + playerInventoryYHotbarOffset, col);
        }

        // Render the off-hand slot (1 slot)
        //renderSlot(guiGraphics, startX + 80, startY - 22, 40); // Adjust position as needed

        // Render dragged item
        if (!draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX - 8, mouseY - 8); // Offset by 8 to center the item on the cursor
            graphics.renderItemDecorations(font, draggedStack, mouseX - 8, mouseY - 8);
        }
    }

    private void renderSlot(GuiGraphics guiGraphics, int x, int y, int slotIndex) {
        guiGraphics.fill(x, y, x + slotSize, y + slotSize, 0xFFAAAAAA); // Gray background
        guiGraphics.fill(x + 1, y + 1, x + slotSize - 1, y + slotSize - 1, 0xFF000000); // Black inner

        // Get the item in this slot
        ItemStack stack = playerInventory.getItem(slotIndex);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 1, y + 1); // Render the item
            guiGraphics.renderItemDecorations(font, stack, x + 1, y + 1); // Render stack count
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !mouseClickToggle) { // Left mouse button
            mouseClickToggle = true;
            if(draggedStack.isEmpty()) {
                for (int slotIndex = 0; slotIndex < playerInventory.getContainerSize(); slotIndex++) {
                    int x = getSlotX(slotIndex);
                    int y = getSlotY(slotIndex);

                    if (isMouseOverSlot(mouseX, mouseY, x, y)) {
                        ItemStack stack = playerInventory.getItem(slotIndex);
                        if (!stack.isEmpty()) {
                            draggedStack = stack; // Start dragging the item
                            playerInventory.setItem(slotIndex, ItemStack.EMPTY); // Temporarily remove the item from the slot
                        }

                        return true;
                    }
                }
            }
            else
            {
                for (int slotIndex = 0; slotIndex < playerInventory.getContainerSize(); slotIndex++) {
                    int x = getSlotX(slotIndex);
                    int y = getSlotY(slotIndex);

                    if (isMouseOverSlot(mouseX, mouseY, x, y)) {
                        playerInventory.setItem(slotIndex, draggedStack); // Place the item in the slot
                        draggedStack = ItemStack.EMPTY; // Clear the dragged stack
                        return true;
                    }
                }

                // If not dropped on a slot, return it to the player's inventory
                // Adjust this logic based on desired behavior
                draggedStack = ItemStack.EMPTY;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    /*@Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggedStack.isEmpty()) {
            draggedMouseX = (int) mouseX;
            draggedMouseY = (int) mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }*/





    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(button == 0)
        {
            mouseClickToggle = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }


    private int getSlotX(int slotIndex) {
        // Calculate X based on inventory layout (hotbar, main inventory, etc.)
        int col = slotIndex % 9;
        return playerInventoryXPos + col * slotSize;
    }

    private int getSlotY(int slotIndex) {
        // Calculate Y based on inventory layout
        int row = slotIndex / 9;
        if (row == 0) return playerInventoryYPos + playerInventoryYHotbarOffset; // Hotbar
        if (row >= 1) return playerInventoryYPos + (row - 1) * slotSize;
        return playerInventoryYPos - 22; // Off-hand slot
    }

    private boolean isMouseOverSlot(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + slotSize &&
                mouseY >= y && mouseY < y + slotSize;
    }

    private void handleSlotClick(int slotIndex, int button) {
        // Logic to handle slot interaction (e.g., picking up or placing items)
        ItemStack heldStack = minecraft.player.containerMenu.getCarried();
        if (button == 0) { // Left click
            if (heldStack.isEmpty()) {
                // Pick up item from the slot
                minecraft.player.containerMenu.setCarried(playerInventory.getItem(slotIndex));
                playerInventory.setItem(slotIndex, ItemStack.EMPTY);
            } else {
                // Place the held item in the slot
                playerInventory.setItem(slotIndex, heldStack.copy());
                minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
    }

    /*
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Do something
        }
    }*/

    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Bind the texture
       // RenderSystem.setShaderTexture(0, MY_IMAGE);

        // Calculate the position to center the image
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        // Draw the image
        //graphics.blit(centerX, centerY, 0, 0, imageWidth, imageHeight);
        graphics.blit(BACKGROUND_IMAGE, centerX, centerY, 0, 0, imageWidth, imageHeight);
    }*/
}