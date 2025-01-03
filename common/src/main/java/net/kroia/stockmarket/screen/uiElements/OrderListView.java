package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.ArrayList;
import java.util.HashMap;

public class OrderListView extends GuiElement {


    private final Label directionLabel;
    private final Label amountLabel;
    private final Label filledLabel;
    private final Label priceLabel;
    private final VerticalListView activeOrderListView;
    public OrderListView()
    {
        super();
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


        addChild(directionLabel);
        addChild(amountLabel);
        addChild(filledLabel);
        addChild(priceLabel);
        addChild(activeOrderListView);
    }


    @Override
    protected void layoutChanged() {
        //int width = activeOrderListView.getWidth()-OrderView.padding*2-activeOrderListView.getScrollbarThickness();
        int width = getWidth()-OrderView.padding*2-activeOrderListView.getScrollbarThickness();
        int labelHeight = 15;

        int _dirWidthRatio = OrderView.dirWidthRatio * width / OrderView.sumRatio;
        int _amountWidthRatio = OrderView.amountWidthRatio * width / OrderView.sumRatio;
        int _filledWidthRatio = OrderView.filledWidthRatio * width / OrderView.sumRatio;
        int _priceWidthRatio = OrderView.priceWidthRatio * width / OrderView.sumRatio;
        //int _cancelWidthRatio = OrderView.cancelWidthRatio * width / OrderView.summRatio;

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


    public void updateActiveOrders()
    {
        ArrayList<Order> orders = ClientMarket.getOrders(TradeScreen.getItemID());
        HashMap<Long,Integer> stillActiveOrderIds = new HashMap<>();
        ArrayList<GuiElement> elements = activeOrderListView.getChilds();

        for(int j=0; j<orders.size(); ++j)
        {
            long orderID = orders.get(j).getOrderID();
            stillActiveOrderIds.put(orderID, 1);
        }
        boolean viewContentChanged = false;
        for (int i = 0; i < elements.size(); ++i) {
            if (elements.get(i) instanceof OrderView view) {
                Order order = view.getOrder();
                if(!stillActiveOrderIds.containsKey(order.getOrderID()))
                {
                    activeOrderListView.removeChild(view);
                    elements = activeOrderListView.getChilds();
                    viewContentChanged = true;
                    i--;
                }
                stillActiveOrderIds.put(order.getOrderID(), 2);
            }
        }
        for(int j=0; j<orders.size(); ++j)
        {
            long orderID = orders.get(j).getOrderID();
            if(stillActiveOrderIds.get(orderID) == 1)
            {
                OrderView orderView = new OrderView(orders.get(j));
                activeOrderListView.addChild(orderView);
                viewContentChanged = true;
            }
        }
    }


}
