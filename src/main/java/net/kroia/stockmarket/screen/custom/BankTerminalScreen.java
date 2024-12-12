package net.kroia.stockmarket.screen.custom;

import com.mojang.datafixers.util.Pair;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.bank.ClientBankManager;
import net.kroia.stockmarket.menu.custom.BankTerminalContainerMenu;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBankDataPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateBankTerminalBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBankDataPacket;
import net.kroia.stockmarket.util.geometry.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;

@Mod.EventBusSubscriber(modid = StockMarketMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BankTerminalScreen extends AbstractContainerScreen<BankTerminalContainerMenu> {
    private class BankElement
    {
        private static final Component RECEIVE_ITEMS_FROM_MARKET_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".bank_terminal_screen.bank_element.receive_items_from_market_button");

        public static final int HEIGHT = 18;
        public static final int textEditWidth = 100;
        private int x;
        private int y;
        private int width;
        private int targetAmount = 0;
        public ItemStack stack;
        public final String itemID;

        public EditBox amountBox;
        public Button receiveItemsFromMarketButton;

        BankTerminalScreen parent;

        public BankElement(BankTerminalScreen parent, int x, int y, int width, ItemStack stack, String itemID)
        {
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.width = width;
            this.stack = stack;
            this.itemID = itemID;

            int boxPadding = 2;

            this.amountBox = new EditBox(Minecraft.getInstance().font, x+width-textEditWidth + boxPadding, y+boxPadding, textEditWidth-2*boxPadding, HEIGHT-2*boxPadding, Component.nullToEmpty(""));
            this.amountBox.setMaxLength(10); // Max length of input
            this.amountBox.setFilter(input -> input.matches("\\d*")); // Allow only digits

            receiveItemsFromMarketButton = Button.builder(RECEIVE_ITEMS_FROM_MARKET_BUTTON_TEXT, this::onReceiveItemsFromMarket)
                    .bounds(x+width-textEditWidth-100, y, 100, HEIGHT).build();
        }

        public void render(GuiGraphics graphics)
        {
            int amount = stack.getCount();
            if(amount == 0)
                stack.setCount(1);
            graphics.renderItem(stack, x+1, y+1);
            if(amount == 0)
                stack.setCount(0);
            String amountStr = "" + amount;
            int fontHeight = Minecraft.getInstance().font.lineHeight;
            graphics.drawString(Minecraft.getInstance().font, amountStr, x + HEIGHT+2, y + (HEIGHT - fontHeight)/2, 0xFFFFFF);

            amountBox.render(graphics, 0, 0, 0);
        }
        public void setY(int y)
        {
            this.y = y;
            amountBox.setY(y);
        }
        public int getY()
        {
            return y;
        }
        public int getTargetAmount()
        {
            saveAmount();
            return targetAmount;
        }
        public void setTargetAmount(int amount)
        {
            this.targetAmount = amount;
            amountBox.setValue(""+amount);
        }
        private void saveAmount() {
            // Retrieve the value from the EditBox
            String text = this.amountBox.getValue();

            if (!text.isEmpty()) {
                try {
                    this.targetAmount = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    // Handle invalid input (shouldn't happen due to input filter)
                    this.targetAmount = 0;
                }
            } else {
                // Handle empty input
                this.targetAmount = 0;
            }
        }
        private void onReceiveItemsFromMarket(Button pButton) {
            //StockMarketMod.LOGGER.info("Sending item: "+itemID + " amount: "+getTargetAmount());
            HashMap<String, Integer> itemTransferToMarketAmounts = new HashMap<>();
            itemTransferToMarketAmounts.put(itemID, getTargetAmount());
            UpdateBankTerminalBlockEntityPacket.sendPacketToServer(parent.menu.getBlockPos(), itemTransferToMarketAmounts, false);
            //RequestOrderPacket.generateRequest(itemID, getTargetAmount());
        }
    }
    private static final ResourceLocation TEXTURE = new ResourceLocation(StockMarketMod.MODID, "textures/gui/example_menu.png");

    private static final Component SEND_ITEMS_TO_MARKET_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".bank_terminal_screen.send_items_to_market_button");
    private static final Component RECEIVE_ITEMS_FROM_MARKET_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".bank_terminal_screen.receive_items_from_market_button");


    private int lastTickCount = 0;
    private int tickCount = 0;
    //private boolean mouseClickToggle = false;
    private final ArrayList<BankElement> bankElements = new ArrayList<>();

    private int scrollOffset = 0;
    private int visibleCount;



    private Rectangle receiveItemsFromMarketButtonRect;
    private Rectangle sendItemsToMarketButtonRect;
    private Rectangle balanceLabelRect;
    private Rectangle itemListViewRect;
    private Rectangle receiveWindowBackgroundRect;

    public BankTerminalScreen(BankTerminalContainerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);

        this.imageWidth = 176;
        this.imageHeight = 166;

        super.titleLabelX += BankTerminalContainerMenu.POS_X;
        super.titleLabelY += BankTerminalContainerMenu.POS_Y;
        super.inventoryLabelX += BankTerminalContainerMenu.POS_X;
        super.inventoryLabelY += BankTerminalContainerMenu.POS_Y;
    }

    @Override
    public void init() {
        super.init();
        sendItemsToMarketButtonRect = new Rectangle(this.width/2,10,this.imageWidth,20);

        int padding = 5;
        receiveWindowBackgroundRect = new Rectangle(5,5,200,this.height-2*padding);
        receiveItemsFromMarketButtonRect = new Rectangle(receiveWindowBackgroundRect.x+padding,receiveWindowBackgroundRect.y+padding,receiveWindowBackgroundRect.width-2*padding,20);
        balanceLabelRect = new Rectangle(receiveWindowBackgroundRect.x+padding,receiveWindowBackgroundRect.y+40,receiveWindowBackgroundRect.width-2*padding,this.font.lineHeight);
        itemListViewRect = new Rectangle(receiveWindowBackgroundRect.x+padding,receiveWindowBackgroundRect.y+60,receiveWindowBackgroundRect.width-2*padding,
                receiveWindowBackgroundRect.height-60-padding);



        addRenderableWidget(Button.builder(SEND_ITEMS_TO_MARKET_BUTTON_TEXT, this::onTransmittItemsToMarket)
                .bounds(sendItemsToMarketButtonRect.x, sendItemsToMarketButtonRect.y, sendItemsToMarketButtonRect.width, sendItemsToMarketButtonRect.height).build());
        addRenderableWidget(Button.builder(RECEIVE_ITEMS_FROM_MARKET_BUTTON_TEXT, this::onReceiveItemsFromMarket)
                .bounds(receiveItemsFromMarketButtonRect.x, receiveItemsFromMarketButtonRect.y, receiveItemsFromMarketButtonRect.width, receiveItemsFromMarketButtonRect.height).build());


        buildItemButtons();

    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
       // renderTransparentBackground(pGuiGraphics);
        super.renderBackground(pGuiGraphics);
        pGuiGraphics.blit(TEXTURE, this.leftPos+BankTerminalContainerMenu.POS_X, this.topPos+BankTerminalContainerMenu.POS_Y, 0, 0, this.imageWidth, this.imageHeight);


        pGuiGraphics.fill(receiveWindowBackgroundRect.x, receiveWindowBackgroundRect.y,
                receiveWindowBackgroundRect.x + receiveWindowBackgroundRect.width, receiveWindowBackgroundRect.y + receiveWindowBackgroundRect.height, 0x7F000000);
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // Draw money string
        long money = ClientBankManager.getBalance();
        //pGuiGraphics.fill(balanceLabelRect.x, balanceLabelRect.y, balanceLabelRect.x + balanceLabelRect.width, balanceLabelRect.y + balanceLabelRect.height, 0x7F000000);
        pGuiGraphics.drawString(font, "Balance: $" + money, balanceLabelRect.x, balanceLabelRect.y, 0xFFFFFF);

        drawItemScrolList(pGuiGraphics);
        renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    // Method to handle the tick event
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Minecraft.getInstance().screen instanceof BankTerminalScreen screen) {
                // This will only run when the screen is an instance of BankTerminalScreen
                screen.handleTick();
            }
        }
    }

    public void handleTick() {
        tickCount++;
        if(tickCount - lastTickCount > 40)
        {
            RequestBankDataPacket.sendRequest();
            StockMarketMod.LOGGER.info("Updating bank terminal screen");
            lastTickCount = tickCount;
            buildItemButtons();
        }
    }

    private void buildItemButtons()
    {
        int x = itemListViewRect.x;
        int y = itemListViewRect.y;
        // Sort the bank accounts by itemID
        ArrayList<Pair<String,SyncBankDataPacket.BankData>> sortedBankAccounts = ClientBankManager.getSortedItemData();

        boolean needsResize = sortedBankAccounts.size() != bankElements.size();
        HashMap<String,String> availableItems = new HashMap<>();
        //bankElements.clear();
        for (int i=0; i<sortedBankAccounts.size(); i++) {
            Pair<String,SyncBankDataPacket.BankData> pair = sortedBankAccounts.get(i);
            int amount = (int)pair.getSecond().getBalance();
            BankElement button = getToggleButton(pair.getFirst());
            if(button == null)
            {
                ItemStack stack = StockMarketMod.createItemStackFromId(pair.getFirst(), amount);
                button = new BankElement(this, x, y + i * BankElement.HEIGHT, itemListViewRect.width -8,stack, pair.getFirst());
                bankElements.add(button);
            }
            else
            {
                button.stack.setCount(amount);
            }
            if(needsResize)
                availableItems.put(pair.getFirst(), pair.getFirst());
            //ItemStack stack = StockMarketMod.createItemStackFromId(pair.getFirst(), amount);
            //BankElement button = new BankElement(x, y + i * BankElement.SIZE+2, stack, pair.getFirst());
            //bankElements.add(button);
        }

        if(needsResize)
        {
            // Remove the buttons that are not in the list
            ArrayList<BankElement> toRemove = new ArrayList<>();
            for (BankElement bankElement : bankElements) {
                if(!availableItems.containsKey(bankElement.itemID))
                    toRemove.add(bankElement);
            }
            bankElements.removeAll(toRemove);
        }

        visibleCount = itemListViewRect.height / BankElement.HEIGHT;
    }

    private BankElement getToggleButton(String itemID)
    {
        for (BankElement button : bankElements) {
            if(button.itemID.equals(itemID))
                return button;
        }
        return null;
    }

    private void onTransmittItemsToMarket(Button pButton) {

        for(BankElement button : bankElements)
        {
            //if(button.isToggled())
            //{
                button.saveAmount();
                StockMarketMod.LOGGER.info("Sending item: "+button.itemID + " amount: "+button.getTargetAmount());
                //RequestOrderPacket.generateRequest(button.itemID, button.getTargetAmount());
            //}
        }

        HashMap<String, Integer> itemTransferToMarketAmounts = new HashMap<>();
        UpdateBankTerminalBlockEntityPacket.sendPacketToServer(this.menu.getBlockPos(), itemTransferToMarketAmounts, true);
    }
    private void onReceiveItemsFromMarket(Button pButton) {
        for(BankElement button : bankElements)
        {
           // if(button.isToggled())
           // {
                button.saveAmount();
                StockMarketMod.LOGGER.info("Sending item: "+button.itemID + " amount: "+button.getTargetAmount());
                //RequestOrderPacket.generateRequest(button.itemID, button.getTargetAmount());
           // }
        }
        HashMap<String, Integer> itemTransferToMarketAmounts = new HashMap<>();
        for(BankElement button : bankElements)
        {
            //if(button.isToggled())
            //{
                int amount = button.getTargetAmount();
                if(amount > 0)
                {
                    itemTransferToMarketAmounts.put(button.itemID, amount);
                }
            //}
        }
        UpdateBankTerminalBlockEntityPacket.sendPacketToServer(this.menu.getBlockPos(), itemTransferToMarketAmounts, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        /*if (button == 0 && !mouseClickToggle) { // Left mouse button
            mouseClickToggle = true;
            for (BankElement button1 : bankElements) {
                if(button1.checkClick((int)mouseX, (int)mouseY))
                    return true;
            }
        }*/
        if (button == 0) {
            if(mouseX >= itemListViewRect.x && mouseX <= itemListViewRect.x + itemListViewRect.width && mouseY >= itemListViewRect.y && mouseY <= itemListViewRect.y + itemListViewRect.height)
            {
                int startIndex = Math.max(0, scrollOffset);
                int endIndex = Math.min(bankElements.size(), startIndex + visibleCount);
                for (int i = startIndex; i < endIndex; i++) {
                    BankElement view = bankElements.get(i);
                    EditBox amountBox = view.amountBox;
                    if(amountBox.isMouseOver((int)mouseX, (int)mouseY))
                    {
                        //amountBox.setFocused(true);
                        this.setFocused(amountBox);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(button == 0)
        {
            mouseClickToggle = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }*/

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Handle scrolling
        if (this.isMouseOver(mouseX, mouseY)) {
            if (delta > 0 && scrollOffset > 0) {
                scrollOffset--; // Scroll up
            } else if (delta < 0 && scrollOffset < bankElements.size() - visibleCount) {
                scrollOffset++; // Scroll down
            }
            return true;
        }
        return false;
    }

  /*  @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.priceBox.isFocused()) {
            return this.priceBox.keyPressed(keyCode, scanCode, modifiers)
                    || this.priceBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }*/

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for(BankElement view : bankElements)
        {
            if(view.amountBox.isFocused())
            {
                if(view.amountBox.charTyped(codePoint, modifiers))
                {
                    if(view.getTargetAmount() > view.stack.getCount())
                    {
                        view.setTargetAmount(view.stack.getCount());
                    }
                    return true;
                }
                return false;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }



    private void drawItemScrolList(GuiGraphics graphics)
    {
        // Render visible buttons
        int startIndex = Math.max(0, scrollOffset);
        int endIndex = Math.min(bankElements.size(), startIndex + visibleCount);

        for (int i = startIndex; i < endIndex; i++) {
            BankElement view = bankElements.get(i);
            int buttonY = itemListViewRect.y + (i - scrollOffset) * BankElement.HEIGHT;
            view.setY(buttonY); // Update button Y position
            view.render(graphics);
        }

        // Render scrollbar
        if (bankElements.size() > visibleCount) {
            int scrollbarHeight = (int) ((float) visibleCount / bankElements.size() * itemListViewRect.height);
            int scrollbarY = itemListViewRect.y + (int) ((float) scrollOffset / bankElements.size() * itemListViewRect.height);
            graphics.fill(
                    itemListViewRect.x + itemListViewRect.width - 6, scrollbarY,
                    itemListViewRect.x + itemListViewRect.width, scrollbarY + scrollbarHeight,
                    0xFFAAAAAA // Scrollbar color
            );
        }
    }

}