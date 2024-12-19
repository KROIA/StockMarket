package net.kroia.stockmarket.screen.uiElements;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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


    public ColoredButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, OnPress pOnPress, int hoverColor, int normalColor, int textColor) {
        super(pX, pY, pWidth, pHeight, pMessage, pOnPress);
        this.hoverColor = hoverColor;
        this.normalColor = normalColor;
        this.textColor = textColor;
    }


    @Override
    public void render(PoseStack guiGraphics, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = this.isHoveredOrFocused();
        int color = hovered ? hoverColor : normalColor; // Bright green when hovered, dark green otherwise

        // Draw the button background
        fill(guiGraphics, this.x, this.y, this.x + this.width, this.y + this.height, color);

        // Draw the button's text
        drawCenteredString(guiGraphics,
                Minecraft.getInstance().font,
                this.getMessage().getString(),
                this.x + this.getWidth() / 2,
                this.y + (this.getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
                textColor // White text
        );
    }
}

