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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BankTerminalScreen extends Screen {

    private static final Component TITLE = Component.translatable("gui." + StockMarketMod.MODID + ".bank_terminal_block_screen");

    private static final ResourceLocation MY_IMAGE = new ResourceLocation(StockMarketMod.MODID, "textures/block/stock_market_block.png");
    private int imageWidth = 256; // Width of the image in pixels
    private int imageHeight = 256; // Height of the image in pixels

    TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(new ResourceLocation(StockMarketMod.MODID, "block/stock_market_clock"));

    private static BankTerminalBlockEntity blockEntity;

    public BankTerminalScreen(BankTerminalBlockEntity blockEntity) {
        super(TITLE);
        BankTerminalScreen.blockEntity = blockEntity;


    }

    @Override
    protected void init() {
        super.init();

    }

    @Override
    public void onClose() {
        super.onClose();
    }

    /*
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Do something
        }
    }*/

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        /*// Background is typically rendered first
        this.renderBackground(graphics);

        // Render things here before widgets (background textures)
        // You can draw additional things like text, images, etc.

        // Render the widgets (buttons, labels, etc.)
        super.render(graphics, mouseX, mouseY, partialTick);

        StockMarketMod.LOGGER.warn("Rendering Bank Terminal Screen");*/

        super.render(graphics, mouseX, mouseY, partialTick);
        // Bind the texture
       // RenderSystem.setShaderTexture(0, MY_IMAGE);

        // Calculate the position to center the image
        int centerX = (this.width - imageWidth) / 2;
        int centerY = (this.height - imageHeight) / 2;

        // Draw the image
        //graphics.blit(centerX, centerY, 0, 0, imageWidth, imageHeight);
        graphics.blit(MY_IMAGE, centerX, centerY, 0, 0, imageWidth, imageHeight);
    }
}