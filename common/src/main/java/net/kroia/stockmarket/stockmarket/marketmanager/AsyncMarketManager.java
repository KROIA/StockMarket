package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.async_function_forwarding.AsyncForwardingRequest;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionDataCodecs;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionInputData;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionOutputData;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AsyncMarketManager implements IAsyncMarketManager {
    private static StockMarketModBackend.ServerInstances SERVER_BACKEND_INSTANCES;
    private static StockMarketModBackend.ClientInstances CLIENT_BACKEND_INSTANCES;
    public static void setServerBackend(StockMarketModBackend.ServerInstances backend) {
        SERVER_BACKEND_INSTANCES = backend;
        AsyncMarket.setServerBackend(backend);
    }
    public static void setClientBackend(StockMarketModBackend.ClientInstances backend) {
        CLIENT_BACKEND_INSTANCES = backend;
        AsyncMarket.setClientBackend(backend);
    }
    private final boolean isClientSide;
    private AsyncMarketManager(boolean clientSide) {
        this.isClientSide = clientSide;
    }

    public static AsyncMarketManager createClientManager()
    {
        return new AsyncMarketManager(true);
    }
    public static AsyncMarketManager createSlaveServerManager()
    {
        return new AsyncMarketManager(false);
    }
    private AsyncMarket createMarket(ItemID itemID)
    {
        return AsyncMarket.createMarket(itemID, isClientSide);
    }



    /**
     * Enumeration to specify each function that can be forwarded
     */
    public enum FunctionType
    {
        GetTradingCurrencyID,
        GetAvailableMarketIDs,
        MarketExists,
        CreateMarket,
        DeleteMarket,
        GetMarket,
        OnPlayerJoin,
        SetStockmarketAdminMode,
        IsStockmarketAdmin,
        GetPlayerPreferences,
        UpdatePlayerPreferences,
    }


    /**
     * Map of codec pairs for each function
     * If a codec is set to null, that means the argument is not available.
     *      -> inputParamsCodec  == null: The function does not take any parameters
     *      -> outputParamsCodec == null: The function does not return any value
     */
    private static AsyncFunctionDataCodecs codecPacket(@Nullable StreamCodec<RegistryFriendlyByteBuf, ?> inputParamsCodec, @Nullable StreamCodec<RegistryFriendlyByteBuf, ?> outputParamsCodec)
    {
        return new AsyncFunctionDataCodecs(inputParamsCodec, outputParamsCodec);
    }
    public static final Map<FunctionType, AsyncFunctionDataCodecs> codecs = new HashMap<>(){{
        //put(FunctionType.GetItemID,                             codecPacket(null, ItemID.STREAM_CODEC));
        put(FunctionType.GetTradingCurrencyID,                      codecPacket(null, ItemID.STREAM_CODEC));
        put(FunctionType.GetAvailableMarketIDs,                     codecPacket(null, ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC)));
        put(FunctionType.MarketExists,                              codecPacket(ItemID.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.CreateMarket,                              codecPacket(ItemID.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.DeleteMarket,                              codecPacket(ItemID.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.GetMarket,                                 codecPacket(ItemID.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.OnPlayerJoin,                              codecPacket(ParamGroup_UUID_String.STREAM_CODEC, null));
        put(FunctionType.SetStockmarketAdminMode,                   codecPacket(ParamGroup_UUID_Bool.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));
        put(FunctionType.IsStockmarketAdmin,                        codecPacket(UUIDUtil.STREAM_CODEC.cast(), ByteBufCodecs.BOOL.cast()));
        put(FunctionType.GetPlayerPreferences,                      codecPacket(UUIDUtil.STREAM_CODEC.cast(), PlayerPreferences.STREAM_CODEC));
        put(FunctionType.UpdatePlayerPreferences,                   codecPacket(ParamGroup_UUID_Preferences.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));

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
        public static <T> InputData of(FunctionType functionType, T result)
        {
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, result, InputData::new);
        }
        public static InputData of(FunctionType functionType)
        {
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, null, InputData::new);
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
            IServerMarketManager manager = AsyncMarketManager.SERVER_BACKEND_INSTANCES.MARKET_MANAGER.getSync();
            if(manager == null) {
                if(AsyncMarketManager.SERVER_BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave())
                {
                    throw new RuntimeException("[]: This server is configured to be a slave server but the slave seems not to be connected to its master.\n" +
                            "This server instance has no IServerMarketManager!");
                }
                throw new RuntimeException("Server market manager not found");
            }


            return CompletableFuture.completedFuture(switch (input.function) {
                //case FunctionType.GetItemID ->                            OutputData.of(input.function, market.getItemID());
                case FunctionType.GetTradingCurrencyID ->      OutputData.of(input.function, manager.getTradingCurrencyID());
                case FunctionType.GetAvailableMarketIDs ->     OutputData.of(input.function, manager.getAvailableMarketIDs());
                case FunctionType.MarketExists ->              OutputData.of(input.function, manager.marketExists(input.decodeParams()));
                case FunctionType.CreateMarket ->              OutputData.of(input.function, manager.createMarket(input.decodeParams()) != null);
                case FunctionType.DeleteMarket ->              OutputData.of(input.function, manager.deleteMarket(input.decodeParams()));
                case FunctionType.GetMarket ->                 OutputData.of(input.function, manager.getMarket(input.decodeParams()) != null);
                case FunctionType.OnPlayerJoin ->              {
                    ParamGroup_UUID_String data = (ParamGroup_UUID_String)input.decodeParams();
                    manager.onPlayerJoin(data.uuid, data.text);
                    yield OutputData.of(input.function);
                }
                case FunctionType.SetStockmarketAdminMode ->              {
                    ParamGroup_UUID_Bool data = (ParamGroup_UUID_Bool)input.decodeParams();
                    manager.setStockmarketAdminMode(data.uuid, data.bool);
                    yield OutputData.of(input.function);
                }
                case FunctionType.IsStockmarketAdmin ->        OutputData.of(input.function, manager.isStockmarketAdmin(input.decodeParams()));
                case FunctionType.GetPlayerPreferences ->     OutputData.of(input.function, manager.getPlayerPreferences(input.decodeParams()));
                case FunctionType.UpdatePlayerPreferences -> {
                    ParamGroup_UUID_Preferences data = (ParamGroup_UUID_Preferences)input.decodeParams();
                    yield OutputData.of(input.function, manager.updatePlayerPreferences(data.uuid, data.preferences));
                }
            });
        }
        @Override
        protected boolean isAllowedToCallByClient(InputData input)
        {
            return switch (input.function) {
                case FunctionType.GetTradingCurrencyID,
                     FunctionType.GetAvailableMarketIDs,
                     FunctionType.MarketExists,
                     FunctionType.CreateMarket,
                     FunctionType.DeleteMarket,
                     FunctionType.GetMarket,
                     FunctionType.GetPlayerPreferences,
                     FunctionType.UpdatePlayerPreferences -> true;

                default -> false;
            };
        }

        @Override
        protected boolean isAllowedToCallByUntrustedSlaveServer(InputData input)
        {
            return switch (input.function) {
                case FunctionType.GetTradingCurrencyID,
                     FunctionType.GetAvailableMarketIDs,
                     FunctionType.MarketExists,
                     FunctionType.GetMarket-> true;

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
            try{
                if(AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                    info("Response received for request: "+ input.function.toString());
                future.complete(outputData);
            }
            catch(Exception ex)
            {
                error("Exception while sending request to server for function: "+input.function.toString(), ex);
                future.completeExceptionally(ex);
            }
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


   /* private record ParamGroup_UUID_int(UUID uuid, int integer)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_UUID_int> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, p -> p.uuid,
                ByteBufCodecs.INT, p -> p.integer,
                ParamGroup_UUID_int::new
        );
    }*/
   private record ParamGroup_UUID_String(UUID uuid, String text)
   {
       public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_UUID_String> STREAM_CODEC = StreamCodec.composite(
               UUIDUtil.STREAM_CODEC, p -> p.uuid,
               ByteBufCodecs.STRING_UTF8, p -> p.text,
               ParamGroup_UUID_String::new
       );
   }
    private record ParamGroup_UUID_Bool(UUID uuid, boolean bool)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_UUID_Bool> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, p -> p.uuid,
                ByteBufCodecs.BOOL, p -> p.bool,
                ParamGroup_UUID_Bool::new
        );
    }
    private record ParamGroup_UUID_Preferences(UUID uuid, PlayerPreferences preferences)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ParamGroup_UUID_Preferences> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, p -> p.uuid,
                PlayerPreferences.STREAM_CODEC, p -> p.preferences,
                ParamGroup_UUID_Preferences::new
        );
    }


    // ================================================================================================================
    //
    //
    //       Main Interface implementation below
    //
    //
    // ================================================================================================================

    @Override
    public CompletableFuture<ItemID> getTradingCurrencyIDAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(ItemID.INVALID_ID);
        CompletableFuture<ItemID> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetTradingCurrencyID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync() {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<ItemID>> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetAvailableMarketIDs);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.MarketExists, marketID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(null);
        CompletableFuture<@Nullable IAsyncMarket> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.CreateMarket, marketID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> {
            AsyncMarket market = null;
            if(outputData.decodeResult())
                market = createMarket(marketID);
            future.complete(market);
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.DeleteMarket, marketID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(null);
        CompletableFuture<@Nullable IAsyncMarket> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetMarket, marketID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> {
            AsyncMarket market = null;
            if(outputData.decodeResult())
                market = createMarket(marketID);
            future.complete(market);
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.SetStockmarketAdminMode, new ParamGroup_UUID_Bool(playerUUID, isAdmin));
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }
    @Override
    public CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID)
    {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.IsStockmarketAdmin, playerUUID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }


    @Override
    public void onPlayerJoinAsync(UUID playerUUID, String playerName){
        if(!MultiServerUtils.canInteractWithStockMarket())
            return;
        InputData inputData = InputData.of(FunctionType.OnPlayerJoin, new ParamGroup_UUID_String(playerUUID, playerName));
        sendRequest(inputData);
    }

    @Override
    public CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(new PlayerPreferences());
        CompletableFuture<PlayerPreferences> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetPlayerPreferences, playerUUID);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences) {
        if(!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.UpdatePlayerPreferences, new ParamGroup_UUID_Preferences(playerUUID, preferences));
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept((outputData)-> future.complete(outputData.decodeResult()));
        return future;
    }






    private static void info(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.info("[AsyncMarketManager] "+msg);
    }
    private static void error(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.error("[AsyncMarketManager] "+msg);
    }
    private static void error(String msg, Throwable e)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.error("[AsyncMarketManager] "+msg,e);
    }
    private static void warn(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.warn("[AsyncMarketManager] "+msg);
    }
    private static void debug(String msg)
    {
        StockMarketLogger logger = getLogger();
        if(logger != null)
            logger.debug("[AsyncMarketManager] "+msg);
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
