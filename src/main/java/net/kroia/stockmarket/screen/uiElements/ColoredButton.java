package net.kroia.stockmarket.screen.uiElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class ColoredButton extends Button {

    public int hoverColor = 0xFF00FF00;
    public int normalColor = 0xFF008800;
    public int textColor = 0xFFFFFFFF;

    public static ColoredButton.Builder builder(Component pMessage, OnPress pOnPress) {
        return new ColoredButton.Builder(pMessage, pOnPress);
    }

    protected ColoredButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, OnPress pOnPress, CreateNarration pCreateNarration) {
        super(pX, pY, pWidth, pHeight, pMessage, pOnPress, pCreateNarration);
    }
    protected ColoredButton(Builder builder) {
        super(builder);
    }

    // Implement builder
    @OnlyIn(Dist.CLIENT)
    public static class Builder extends Button.Builder {
        private int hoverColor = 0xFF00FF00;
        private int normalColor = 0xFF008800;
        private int textColor = 0xFFFFFFFF;
        public Builder(Component pMessage, Button.OnPress onPress) {
            super(pMessage, onPress);
        }

        public Builder hoverColor(int hoverColor) {
            this.hoverColor = hoverColor;
            return this;
        }

        public Builder normalColor(int normalColor) {
            this.normalColor = normalColor;
            return this;
        }

        public Builder textColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        @Override
        public @NotNull Button build() {
            ColoredButton button = new ColoredButton(this);
            button.hoverColor = this.hoverColor;
            button.normalColor = this.normalColor;
            button.textColor = this.textColor;
            return button;
        }

    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = this.isHoveredOrFocused();
        int color = hovered ? hoverColor : normalColor; // Bright green when hovered, dark green otherwise

        // Draw the button background
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);

        // Draw the button's text
        guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                this.getMessage().getString(),
                this.getX() + this.getWidth() / 2,
                this.getY() + (this.getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
                textColor // White text
        );
    }
}

