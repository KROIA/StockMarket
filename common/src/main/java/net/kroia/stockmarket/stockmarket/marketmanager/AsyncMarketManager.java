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
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.util.AdminFlagSync;
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
import java.util.function.Function;

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
    /**
     * Sends one function call and returns the answer future, or {@code null} when the master is unreachable
     * and nothing was sent. {@link #sendRequest} in production, a fake in {@link #createForTesting}.
     */
    private final Function<InputData, CompletableFuture<OutputData>> transport;
    private AsyncMarketManager(boolean clientSide) {
        this.isClientSide = clientSide;
        this.transport = this::sendRequest;
    }
    private AsyncMarketManager(Function<InputData, CompletableFuture<OutputData>> transport) {
        this.isClientSide = true;
        this.transport = transport;
    }

    /**
     * Test seam (T-227): a manager whose calls go to {@code transport} instead of the ARRS, so a test can
     * fail the answer future without a network. The transport returns the answer future, or {@code null}
     * for "master unreachable, nothing sent".
     *
     * @param transport replaces the ARRS send
     * @return the manager
     */
    public static AsyncMarketManager createForTesting(Function<InputData, CompletableFuture<OutputData>> transport)
    {
        return new AsyncMarketManager(transport);
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
        /** Slave to master: admin flag change from LuckPerms (AdminFlagSync); trusted slaves only, master setting gates it. */
        SyncAdminFlagFromPermission,
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
        put(FunctionType.SyncAdminFlagFromPermission,               codecPacket(ParamGroup_UUID_Bool.STREAM_CODEC, ByteBufCodecs.BOOL.cast()));

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
                case FunctionType.CreateMarket ->              isAdminOrServer(manager, playerSender)
                        ? OutputData.of(input.function, manager.createMarket(input.decodeParams()) != null)
                        : refused(input);
                case FunctionType.DeleteMarket ->              isAdminOrServer(manager, playerSender)
                        ? OutputData.of(input.function, manager.deleteMarket(input.decodeParams()))
                        : refused(input);
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
                case FunctionType.IsStockmarketAdmin -> {
                    // T-239: a player may ask only about itself. An untrusted slave sets the sender itself, so it
                    // gets an empty answer (NetworkGate logs it). Refused here, not in
                    // isAllowedToCallByUntrustedSlaveServer: a refusal there with a null sender (the slave's own
                    // /stockmarket manage check) would make BankSystem broadcast a notice to every player on it.
                    UUID uuid = input.decodeParams();
                    if(!isSelfOrServer(playerSender, uuid))
                        yield refused(input);
                    yield NetworkGate.isManagementReadAllowed(slaveID, "IsStockmarketAdmin")
                            ? OutputData.of(input.function, manager.isStockmarketAdmin(uuid))
                            : OutputData.of(input.function);
                }
                case FunctionType.GetPlayerPreferences -> {
                    UUID uuid = input.decodeParams();
                    yield isSelfOrServer(playerSender, uuid)
                            ? OutputData.of(input.function, manager.getPlayerPreferences(uuid))
                            : refused(input);
                }
                case FunctionType.UpdatePlayerPreferences -> {
                    ParamGroup_UUID_Preferences data = (ParamGroup_UUID_Preferences)input.decodeParams();
                    yield isSelfOrServer(playerSender, data.uuid)
                            ? OutputData.of(input.function, manager.updatePlayerPreferences(data.uuid, data.preferences))
                            : refused(input);
                }
                case FunctionType.SyncAdminFlagFromPermission -> {
                    // Server-internal (null sender) write from a TRUSTED slave (untrusted ones are already refused by
                    // isRequestAllowed; checked again here), applied only while the master's own setting is on.
                    ParamGroup_UUID_Bool data = (ParamGroup_UUID_Bool)input.decodeParams();
                    boolean accept = AdminFlagSync.acceptSlaveSync(!slaveID.isEmpty(), playerSender != null,
                            !slaveID.isEmpty() && NetworkGate.isMutatingCallAllowed(slaveID, "SyncAdminFlagFromPermission"),
                            AdminFlagSync.isEnabled());
                    yield OutputData.of(input.function, accept
                            && AdminFlagSync.writeLocal(manager, data.uuid, data.bool, "permission sync from slave '" + slaveID + "'"));
                }
            });
        }

        /**
         * T-238: may the sender create or delete a market? {@link #isRequestAllowed} only checks the function list,
         * so the player rule is checked here.
         *
         * @param manager      the master's market manager, holds the admin flags
         * @param playerSender the player who sent the call (directly or through a trusted slave); null for a
         *                     server-internal call (the master itself or a slave), which may
         * @return true for a server-internal call or a player with StockMarket admin mode
         */
        private static boolean isAdminOrServer(IServerMarketManager manager, @Nullable UUID playerSender)
        {
            return playerSender == null || manager.isStockmarketAdmin(playerSender);
        }

        /**
         * T-238: may the sender read or write the preferences of {@code subject}, or (T-239) ask whether
         * {@code subject} has StockMarket admin mode? A player only their own.
         *
         * @param playerSender the player who sent the call; null for a server-internal call, which may access any
         * @param subject      the player whose preferences or admin flag are read or written
         * @return true for a server-internal call or if {@code subject} is the sender
         */
        private static boolean isSelfOrServer(@Nullable UUID playerSender, UUID subject)
        {
            return playerSender == null || playerSender.equals(subject);
        }

        /**
         * T-238: the answer to a player call refused by {@link #isAdminOrServer} or {@link #isSelfOrServer}: an
         * empty result, like the refusals of {@link #isRequestAllowed}. Logs the function, not the player.
         *
         * @param input the refused call
         * @return the empty answer
         */
        private OutputData refused(InputData input)
        {
            warn("Refused " + input.function + " from a client: it needs StockMarket admin mode or the player's own UUID");
            return OutputData.of(input.function);
        }

        /**
         * Functions a player may send. CreateMarket and DeleteMarket also need StockMarket admin mode, the
         * preferences functions the sender's own UUID (T-238), IsStockmarketAdmin as well (T-239); checked in
         * {@link #handleOnMasterServer}.
         */
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
                     FunctionType.IsStockmarketAdmin,
                     FunctionType.GetPlayerPreferences,
                     FunctionType.UpdatePlayerPreferences -> true;

                default -> false;
            };
        }

        /**
         * Functions an untrusted slave may send. IsStockmarketAdmin stays listed so a refusal does not make
         * BankSystem broadcast a notice to the whole slave; {@link #handleOnMasterServer} answers it empty for an
         * untrusted slave instead (T-239).
         */
        @Override
        protected boolean isAllowedToCallByUntrustedSlaveServer(InputData input)
        {
            return switch (input.function) {
                case FunctionType.GetTradingCurrencyID,
                     FunctionType.GetAvailableMarketIDs,
                     FunctionType.MarketExists,
                     FunctionType.IsStockmarketAdmin,
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
        return call(InputData.of(FunctionType.GetTradingCurrencyID), ItemID.INVALID_ID);
    }

    /**
     * {@inheritDoc}
     * <p>
     * T-237: markets whose item this side cannot resolve (an item only the master has) are left out, so a
     * slave's commands and tab completion never offer them.
     */
    @Override
    public CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync() {
        return this.<List<ItemID>>call(InputData.of(FunctionType.GetAvailableMarketIDs), List.of())
                .thenApply(ids -> ids == null ? null
                        : ids.stream().filter(MultiServerUtils::isMarketResolvableOnThisServer).toList());
    }

    @Override
    public CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID) {
        return call(InputData.of(FunctionType.MarketExists, marketID), false);
    }

    @Override
    public CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID) {
        // Unreachable master -> false -> null market, as before. A failed request fails the returned future.
        return this.<Boolean>call(InputData.of(FunctionType.CreateMarket, marketID), false)
                .thenApply(created -> Boolean.TRUE.equals(created) ? createMarket(marketID) : null);
    }

    @Override
    public CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID) {
        return call(InputData.of(FunctionType.DeleteMarket, marketID), false);
    }

    @Override
    public CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID) {
        // Unreachable master -> false -> null market, as before. A failed request fails the returned future.
        return this.<Boolean>call(InputData.of(FunctionType.GetMarket, marketID), false)
                .thenApply(exists -> Boolean.TRUE.equals(exists) ? createMarket(marketID) : null);
    }

    @Override
    public CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin)
    {
        return call(InputData.of(FunctionType.SetStockmarketAdminMode, new ParamGroup_UUID_Bool(playerUUID, isAdmin)), false);
    }
    /**
     * Slave only: forwards a LuckPerms change of the admin node to the master (see {@link AdminFlagSync}).
     * Not part of {@link IAsyncMarketManager}: it is internal to the permission sync.
     *
     * @param playerUUID the user
     * @param isAdmin    the effective node value
     * @return true if the master applied it; false if it refused (setting off, untrusted) or is unreachable
     */
    public CompletableFuture<Boolean> syncAdminFlagFromPermissionAsync(UUID playerUUID, boolean isAdmin)
    {
        return this.<Boolean>call(InputData.of(FunctionType.SyncAdminFlagFromPermission, new ParamGroup_UUID_Bool(playerUUID, isAdmin)), false)
                .thenApply(Boolean.TRUE::equals);
    }
    @Override
    public CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID)
    {
        return call(InputData.of(FunctionType.IsStockmarketAdmin, playerUUID), false);
    }


    @Override
    public void onPlayerJoinAsync(UUID playerUUID, String playerName){
        // Fire and forget: nobody waits on the answer, so a failed request has nothing to report to.
        call(InputData.of(FunctionType.OnPlayerJoin, new ParamGroup_UUID_String(playerUUID, playerName)), null);
    }

    @Override
    public CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID) {
        return call(InputData.of(FunctionType.GetPlayerPreferences, playerUUID), new PlayerPreferences());
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences) {
        return call(InputData.of(FunctionType.UpdatePlayerPreferences, new ParamGroup_UUID_Preferences(playerUUID, preferences)), false);
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
