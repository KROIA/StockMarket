package net.kroia.stockmarket.screen.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CustomItemSelectionScreen extends Screen {
    private final Screen parentScreen;
    private final Set<ResourceLocation> allowedItems;
    private final Consumer<String> onItemSelected;

    private EditBox searchField;
    private List<ItemStack> displayedItems = new ArrayList<>();
    private List<ItemStack> filteredItems = new ArrayList<>();

    private int scrollOffset = 0;
    private static final int ITEMS_PER_ROW = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 10;

    public CustomItemSelectionScreen(Screen parentScreen, ArrayList<String> allowedItemsIDs, Consumer<String> onItemSelected) {
        super(Component.translatable("item_selection.title"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        this.allowedItems = new HashSet<>();
        for(String itemId : allowedItemsIDs) {
            this.allowedItems.add(new ResourceLocation(itemId));
        }
    }

    @Override
    protected void init() {
        super.init();

        // Initialize search field
        this.searchField = new EditBox(this.font, this.width / 2 - 100, PADDING, 200, 20, Component.translatable("item_selection.search"));
        this.searchField.setResponder(this::updateFilter);
        this.addWidget(this.searchField);

        // Populate displayed items
        for (ResourceLocation id : allowedItems) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) {
                displayedItems.add(new ItemStack(item));
            }
        }
        this.filteredItems.addAll(this.displayedItems);

        // Add a back button
        addRenderableWidget(new Button(this.width / 2 - 50, this.height - 30, 100, 20, Component.translatable("gui.back"), button -> {
                    this.minecraft.setScreen(parentScreen);
                }));
    }

    private void updateFilter(String filter) {
        filteredItems.clear();
        if (filter.isEmpty()) {
            filteredItems.addAll(displayedItems);
        } else {
            String lowerFilter = filter.toLowerCase();
            for (ItemStack stack : displayedItems) {
                String name = stack.getHoverName().getString().toLowerCase();
                if (name.contains(lowerFilter)) {
                    filteredItems.add(stack);
                }
            }
        }
    }

    @Override
    //public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) { // 1.20.2
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX) {
        int maxScroll = Math.max(0, (filteredItems.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW - (this.height - 60) / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) deltaX, maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int xStart = (this.width - ITEMS_PER_ROW * ROW_HEIGHT) / 2;
            int yStart = 40;
            for (int i = 0; i < filteredItems.size(); i++) {
                int row = (i / ITEMS_PER_ROW) - scrollOffset;
                if (row < 0 || row >= (this.height - 60) / ROW_HEIGHT) continue;

                int x = xStart + (i % ITEMS_PER_ROW) * ROW_HEIGHT;
                int y = yStart + row * ROW_HEIGHT;

                if (mouseX >= x && mouseX < x + ROW_HEIGHT && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    ItemStack clickedStack = filteredItems.get(i);
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(clickedStack.getItem());
                    onItemSelected.accept(itemId.toString());
                    this.minecraft.setScreen(parentScreen);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    //@Override // 1.20.2
    public void renderBackground(PoseStack pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick)
    {
        //super.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick); // 1.20.2
        super.renderBackground(pGuiGraphics);
    }
    @Override
    public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // Draw search field
        this.searchField.render(graphics, mouseX, mouseY, partialTick);


        // Draw item grid
        int xStart = (this.width - ITEMS_PER_ROW * ROW_HEIGHT) / 2;
        int yStart = 40;

        for (int i = 0; i < filteredItems.size(); i++) {
            int row = (i / ITEMS_PER_ROW) - scrollOffset;
            if (row < 0 || row >= (this.height - 60) / ROW_HEIGHT) continue;

            int x = xStart + (i % ITEMS_PER_ROW) * ROW_HEIGHT;
            int y = yStart + row * ROW_HEIGHT;

            Minecraft.getInstance().getItemRenderer().renderGuiItem(filteredItems.get(i), x, y);
        }



        // Draw tooltips
        for (int i = 0; i < filteredItems.size(); i++) {
            int row = (i / ITEMS_PER_ROW) - scrollOffset;
            if (row < 0 || row >= (this.height - 60) / ROW_HEIGHT) continue;

            int x = xStart + (i % ITEMS_PER_ROW) * ROW_HEIGHT;
            int y = yStart + row * ROW_HEIGHT;

            if (mouseX >= x && mouseX < x + ROW_HEIGHT && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                TooltipFlag flag = this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
                renderTooltip(graphics, filteredItems.get(i), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
