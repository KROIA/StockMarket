package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.OrderReadListData;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class OrderListView extends StockMarketGuiElement {

    private final Label directionLabel;
    private final Label amountLabel;
    private final Label filledLabel;
    private final Label priceLabel;
    private final VerticalListView activeOrderListView;
    private final Consumer<OrderReadData> onCancelOrder;
    public OrderListView(Consumer<OrderReadData> onCancelOrder)
    {
        super();
        this.onCancelOrder = onCancelOrder;
        activeOrderListView = new VerticalListView(0,0,0,0);
        directionLabel = new Label(TradeScreen.DIRECTION_LABEL.getString());
        amountLabel = new Label(TradeScreen.AMOUNT_LABEL.getString());
        filledLabel = new Label(TradeScreen.FILLED_LABEL.getString());
        priceLabel = new Label(TradeScreen.PRICE_LABEL.getString());

        directionLabel.setAlignment(OrderView.alignment);
        amountLabel.setAlignment(OrderView.alignment);
        filledLabel.setAlignment(OrderView.alignment);
        priceLabel.setAlignment(OrderView.alignment);

        activeOrderListView.setLayout(new LayoutVertical(0, 0, true, false));


        filledLabel.setHoverTooltipMousePositionAlignment(Label.Alignment.BOTTOM);
        filledLabel.setHoverTooltipSupplier(StockMarketTextMessages::getOrderListViewFilled);
        filledLabel.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);


        addChild(directionLabel);
        addChild(amountLabel);
        addChild(filledLabel);
        addChild(priceLabel);
        addChild(activeOrderListView);
    }


    @Override
    protected void layoutChanged() {
        int width = getWidth()-OrderView.padding*2-activeOrderListView.getScrollbarThickness();
        int labelHeight = 15;

        int _dirWidthRatio = OrderView.dirWidthRatio * width / OrderView.sumRatio;
        int _amountWidthRatio = OrderView.amountWidthRatio * width / OrderView.sumRatio;
        int _filledWidthRatio = OrderView.filledWidthRatio * width / OrderView.sumRatio;
        int _priceWidthRatio = OrderView.priceWidthRatio * width / OrderView.sumRatio;

        int x = OrderView.padding;
        int y = OrderView.padding;
        directionLabel.setBounds(x,y,_dirWidthRatio,labelHeight);
        amountLabel.setBounds(directionLabel.getRight(),y,_amountWidthRatio,labelHeight);
        filledLabel.setBounds(amountLabel.getRight(),y,_filledWidthRatio,labelHeight);
        priceLabel.setBounds(filledLabel.getRight(),y,_priceWidthRatio,labelHeight);
        activeOrderListView.setBounds(0,y+labelHeight,getWidth(),getHeight()-priceLabel.getBottom());

    }
    @Override
    protected void render() {

    }


    public void updateActiveOrders(OrderReadListData orderList, int itemFractionScaleFactor)
    {
        List<OrderReadData> orders = orderList.orders;
        //ArrayList<Order> orders = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getOrders(TradeScreen.getItemID());
        HashMap<Long,Integer> stillActiveOrderIds = new HashMap<>();
        List<GuiElement> elements = activeOrderListView.getChilds();

        for (OrderReadData item : orders) {
            stillActiveOrderIds.put(item.orderID, 1);
        }
        for (int i = 0; i < elements.size(); ++i) {
            if (elements.get(i) instanceof OrderView view) {
                OrderReadData order = view.getOrder();
                if(!stillActiveOrderIds.containsKey(order.orderID))
                {
                    activeOrderListView.removeChild(view);
                    elements = activeOrderListView.getChilds();
                    i--;
                }
                else {
                    for (OrderReadData value : orders) {
                        if (value.orderID == order.orderID) {
                            view.setOrder(value, itemFractionScaleFactor);
                            break;
                        }
                    }
                }
                stillActiveOrderIds.put(order.orderID, 2);
            }
        }
        for (OrderReadData order : orders) {
            if (stillActiveOrderIds.get(order.orderID) == 1) {
                OrderView orderView = new OrderView(order, onCancelOrder, itemFractionScaleFactor);
                activeOrderListView.addChild(orderView);
            }
        }
    }


}
