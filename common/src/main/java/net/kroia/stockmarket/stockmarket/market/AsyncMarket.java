package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.async_function_forwarding.AsyncForwardingRequest;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionDataCodecs;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionInputData;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionOutputData;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.market.ISyncServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AsyncMarket implements IAsyncMarket{
    private static StockMarketModBackend.ServerInstances SERVER_BACKEND_INSTANCES;
    private static StockMarketModBackend.ClientInstances CLIENT_BACKEND_INSTANCES;
    public static void setServerBackend(StockMarketModBackend.ServerInstances backend) {
        SERVER_BACKEND_INSTANCES = backend;
    }
    public static void setClientBackend(StockMarketModBackend.ClientInstances backend) {
        CLIENT_BACKEND_INSTANCES = backend;
    }

    private final boolean isClientSide;
    private final ItemID itemID;

    private AsyncMarket(ItemID itemID, boolean isClientSide)
    {
        this.isClientSide = isClientSide;
        this.itemID = itemID;
    }
    public static AsyncMarket createClientMarket(ItemID itemID)
    {
        return new AsyncMarket(itemID, true);
    }
    public static AsyncMarket createSlaveServerMarket(ItemID itemID)
    {
        return new AsyncMarket(itemID, false);
    }
    public static AsyncMarket createMarket(ItemID itemID, boolean isClientSide)
    {
        return new AsyncMarket(itemID, isClientSide);
    }




    /**
     * Enumeration to specify each function that can be forwarded
     */
    public enum FunctionType
    {
        //GetItemID,
        GetDefaultPrice,
        GetCurrentMarketPrice,
        GetCurrentTime,
        GetRawVolume_1,
        GetRawVolume_2,
        GetRealVolume_1,
        GetRealVolume_2,
        PutOrder_1,
        PutOrder_2,
        GetLimitOrders,
        IsMarketOpen,
        SetMarketOpen,
        GetCurrentMarketPriceStruct,
        GetCurrentMarketPriceStructAndReset,
        GetSettings,
        SetSettings,
        ResetNetPlayerItemFlow,
    }


    /**
     * Map of codec pairs for each function
     * If a codec is set to null, that means the argument is not available.
     *      -> inputParamsCodec  == null: The function does not take any parameters
     *      -> outputParamsCodec == null: The function does not return any value
     */
    private static AsyncFunctionDataCodecs codecPacket(@Nullable StreamCodec<RegistryFriendlyByteBuf, ?> inputParamsCodec, @Nullable StreamCodec<RegistryFriendlyByteBuf, ?> outputParamsCodec)
    {
        return new AsyncFunctionDataCodecs(MarketIdentifyAndDataPacket.streamCodec(inputParamsCodec), outputParamsCodec);
    }
    public static final Map<FunctionType, AsyncFunctionDataCodecs> codecs = new HashMap<>(){{
        //put(FunctionType.GetItemID,                           codecPacket(null, ItemID.STREAM_CODEC));
        put(FunctionType.GetDefaultPrice,                       codecPacket(null, ByteBufCodecs.VAR_LONG.cast()));
        put(FunctionType.GetCurrentMarketPrice,                 codecPacket(null, ByteBufCodecs.VAR_LONG.cast()));
        put(FunctionType.GetCurrentTime,                        codecPacket(null, ByteBufCodecs.VAR_LONG.cast()));
        put(FunctionType.GetRawVolume_1,                        codecPacket(ByteBufCodecs.VAR_LONG.cast(), ByteBufCodecs.VAR_LONG.cast()));
        put(FunctionType.GetRawVolume_2,                        codecPacket(ParamGroup_long_long.STREAM_CODEC.cast(), ByteBufCodecs.VAR_LONG.cast()));
        put(FunctionType.GetRealVolume_1,                       codecPacket(ByteBufCodecs.DOUBLE.cast(), ByteBufCodecs.FLOAT.cast()));
        put(FunctionType.GetRealVolume_2,                       codecPacket(ParamGroup_double_double.STREAM_CODEC.cast(), ByteBufCodecs.FLOAT.cast()));
        put(FunctionType.PutOrder_1,                            codecPacket(Order.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.PutOrder_2,                            codecPacket(InterMarketOrder.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.GetLimitOrders,                        codecPacket(null, ExtraCodecUtils.listStreamCodec(Order.STREAM_CODEC)));
        put(FunctionType.IsMarketOpen,                          codecPacket(null, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.SetMarketOpen,                         codecPacket(ByteBufCodecs.BOOL.cast(), ByteBufCodecs.BOOL.cast()));
        put(FunctionType.GetCurrentMarketPriceStruct,           codecPacket(null, MarketPriceStruct.STREAM_CODEC));
        put(FunctionType.GetCurrentMarketPriceStructAndReset,   codecPacket(null, MarketPriceStruct.STREAM_CODEC));
        put(FunctionType.GetSettings,                           codecPacket(null, MarketSettings.STREAM_CODEC));
        put(FunctionType.SetSettings,                           codecPacket(MarketSettings.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.ResetNetPlayerItemFlow,               codecPacket(null, ByteBufCodecs.BOOL.cast()));

    }};
    /**
     * Specialized InputData class, acting as data container for function input arguments
     */
    public static class InputData extends AsyncFunctionInputData<FunctionType> {
        public InputData(FunctionType function, byte[] encodedParams) {
            super(function, codecs.get(function).inputParamsCodec, encodedParams);
        }
        public InputData(FunctionType function) {
            super(function, codecs.get(function).inputParamsCodec);
        }
        public static <T> InputData of(FunctionType functionType, ItemID itemID, T result)
        {
            MarketIdentifyAndDataPacket<T> packet = MarketIdentifyAndDataPacket.of(itemID, result);
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, packet, InputData::new);
        }
        public static InputData of(FunctionType functionType, ItemID itemID)
        {
            MarketIdentifyAndDataPacket packet = MarketIdentifyAndDataPacket.of(itemID, null);
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, packet, InputData::new);
        }
    }

    /**
     * Specialized OutputData class, acting as data container for function return values
     */
    public static class OutputData extends AsyncFunctionOutputData<FunctionType> {

        public OutputData(FunctionType function, byte[] encodedResult) {
            super(function, codecs.get(function).outputParamsCodec, encodedResult);
        }
        public OutputData(FunctionType function) {
            super(function, codecs.get(function).outputParamsCodec);
        }
        public static <T> OutputData of(FunctionType functionType, T result)
        {
            return (OutputData) AsyncFunctionOutputData.of(codecs.get(functionType).outputParamsCodec, functionType, result, OutputData::new);
        }
        public static OutputData of(FunctionType functionType)
        {
            return (OutputData) AsyncFunctionOutputData.of(functionType, OutputData::new);
        }
    }

    /**
     * Specialized Request class to transport the data packets to the master
     */
    public static class Request extends AsyncForwardingRequest<FunctionType, InputData, OutputData>
    {
        public static final Request instance = (Request) AsynchronousRequestResponseSystem.register(new Request());
        public Request() {
            super(InputData::new, OutputData::new, FunctionType.class);
        }
        @Override
        public String getRequestTypeID() {
            return Request.class.getName();
        }

        @Override
        public CompletableFuture<OutputData> sendRequestToServer(InputData input)
        {
            if(AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Sending request to server for function: "+input.function.toString());
            return super.sendRequestToServer(input);
        }
        boolean isPlayerAdmin(UUID playerID)
        {
            return AsyncMarket.SERVER_BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(playerID);
        }

        /**
         * Gets called by the Request handler on the master side
         * @param input the input data provided by the function call
         * @param playerSender the player. If null, no player has sent the request (server only request)
         * @return the response data future for to send back to the requestor
         */
        @Override
        public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
            String playerInfo = "";
            String playerName = "";
            if(playerSender != null) {
                playerName = tryGetPlayerName(playerSender);
                playerInfo = " from player: " + playerName;
            }
            if(!isRequestAllowed(input, slaveID, playerSender, playerName))
                return CompletableFuture.completedFuture(OutputData.of(input.function));

            if(AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Received request to handle on master server for function: "+input.function.toString() + playerInfo);
            MarketIdentifyAndDataPacket inputData = input.decodeParams();
            ItemID itemID = inputData.itemID;
            IServerMarketManager serverMarketManager = AsyncMarket.SERVER_BACKEND_INSTANCES.MARKET_MANAGER.getSync();
            if(serverMarketManager == null) {
                if(AsyncMarket.SERVER_BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave())
                {
                    throw new RuntimeException("[AsyncMarket]: This server is configured to be a slave server but the slave seems not to be connected to its master.\n" +
                            "This server instance has no IServerMarketManager!");
                }
                throw new RuntimeException("Server market manager not found");
            }
            ISyncServerMarket market = serverMarketManager.getMarket(itemID);
            if(market == null)
                return CompletableFuture.completedFuture(OutputData.of(input.function));


            return CompletableFuture.completedFuture(switch (input.function) {
                //case FunctionType.GetItemID ->                            OutputData.of(input.function, market.getItemID());
                case FunctionType.GetDefaultPrice ->                      OutputData.of(input.function, market.getDefaultPrice());
                case FunctionType.GetCurrentMarketPrice ->                OutputData.of(input.function, market.getCurrentMarketPrice());
                case FunctionType.GetCurrentTime ->                       OutputData.of(input.function, market.getCurrentTime());
                case FunctionType.GetRawVolume_1 ->                          OutputData.of(input.function, market.getRawVolume((Long)inputData.extra));
                case FunctionType.GetRawVolume_2 ->                          {
                    ParamGroup_long_long params = (ParamGroup_long_long)inputData.extra;
                    yield OutputData.of(input.function, market.getRawVolume(params.longValue1, params.longValue2));
                }
                case FunctionType.GetRealVolume_1 ->                          OutputData.of(input.function, market.getRealVolume((Long)inputData.extra));
                case FunctionType.GetRealVolume_2 ->                          {
                    ParamGroup_double_double params = (ParamGroup_double_double)inputData.extra;
                    yield OutputData.of(input.function, market.getRealVolume(params.value1, params.value2));
                }
                case FunctionType.PutOrder_1 ->                           OutputData.of(input.function, market.putOrder((Order)inputData.extra));
                case FunctionType.PutOrder_2 ->                           OutputData.of(input.function, market.putOrder((InterMarketOrder)inputData.extra));
                case FunctionType.GetLimitOrders ->                       OutputData.of(input.function, market.getLimitOrders());
                case FunctionType.IsMarketOpen ->                         OutputData.of(input.function, market.isMarketOpen());
                case FunctionType.SetMarketOpen ->                        OutputData.of(input.function, market.setMarketOpen((Boolean)inputData.extra));
                case FunctionType.GetCurrentMarketPriceStruct ->          OutputData.of(input.function, market.getCurrentMarketPriceStruct());
                case FunctionType.GetCurrentMarketPriceStructAndReset ->  OutputData.of(input.function, market.getCurrentMarketPriceStructAndReset());
                case FunctionType.GetSettings ->  {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, new MarketSettings());
                    }
                    yield OutputData.of(input.function, market.getSettings());
                }
                case FunctionType.SetSettings ->  {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    market.setSettings((MarketSettings)inputData.extra);
                    yield OutputData.of(input.function, true);
                }
                case FunctionType.ResetNetPlayerItemFlow -> {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    market.resetNetPlayerItemFlow();
                    yield OutputData.of(input.function, true);
                }
            });
        }
        @Override
        protected boolean isAllowedToCallByClient(InputData input)
        {
            return switch (input.function) {
                case //FunctionType.GetItemID,
                     FunctionType.GetCurrentMarketPrice,
                     FunctionType.GetCurrentTime,
                     FunctionType.GetRawVolume_1,
                     FunctionType.GetRawVolume_2,
                     FunctionType.GetRealVolume_1,
                     FunctionType.GetRealVolume_2,
                     //FunctionType.PutOrder_1,
                     //FunctionType.PutOrder_2,
                     //FunctionType.GetLimitOrders,
                     FunctionType.IsMarketOpen,
                     //FunctionType.SetMarketOpen,
                     FunctionType.GetCurrentMarketPriceStruct,
                     //FunctionType.GetCurrentMarketPriceStructAndReset,
                     FunctionType.GetSettings,
                     FunctionType.SetSettings,
                     FunctionType.ResetNetPlayerItemFlow
                     -> true;

                default -> false;
            };
        }
    }

    /**
     * Makes sure that the instance exists from the beginning on and not only on the first usage
     */
    public static void setupNetworkPacket()
    {
        Request instance = Request.instance;
    }

    private CompletableFuture<OutputData> sendRequest(InputData input)
    {
        CompletableFuture<OutputData> future = new CompletableFuture<>();
        CompletableFuture<OutputData> tmpFuture;
        if(isClientSide)
            tmpFuture = Request.instance.sendRequestToServer(input);
        else
            tmpFuture = Request.instance.sendRequestToMaster(input);

        tmpFuture.thenAccept(outputData ->{
            if(AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Response received for request: "+ input.function.toString());
            future.complete(outputData);
        });

        return future;
    }



    // ================================================================================================================
    //
    //
    //       Custom Objects to hold multiple parameters, passed by a function call
    //       These objects are used to bundle the arguments from a function that uses multiple arguments
    //
    //
    // ================================================================================================================

    public record MarketIdentifyAndDataPacket<T>(ItemID itemID, T extra) {

        public static <T> MarketIdentifyAndDataPacket<T> of(ItemID itemID, T extra)
        {
            return new MarketIdentifyAndDataPacket<>(itemID, extra);
        }

        // Encode: write accountNr, itemID, then the extra using the provided codec
        public static <T> void encode(
                RegistryFriendlyByteBuf buf,
                MarketIdentifyAndDataPacket<T> params,
                StreamCodec<RegistryFriendlyByteBuf, T> extraCodec
        ) {
            ItemID.STREAM_CODEC.encode(buf, params.itemID);
            ExtraCodecUtils.nullable(extraCodec).encode(buf, params.extra);
        }

        // Decode: read accountNr, itemID, then the extra using the provided codec
        public static <T> MarketIdentifyAndDataPacket<T> decode(
                RegistryFriendlyByteBuf buf,
                StreamCodec<RegistryFriendlyByteBuf, T> extraCodec
        ) {
            ItemID itemID = ItemID.STREAM_CODEC.decode(buf);
            T extra = ExtraCodecUtils.nullable(extraCodec).decode(buf);
            return new MarketIdentifyAndDataPacket<>(itemID, extra);
        }

        // Factory to build a StreamCodec for a specific extra type
        public static <T> StreamCodec<RegistryFriendlyByteBuf, MarketIdentifyAndDataPacket<T>> streamCodec(
                StreamCodec<RegistryFriendlyByteBuf, T> extraCodec
        ) {
            return StreamCodec.of(
                    (buf, params) -> encode(buf, params, extraCodec),
                    buf -> decode(buf, extraCodec)
            );
        }
    }

    private record ParamGroup_long_long(long longValue1, long longValue2)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_long_long> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.longValue1,
                ByteBufCodecs.VAR_LONG, p -> p.longValue2,
                ParamGroup_long_long::new
        );
    }
    private record ParamGroup_double_double(double value1, double value2)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_double_double> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, p -> p.value1,
                ByteBufCodecs.DOUBLE, p -> p.value2,
                ParamGroup_double_double::new
        );
    }

   /* private record ParamGroup_UUID_int(UUID uuid, int integer)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_UUID_int> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, p -> p.uuid,
                ByteBufCodecs.INT, p -> p.integer,
                ParamGroup_UUID_int::new
        );
    }*/


    // ================================================================================================================
    //
    //
    //       Main Interface implementation below
    //
    //
    // ================================================================================================================

    @Override
    public ItemID getItemIDAsync() {
        return itemID;
    }

    @Override
    public CompletableFuture<Long> getDefaultPriceAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0L);
        CompletableFuture<Long> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetDefaultPrice, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Long> getCurrentMarketPriceAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0L);
        CompletableFuture<Long> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetCurrentMarketPrice, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Long> getCurrentTimeAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0L);
        CompletableFuture<Long> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetCurrentTime, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long price) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0L);
        CompletableFuture<Long> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetRawVolume_1, itemID, price);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long startPrice, long endPrice)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0L);
        CompletableFuture<Long> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetRawVolume_2, itemID, new ParamGroup_long_long(startPrice, endPrice));
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }



    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double price) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0.0f);
        CompletableFuture<Float> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetRealVolume_1, itemID, price);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double startPrice, double endPrice)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(0.0f);
        CompletableFuture<Float> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetRealVolume_2, itemID, new ParamGroup_double_double(startPrice, endPrice));
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }


    @Override
    public CompletableFuture<Boolean> putOrderAsync(Order order) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.PutOrder_1, itemID, order);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> putOrderAsync(InterMarketOrder order) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.PutOrder_2, itemID, order);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<List<Order>> getLimitOrdersAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<Order>> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetLimitOrders, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> isMarketOpenAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.IsMarketOpen, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.SetMarketOpen, itemID, marketOpen);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(new MarketPriceStruct(itemID.getShort(), 0,0,0,0, 0f));
        CompletableFuture<MarketPriceStruct> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetCurrentMarketPriceStruct, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(new MarketPriceStruct(itemID.getShort(), 0,0,0,0, 0f));
        CompletableFuture<MarketPriceStruct> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetCurrentMarketPriceStructAndReset, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }



    @Override
    public CompletableFuture<MarketSettings> getSettingsAsync()
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(new MarketSettings());
        CompletableFuture<MarketSettings> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetSettings, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }


    @Override
    public CompletableFuture<Boolean> setSettingsAsync(MarketSettings settings)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.SetSettings, itemID, settings);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> resetNetPlayerItemFlowAsync()
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.ResetNetPlayerItemFlow, itemID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }






    private static void info(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.info("[AsyncMarket] "+msg);
    }
    private static void error(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.error("[AsyncMarket] "+msg);
    }
    private static void error(String msg, Throwable e)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.error("[AsyncMarket] "+msg,e);
    }
    private static void warn(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.warn("[AsyncMarket] "+msg);
    }
    private static void debug(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.debug("[AsyncMarket] "+msg);
    }
    private static StockMarketLogger getLogger()
    {
        if(SERVER_BACKEND_INSTANCES != null)
            return SERVER_BACKEND_INSTANCES.LOGGER;
        else if (CLIENT_BACKEND_INSTANCES != null)
            return CLIENT_BACKEND_INSTANCES.LOGGER;
        return null;
    }
}
