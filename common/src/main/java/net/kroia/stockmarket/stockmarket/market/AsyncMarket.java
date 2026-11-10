package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
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
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
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
import java.util.function.Function;

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
    /**
     * Sends one function call and returns the answer future, or {@code null} when the master is unreachable
     * and nothing was sent. {@link #sendRequest} in production, a fake in {@link #createForTesting}.
     */
    private final Function<InputData, CompletableFuture<OutputData>> transport;

    private AsyncMarket(ItemID itemID, boolean isClientSide)
    {
        this.isClientSide = isClientSide;
        this.itemID = itemID;
        this.transport = this::sendRequest;
    }
    private AsyncMarket(ItemID itemID, Function<InputData, CompletableFuture<OutputData>> transport)
    {
        this.isClientSide = true;
        this.itemID = itemID;
        this.transport = transport;
    }
    /**
     * Test seam (T-227): a market whose calls go to {@code transport} instead of the ARRS, so a test can
     * fail the answer future without a network. The transport returns the answer future, or {@code null}
     * for "master unreachable, nothing sent".
     *
     * @param itemID    the market
     * @param transport replaces the ARRS send
     * @return the market
     */
    public static AsyncMarket createForTesting(ItemID itemID, Function<InputData, CompletableFuture<OutputData>> transport)
    {
        return new AsyncMarket(itemID, transport);
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
        GetLimitOrders,
        IsMaintenance,
        SetMaintenance,
        GetCurrentMarketPriceStruct,
        GetCurrentMarketPriceStructAndReset,
        GetSettings,
        SetSettings,
        ResetNetPlayerItemFlow,
        ResetVirtualOrderbook,
        ClearVirtualOrderbook,
        // Appended at the end: ordinals are part of the wire format.
        SetTradingHours,
        SetTradingOverride,
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
        put(FunctionType.GetLimitOrders,                        codecPacket(null, ExtraCodecUtils.listStreamCodec(Order.STREAM_CODEC)));
        put(FunctionType.IsMaintenance,                          codecPacket(null, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.SetMaintenance,                         codecPacket(ByteBufCodecs.BOOL.cast(), ByteBufCodecs.BOOL.cast()));
        put(FunctionType.GetCurrentMarketPriceStruct,           codecPacket(null, MarketPriceStruct.STREAM_CODEC));
        put(FunctionType.GetCurrentMarketPriceStructAndReset,   codecPacket(null, MarketPriceStruct.STREAM_CODEC));
        put(FunctionType.GetSettings,                           codecPacket(null, MarketSettings.STREAM_CODEC));
        put(FunctionType.SetSettings,                           codecPacket(MarketSettings.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.ResetNetPlayerItemFlow,               codecPacket(null, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.ResetVirtualOrderbook,                codecPacket(null, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.ClearVirtualOrderbook,                codecPacket(null, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.SetTradingHours,                      codecPacket(ParamGroup_long_long.STREAM_CODEC.cast(), ByteBufCodecs.BOOL.cast()));
        put(FunctionType.SetTradingOverride,                   codecPacket(ByteBufCodecs.BOOL.cast(), ByteBufCodecs.BOOL.cast()));

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
                case FunctionType.PutOrder_1 ->                           OutputData.of(input.function, putCheckedOrder(itemID, (Order)inputData.extra, slaveID));
                case FunctionType.GetLimitOrders ->                       OutputData.of(input.function, market.getLimitOrders());
                case FunctionType.IsMaintenance ->                         OutputData.of(input.function, market.isUnderMaintenance());
                case FunctionType.SetMaintenance ->
                {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    yield OutputData.of(input.function, market.setMaintenance((Boolean)inputData.extra));
                }
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
                case FunctionType.ResetVirtualOrderbook -> {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    market.resetVirtualOrderbook();
                    yield OutputData.of(input.function, true);
                }
                case FunctionType.ClearVirtualOrderbook -> {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    market.clearVirtualOrderbook();
                    yield OutputData.of(input.function, true);
                }
                case FunctionType.SetTradingHours -> {
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    ParamGroup_long_long params = (ParamGroup_long_long)inputData.extra;
                    yield OutputData.of(input.function, market.setTradingHours((int)params.longValue1, (int)params.longValue2));
                }
                case FunctionType.SetTradingOverride -> {
                    // Not client-callable (see isAllowedToCallByClient); the admin check is a
                    // second line of defence in case a player UUID is attached anyway.
                    if(playerSender != null)
                    {
                        if(!isPlayerAdmin(playerSender))
                            yield OutputData.of(input.function, false);
                    }
                    yield OutputData.of(input.function, market.setTradingOverride((Boolean)inputData.extra));
                }
            });
        }
        /**
         * Issue #95: places an order sent by a slave's {@link IAsyncMarket#putOrderAsync} through the same
         * validation and fund lock as a player's {@code CreateOrderRequest} (membership, permissions, balance,
         * market state, lock + unlock on refusal). Only the order's market, player, account, type, volume and
         * price are used; its fill state, time and ID are ignored.
         *
         * @param itemID the market the call was sent to
         * @param order  the order the slave wants placed
         * @param slaveID the slave the call came from, recorded as the order's origin (T-215)
         * @return true if the order was placed; false for a bot order (no player), an order for another
         *         market, or any refusal of the shared order check
         */
        private static boolean putCheckedOrder(ItemID itemID, Order order, String slaveID)
        {
            UUID player = order.getExecutorPlayerUUID();
            if(player == null || !itemID.equals(order.getItemID()))
                return false; // bot orders are master-internal; a call never places into another market
            ISyncServerBankManager bankManager = AsyncMarket.SERVER_BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
            CreateOrderRequest.InputData request = new CreateOrderRequest.InputData(itemID, order.getBankAccountNr(), order.getType(),
                    bankManager.convertToRealAmount(order.getTargetVolume()), bankManager.convertToRealAmount(order.getStartPrice()));
            return AsyncMarket.SERVER_BACKEND_INSTANCES.NETWORKING.CREATE_ORDER_REQUEST.placeOrder(request, player, slaveID)
                    .join().status == CreateOrderRequest.Status.CREATED; // completes at once
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
                     //FunctionType.GetLimitOrders,
                     FunctionType.IsMaintenance,
                     FunctionType.SetMaintenance,
                     FunctionType.GetCurrentMarketPriceStruct,
                     //FunctionType.GetCurrentMarketPriceStructAndReset,
                     FunctionType.GetSettings,
                     FunctionType.SetSettings,
                     FunctionType.ResetNetPlayerItemFlow,
                     FunctionType.ResetVirtualOrderbook,
                     FunctionType.ClearVirtualOrderbook,
                     FunctionType.SetTradingHours
                     // SetTradingOverride is server/command-only -> not client-callable
                     -> true;

                default -> false;
            };
        }

        /**
         * T-123 (untrusted slave gate): tighten the base class default (which
         * would allow every function this class permits from clients) so that
         * mutating function types are refused when forwarded from an untrusted
         * slave. Read-only queries stay wide open — the news feed, price
         * requests and market listings must keep working on an untrusted
         * slave; the untrusted flag only blocks writes.
         *
         * @param input the forwarded function call
         * @return true only for read-only functions
         */
        @Override
        protected boolean isAllowedToCallByUntrustedSlaveServer(InputData input)
        {
            return switch (input.function) {
                case FunctionType.GetCurrentMarketPrice,
                     FunctionType.GetCurrentTime,
                     FunctionType.GetRawVolume_1,
                     FunctionType.GetRawVolume_2,
                     FunctionType.GetRealVolume_1,
                     FunctionType.GetRealVolume_2,
                     FunctionType.IsMaintenance,
                     FunctionType.GetCurrentMarketPriceStruct,
                     FunctionType.GetSettings -> true;
                // SetMaintenance, SetSettings, ResetNetPlayerItemFlow, PutOrder,
                // SetTradingHours, SetTradingOverride,
                // GetCurrentMarketPriceStructAndReset are mutating — refused.
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

    /**
     * The production {@link #transport}: sends {@code input} over the ARRS, to the server on the client and
     * to the master on a slave.
     *
     * @param input the function call to forward
     * @return the ARRS future, unchanged so its failures reach the caller; {@code null} if this slave is not
     *         connected to the master (nothing is sent)
     */
    private @Nullable CompletableFuture<OutputData> sendRequest(InputData input)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return null;
        CompletableFuture<OutputData> future = isClientSide
                ? Request.instance.sendRequestToServer(input)
                : Request.instance.sendRequestToMaster(input);
        if(AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
            future.thenAccept(outputData -> info("Response received for request: "+ input.function.toString()));
        return future;
    }

    /**
     * Calls {@code input.function} on the master and decodes its return value.
     * <p>
     * The returned future fails when the ARRS request fails: {@code RequestOutcomeUnknownException} (no answer
     * in time or the connection was lost; the master may have run the function) or
     * {@code RequestNotSentException} (never sent), or when the answer can't be decoded. Callers must not undo
     * or repeat a mutating call because of such a failure.
     *
     * @param input             the function and its encoded arguments
     * @param unreachableResult returned at once, without sending, when this slave is not connected to the master
     * @param <T>               the function's return type
     * @return the function's decoded return value
     */
    private <T> CompletableFuture<T> call(InputData input, T unreachableResult)
    {
        CompletableFuture<OutputData> response = transport.apply(input);
        if(response == null)
            return CompletableFuture.completedFuture(unreachableResult);
        CompletableFuture<T> result = new CompletableFuture<>();
        AsyncFunctionOutputData.relay(response, result); // completes or fails result like response
        return result;
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
        return call(InputData.of(FunctionType.GetDefaultPrice, itemID), 0L);
    }

    @Override
    public CompletableFuture<Long> getCurrentMarketPriceAsync() {
        return call(InputData.of(FunctionType.GetCurrentMarketPrice, itemID), 0L);
    }

    @Override
    public CompletableFuture<Long> getCurrentTimeAsync() {
        return call(InputData.of(FunctionType.GetCurrentTime, itemID), 0L);
    }

    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long price) {
        return call(InputData.of(FunctionType.GetRawVolume_1, itemID, price), 0L);
    }

    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long startPrice, long endPrice)
    {
        return call(InputData.of(FunctionType.GetRawVolume_2, itemID, new ParamGroup_long_long(startPrice, endPrice)), 0L);
    }



    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double price) {
        return call(InputData.of(FunctionType.GetRealVolume_1, itemID, price), 0.0f);
    }

    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double startPrice, double endPrice)
    {
        return call(InputData.of(FunctionType.GetRealVolume_2, itemID, new ParamGroup_double_double(startPrice, endPrice)), 0.0f);
    }


    @Override
    public CompletableFuture<Boolean> putOrderAsync(Order order) {
        return call(InputData.of(FunctionType.PutOrder_1, itemID, order), false);
    }

    @Override
    public CompletableFuture<List<Order>> getLimitOrdersAsync() {
        return call(InputData.of(FunctionType.GetLimitOrders, itemID), List.of());
    }

    @Override
    public CompletableFuture<Boolean> isUnderMaintenanceAsync() {
        // Not reachable -> report under maintenance (mirrors the former "closed" fallback).
        return call(InputData.of(FunctionType.IsMaintenance, itemID), true);
    }

    @Override
    public CompletableFuture<Boolean> setMaintenanceAsync(boolean maintenance) {
        return call(InputData.of(FunctionType.SetMaintenance, itemID, maintenance), false);
    }

    @Override
    public CompletableFuture<Boolean> setTradingHoursAsync(int openMinute, int closeMinute) {
        return call(InputData.of(FunctionType.SetTradingHours, itemID, new ParamGroup_long_long(openMinute, closeMinute)), false);
    }

    @Override
    public CompletableFuture<Boolean> setTradingOverrideAsync(boolean open) {
        return call(InputData.of(FunctionType.SetTradingOverride, itemID, open), false);
    }

    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync() {
        return call(InputData.of(FunctionType.GetCurrentMarketPriceStruct, itemID), new MarketPriceStruct(itemID.getShort(), 0,0,0,0, 0f));
    }

    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync() {
        return call(InputData.of(FunctionType.GetCurrentMarketPriceStructAndReset, itemID), new MarketPriceStruct(itemID.getShort(), 0,0,0,0, 0f));
    }



    @Override
    public CompletableFuture<MarketSettings> getSettingsAsync()
    {
        return call(InputData.of(FunctionType.GetSettings, itemID), new MarketSettings());
    }


    @Override
    public CompletableFuture<Boolean> setSettingsAsync(MarketSettings settings)
    {
        return call(InputData.of(FunctionType.SetSettings, itemID, settings), false);
    }

    @Override
    public CompletableFuture<Boolean> resetNetPlayerItemFlowAsync()
    {
        return call(InputData.of(FunctionType.ResetNetPlayerItemFlow, itemID), false);
    }

    /**
     * Client / slave entry point for clearing the virtual orderbook of this market.
     * Mirrors {@link #resetNetPlayerItemFlowAsync()}: the request is dispatched
     * through the same admin-gated {@link Request}/{@link FunctionType} channel and
     * resolves once the master has applied the change (or denied the call).
     */
    @Override
    public CompletableFuture<Boolean> resetVirtualOrderbookAsync()
    {
        return call(InputData.of(FunctionType.ResetVirtualOrderbook, itemID), false);
    }

    /**
     * Client / slave entry point for the sticky-clear operation. Mirrors
     * {@link #resetVirtualOrderbookAsync()} end-to-end but routes through the
     * distinct {@link FunctionType#ClearVirtualOrderbook} channel.
     */
    @Override
    public CompletableFuture<Boolean> clearVirtualOrderbookAsync()
    {
        return call(InputData.of(FunctionType.ClearVirtualOrderbook, itemID), false);
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
