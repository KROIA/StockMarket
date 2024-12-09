package net.kroia.stockmarket.util;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.geometry.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class OrderListWidget {


    private class OrderView
    {

        public static final float columnPos_direction = 0.0f;
        public static final float columnPos_type = 0.2f;
        public static final float columnPos_amount = 0.4f;
        public static final float columnPos_filled = 0.6f;
        public static final float columnPos_price = 0.8f;

        private static final Component CANCEL_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.order_list_widget.cancel_button_text");

        private final OrderListWidget parent;

        private final int buttonWidth = 40;
        public int backgroundColorBuy = 0x7F00FF00;
        public int backgroundColorSell = 0x7FFF0000;

        private final Order order;
        private Button cancelButton;
        private String orderDirectionStr;
        private String orderTypeStr;
        private int backgroundColor;
        private final Rectangle backgoundRect;
        public OrderView(OrderListWidget parent, Order order)
        {
            this.parent = parent;
            this.order = order;
            backgoundRect = new Rectangle(0,0,0,0);
        }

        public void init(int x, int y, int width, int height)
        {
            backgoundRect.x = x;
            backgoundRect.y = y;
            backgoundRect.width = width;
            backgoundRect.height = height;
            cancelButton = Button.builder(CANCEL_BUTTON_TEXT,
                    this::onCancelButtonPressed)
                    .bounds(x+width-buttonWidth-6, y, buttonWidth, height).build();
            if(order.isBuy())
            {
                orderDirectionStr = "Buy";
                backgroundColor = backgroundColorBuy;
            }
            else {
                orderDirectionStr = "Sell";
                backgroundColor = backgroundColorSell;
            }

            if(order instanceof LimitOrder)
            {
                LimitOrder limitOrder = (LimitOrder) order;
                orderTypeStr = "Limit";
            }
            else if(order instanceof MarketOrder)
            {
                orderTypeStr = "Market";
            }else {
                StockMarketMod.LOGGER.error("Invalid order type");
            }
        }

        public static void renderLegend(GuiGraphics graphics, Rectangle backgroundRect)
        {
            drawText(graphics, backgroundRect, columnPos_direction, "BUY/SELL");
            drawText(graphics, backgroundRect, columnPos_type, "Type");
            drawText(graphics, backgroundRect, columnPos_amount, "Amount");
            drawText(graphics, backgroundRect, columnPos_filled, "Filled");
            drawText(graphics, backgroundRect, columnPos_price, "Price");
        }
        public void render(GuiGraphics graphics, int mouseX, int mouseY)
        {
            // Draw background rect
            backgoundRect.render(graphics, backgroundColor);
            // Draw text

            drawText(graphics, columnPos_direction, orderDirectionStr);
            drawText(graphics, columnPos_type, orderTypeStr);;
            drawText(graphics, columnPos_amount, String.valueOf(Math.abs(order.getAmount())));
            if(order instanceof LimitOrder limitOrder)
            {
                drawText(graphics, columnPos_filled, String.valueOf(limitOrder.getFilledAmount()));
                drawText(graphics, columnPos_price, String.valueOf(limitOrder.getPrice()));
            }


            cancelButton.render(graphics, mouseX, mouseY, 0);

        }
        private void drawText(GuiGraphics graphics, float relativeXPos, String text)
        {
            drawText(graphics, backgoundRect, relativeXPos, text);
        }
        private static void drawText(GuiGraphics graphics, Rectangle rect, float relativeXPos, String text)
        {
            int absXPos = rect.x + (int)(rect.width * relativeXPos);
            graphics.drawString(Minecraft.getInstance().font, text, absXPos, rect.y + rect.height/2, 0xFFFFFFFF);
        }
        public void setY(int y)
        {
            cancelButton.setY(y);
            backgoundRect.y = y;
        }
        public int getY()
        {
            return cancelButton.getY();
        }
        public int getHeight()
        {
            return cancelButton.getHeight();
        }
        public boolean handleMouseClick(double mouseX, double mouseY) {
             if (isMouseOver(cancelButton, mouseX, mouseY)) {
                 // Trigger the button's action manually
                 onCancelButtonPressed(cancelButton);
                 return true;
             }
             return false;
        }

        private boolean isMouseOver(Button button, double mouseX, double mouseY) {
            int x = button.getX();
            int y = button.getY();
            int width = button.getWidth();
            int height = button.getHeight();
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private void onCancelButtonPressed(Button button)
        {
            // Send cancel order packet
            StockMarketMod.LOGGER.info("Cancel order: " + order.getOrderID());
            ClientMarket.cancelOrder(order);
        }
    }

    private final TradeScreen parent;

    private final Rectangle backgroundRect = new Rectangle(0,0,0,0);

    private int scrollOffset = 0;
    private int visibleCount;
    private final int buttonHeight = 20;



    private final ArrayList<OrderView> orderViewList;

    public OrderListWidget(TradeScreen parent) {
        this.parent = parent;

        orderViewList = new ArrayList<>();
    }

    public void init(int x, int y, int width, int height)
    {
        backgroundRect.x = x;
        backgroundRect.y = y;
        backgroundRect.width = width;
        backgroundRect.height = height;
        init();
    }
    public void init()
    {
        // remove old buttons
        orderViewList.clear();

        ArrayList<Order> orders = ClientMarket.getOrders(TradeScreen.getItemID());

        for(int i=0; i<orders.size(); i++)
        {
            Order order = orders.get(i);
            OrderView orderView = new OrderView(this, order);
            orderView.init(backgroundRect.x, backgroundRect.y + i * buttonHeight, backgroundRect.width, buttonHeight);
            orderViewList.add(orderView);
        }
        visibleCount = backgroundRect.height / buttonHeight -1;

    }


    public void render(GuiGraphics graphics, int mouseX, int mouseY)
    {
        // Draw legend
        OrderView.renderLegend(graphics, new Rectangle(backgroundRect.x, backgroundRect.y, backgroundRect.width, buttonHeight));

        // Render visible buttons
        int startIndex = Math.max(0, scrollOffset);
        int endIndex = Math.min(orderViewList.size(), startIndex + visibleCount);

        for (int i = startIndex; i < endIndex; i++) {
            OrderView view = orderViewList.get(i);
            int buttonY = backgroundRect.y + (i - scrollOffset) * view.getHeight() + buttonHeight;
            view.setY(buttonY); // Update button Y position
            view.render(graphics, mouseX, mouseY);
        }

        // Render scrollbar
        if (orderViewList.size() > visibleCount) {
            int scrollbarHeight = (int) ((float) visibleCount / orderViewList.size() * (backgroundRect.height-buttonHeight));
            int scrollbarY = backgroundRect.y+buttonHeight + (int) ((float) scrollOffset / orderViewList.size() * (backgroundRect.height-buttonHeight));
            graphics.fill(
                    backgroundRect.x + backgroundRect.width - 6, scrollbarY,
                    backgroundRect.x + backgroundRect.width, scrollbarY + scrollbarHeight,
                    0xFFAAAAAA // Scrollbar color
            );
        }
        /*
        for(OrderView orderView : orderViewList)
        {
            orderView.render(graphics, mouseX, mouseY);
        }*/
    }

    public boolean handleMouseClick(double mouseX, double mouseY) {
        for(OrderView orderView : orderViewList)
        {
            if(orderView.handleMouseClick(mouseX, mouseY))
                return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Handle scrolling
        if (this.isMouseOver(mouseX, mouseY)) {
            if (delta > 0 && scrollOffset > 0) {
                scrollOffset--; // Scroll up
            } else if (delta < 0 && scrollOffset < orderViewList.size() - visibleCount) {
                scrollOffset++; // Scroll down
            }
            return true;
        }
        return false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return backgroundRect.isMouseOver(mouseX, mouseY);
    }
    private void drawText(GuiGraphics graphics, float relativeXPos, String text)
    {
        int absXPos = backgroundRect.x + (int)(backgroundRect.width * relativeXPos);
        graphics.drawString(Minecraft.getInstance().font, text, absXPos, backgroundRect.y + backgroundRect.height/2, 0xFFFFFFFF);
    }
}
