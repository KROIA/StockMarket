package net.kroia.stockmarket.plugin.base;

/*
public class MarketPluginInterface  {

    public class Market implements IMarketPluginInterface {
        @Override
        public @NotNull TradingPair getTradingPair() {
            return null;
        }

        @Override
        public float getPrice() {
            return 0;
        }

        @Override
        public float getTargetPrice() {
            return 0;
        }

        @Override
        public void addToTargetPrice(float delta) {

        }

        @Override
        public long placeOrder(float amount, float price) {
            return 0;
        }

        @Override
        public long placeOrder(float amount) {
            return 0;
        }


        public class OrderBookInterface implements IMarketPluginInterface.OrderBookInterface {


            @Override
            public @NotNull List<LimitOrder> getOrders() {
                return null;
            }

            @Override
            public @NotNull List<Order> getNewOrders() {
                return null;
            }

            @Override
            public float getVolume(float minPrice, float maxPrice) {
                return 0;
            }

            @Override
            public @NotNull Tuple<@NotNull Float, @NotNull Float> getEditablePriceRange() {
                return null;
            }

            @Override
            public void setVolume(float minPrice, float maxPrice, float volume) {

            }

            @Override
            public void addVolume(float minPrice, float maxPrice, float volume) {

            }

            @Override
            public void registerDefaultVolumeDistributionFunction(Function<Float, Float> volumeDistributionFunction) {

            }

            @Override
            public void unregisterDefaultVolumeDistributionFunction() {

            }
        }

        public final OrderBookInterface orderBookInterface = new OrderBookInterface();
    }

    private final ServerPluginManager pluginManager;
    public final Market market = new Market();

    public MarketPluginInterface(ServerPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
}
*/