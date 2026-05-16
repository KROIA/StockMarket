package net.kroia.stockmarket.stockmarket.market.preset;

import net.kroia.banksystem.util.async_function_forwarding.AsyncForwardingRequest;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionDataCodecs;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionInputData;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionOutputData;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.preset.IAsyncPresetManager;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AsyncPresetManager implements IAsyncPresetManager {
    private static StockMarketModBackend.ServerInstances SERVER_BACKEND_INSTANCES;
    private static StockMarketModBackend.ClientInstances CLIENT_BACKEND_INSTANCES;

    public static void setServerBackend(StockMarketModBackend.ServerInstances backend) {
        SERVER_BACKEND_INSTANCES = backend;
    }
    public static void setClientBackend(StockMarketModBackend.ClientInstances backend) {
        CLIENT_BACKEND_INSTANCES = backend;
    }

    private final boolean isClientSide;

    private AsyncPresetManager(boolean clientSide) {
        this.isClientSide = clientSide;
    }

    public static AsyncPresetManager createClientManager() {
        return new AsyncPresetManager(true);
    }

    public static AsyncPresetManager createSlaveServerManager() {
        return new AsyncPresetManager(false);
    }

    // Enumeration of forwardable functions
    public enum FunctionType {
        GetCategories,
        UpdatePresets,
    }

    // Codec pairs for each function (null = no params / no return)
    private static AsyncFunctionDataCodecs codecPacket(@Nullable StreamCodec<RegistryFriendlyByteBuf, ?> inputParamsCodec, @Nullable StreamCodec<RegistryFriendlyByteBuf, ?> outputParamsCodec) {
        return new AsyncFunctionDataCodecs(inputParamsCodec, outputParamsCodec);
    }

    public static final Map<FunctionType, AsyncFunctionDataCodecs> codecs = new HashMap<>() {{
        put(FunctionType.GetCategories,  codecPacket(null, ExtraCodecUtils.listStreamCodec(MarketPresetCategory.STREAM_CODEC)));
        put(FunctionType.UpdatePresets,  codecPacket(ExtraCodecUtils.listStreamCodec(MarketPreset.STREAM_CODEC), ByteBufCodecs.BOOL.cast()));
    }};

    // InputData container
    public static class InputData extends AsyncFunctionInputData<FunctionType> {
        public InputData(FunctionType function, byte[] encodedParams) {
            super(function, codecs.get(function).inputParamsCodec, encodedParams);
        }
        public InputData(FunctionType function) {
            super(function, codecs.get(function).inputParamsCodec);
        }
        public static <T> InputData of(FunctionType functionType, T result) {
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, result, InputData::new);
        }
        public static InputData of(FunctionType functionType) {
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, null, InputData::new);
        }
    }

    // OutputData container
    public static class OutputData extends AsyncFunctionOutputData<FunctionType> {
        public OutputData(FunctionType function, byte[] encodedResult) {
            super(function, codecs.get(function).outputParamsCodec, encodedResult);
        }
        public OutputData(FunctionType function) {
            super(function, codecs.get(function).outputParamsCodec);
        }
        public static <T> OutputData of(FunctionType functionType, T result) {
            return (OutputData) AsyncFunctionOutputData.of(codecs.get(functionType).outputParamsCodec, functionType, result, OutputData::new);
        }
        public static OutputData of(FunctionType functionType) {
            return (OutputData) AsyncFunctionOutputData.of(functionType, OutputData::new);
        }
    }

    // Request handler for master server processing
    public static class Request extends AsyncForwardingRequest<FunctionType, InputData, OutputData> {
        public static final Request instance = (Request) AsynchronousRequestResponseSystem.register(new Request());

        public Request() {
            super(InputData::new, OutputData::new, FunctionType.class);
        }

        @Override
        public String getRequestTypeID() {
            return Request.class.getName();
        }

        @Override
        public CompletableFuture<OutputData> sendRequestToServer(InputData input) {
            if (AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Sending request to server for function: " + input.function.toString());
            return super.sendRequestToServer(input);
        }

        @Override
        public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
            String playerInfo = "";
            String playerName = "";
            if (playerSender != null) {
                playerName = tryGetPlayerName(playerSender);
                playerInfo = " from player: " + playerName;
            }
            if (!isRequestAllowed(input, slaveID, playerSender, playerName))
                return CompletableFuture.completedFuture(OutputData.of(input.function));

            if (AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Received request to handle on master server for function: " + input.function.toString() + playerInfo);

            MarketPresetManager presetManager = SERVER_BACKEND_INSTANCES.PRESET_MANAGER;
            if (presetManager == null) {
                error("No preset manager found on server");
                return CompletableFuture.completedFuture(OutputData.of(input.function));
            }

            return CompletableFuture.completedFuture(switch (input.function) {
                case FunctionType.GetCategories -> OutputData.of(input.function, presetManager.getCategories());
                case FunctionType.UpdatePresets -> {
                    // Permission check: only op level 2 players can update presets
                    if (playerSender == null || !hasPermission(playerSender)) {
                        yield OutputData.of(input.function, false);
                    }
                    List<MarketPreset> updates = input.decodeParams();
                    for (MarketPreset updated : updates) {
                        for (MarketPresetCategory cat : presetManager.getCategories()) {
                            List<MarketPreset> presets = cat.getPresets();
                            for (int i = 0; i < presets.size(); i++) {
                                if (presets.get(i).getItemId().equals(updated.getItemId())) {
                                    presets.set(i, updated);
                                    break;
                                }
                            }
                        }
                    }
                    presetManager.saveAll(DataManager.getPresetPath());
                    info("Updated " + updates.size() + " preset(s)");
                    yield OutputData.of(input.function, true);
                }
            });
        }

        private boolean hasPermission(UUID playerUUID) {
            MinecraftServer server = UtilitiesPlatform.getServer();
            if (server == null) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            return player != null && player.hasPermissions(2);
        }

        @Override
        protected boolean isAllowedToCallByClient(InputData input) {
            return switch (input.function) {
                case FunctionType.GetCategories,
                     FunctionType.UpdatePresets -> true;
            };
        }

        @Override
        protected boolean isAllowedToCallByUntrustedSlaveServer(InputData input) {
            return switch (input.function) {
                case FunctionType.GetCategories -> true;
                default -> false;
            };
        }
    }

    // Initialize the request handler registration
    public static void setupNetworkPacket() {
        Request instance = Request.instance;
    }

    private CompletableFuture<OutputData> sendRequest(InputData input) {
        CompletableFuture<OutputData> future = new CompletableFuture<>();
        CompletableFuture<OutputData> tmpFuture;
        if (isClientSide)
            tmpFuture = Request.instance.sendRequestToServer(input);
        else
            tmpFuture = Request.instance.sendRequestToMaster(input);

        tmpFuture.thenAccept(outputData -> {
            try {
                if (AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                    info("Response received for request: " + input.function.toString());
                future.complete(outputData);
            } catch (Exception ex) {
                error("Exception while sending request for function: " + input.function.toString(), ex);
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    // ===== IAsyncPresetManager implementation =====

    @Override
    public CompletableFuture<List<MarketPresetCategory>> getCategoriesAsync() {
        if (!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<MarketPresetCategory>> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.GetCategories);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept(outputData -> future.complete(outputData.decodeResult()));
        return future;
    }

    @Override
    public CompletableFuture<Boolean> updatePresetsAsync(List<MarketPreset> updatedPresets) {
        if (!MultiServerUtils.canInteractWithStockMarket())
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        InputData inputData = InputData.of(FunctionType.UpdatePresets, updatedPresets);
        CompletableFuture<OutputData> outputDataFuture = sendRequest(inputData);
        outputDataFuture.thenAccept(outputData -> future.complete(outputData.decodeResult()));
        return future;
    }

    // ===== Logging =====

    private static void info(String msg) {
        StockMarketLogger logger = getLogger();
        if (logger != null)
            logger.info("[AsyncPresetManager] " + msg);
    }
    private static void error(String msg) {
        StockMarketLogger logger = getLogger();
        if (logger != null)
            logger.error("[AsyncPresetManager] " + msg);
    }
    private static void error(String msg, Throwable e) {
        StockMarketLogger logger = getLogger();
        if (logger != null)
            logger.error("[AsyncPresetManager] " + msg, e);
    }
    private static StockMarketLogger getLogger() {
        if (SERVER_BACKEND_INSTANCES != null)
            return SERVER_BACKEND_INSTANCES.LOGGER;
        else if (CLIENT_BACKEND_INSTANCES != null)
            return CLIENT_BACKEND_INSTANCES.LOGGER;
        return null;
    }
}
