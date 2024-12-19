package net.kroia.stockmarket.util.geometry;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;

public class Rectangle {
    public int x;
    public int y;
    public int width;
    public int height;
    public Rectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(PoseStack graphics, int color) {
        GuiComponent.fill(graphics,x, y, x + width, y + height, color);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x &&
                mouseX < x + width &&
                mouseY >= y &&
                mouseY < y + height;
    }
}
