package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CustomItemSelectionScreen extends GuiScreen {
    private class ItemButton extends ItemView {

        public ItemButton(ItemStack stack) {
            super(stack);
        }

        @Override
        public void renderBackground()
        {
            super.renderBackground();
            if(isMouseOver())
            {
                drawRect(0,0,getWidth(),getHeight(),0x80FFFFFF);
            }
        }
        @Override
        public boolean mouseClickedOverElement(int button) {
            if (button == 0) {
                onItemSelected.accept(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString());
                minecraft.setScreen(parentScreen);
                return true;
            }
            return false;
        }
    }

    private static final int menuWidth = 200;
    private final GuiScreen parentScreen;
    private final Set<ItemStack> allowedItems;
    private final Consumer<String> onItemSelected;

    private final TextBox searchField;
    private final ListView listView;
    private final Button backButton;



    public CustomItemSelectionScreen(GuiScreen parentScreen, ArrayList<String> allowedItemsIDs, Consumer<String> onItemSelected, Component title) {
        super(title);
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        this.allowedItems = new HashSet<>();
        for(String itemId : allowedItemsIDs) {
            this.allowedItems.add(ItemUtilities.createItemStackFromId(itemId));
        }

        searchField = new TextBox();
        searchField.setOnTextChanged(this::updateFilter);
        listView = new VerticalListView();
        listView.setLayout(new LayoutGrid(1, 0, false, false,0,menuWidth/20, GuiElement.Alignment.TOP));
        backButton = new Button(TradeScreen.BACK_BUTTON.getString());
        backButton.setOnFallingEdge(() -> minecraft.setScreen(parentScreen));

        addElement(searchField);
        addElement(listView);
        addElement(backButton);

        updateFilter();
    }

    @Override
    protected void updateLayout(Gui gui) {

        int width = getWidth();
        searchField.setBounds((width - menuWidth) / 2, 10, menuWidth, 20);
        listView.setBounds((width - menuWidth) / 2, 40, menuWidth, getHeight() -80);
        backButton.setBounds((width - menuWidth) / 2, getHeight() - 30, menuWidth, 20);
    }

    private void updateFilter() {
        String filter = searchField.getText();
        listView.removeChilds();
        listView.getLayout().enabled = false;
        if (filter.isEmpty()) {
            for (ItemStack stack : allowedItems) {
                listView.addChild(new ItemButton(stack));
            }
        } else {
            String lowerFilter = filter.toLowerCase();
            for (ItemStack stack : allowedItems) {
                String name = stack.getHoverName().getString().toLowerCase();
                if (name.contains(lowerFilter)) {
                    listView.addChild(new ItemButton(stack));
                }
            }
        }
        listView.getLayout().enabled = true;
        listView.layoutChangedInternal();
    }
}
