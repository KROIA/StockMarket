package net.kroia.stockmarket.minecraft.command;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.banksystem.util.async_function_forwarding.AsyncForwardingRequest;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionDataCodecs;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionInputData;
import net.kroia.banksystem.util.async_function_forwarding.AsyncFunctionOutputData;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Slave-side command handler implementation.
 * Implements IAsyncStockMarketCommandHandler for commands that work locally via async APIs.
 * Contains the ARRS Request inner class for forwarding commands to the master server.
 *
 * IMPORTANT: Uses SM_BACKEND instead of BACKEND_INSTANCES to avoid shadowing
 * BankSystemGenericRequest.BACKEND_INSTANCES (of type BankSystemModBackend.Instances)
 * inherited through the AsyncForwardingRequest chain.
 */
public class AsyncStockMarketCommandHandler implements IAsyncStockMarketCommandHandler {
    // Use SM_BACKEND to avoid shadowing BankSystemGenericRequest.BACKEND_INSTANCES
    private static StockMarketModBackend.ServerInstances SM_BACKEND;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        SM_BACKEND = backend;
    }

    // === FunctionType enum for forwarded commands ===
    // Currently empty - future forwarded commands (data operations) will be added here.
    // manage/devTestScreen work locally via async APIs, op/deop are master-only.
    public enum FunctionType {
        // Future forwarded commands go here
        // Example: Stockmarket_cancelAllOrders, Stockmarket_createMarket, etc.
    }

    // === Codec map ===
    private static AsyncFunctionDataCodecs codecPacket(@Nullable StreamCodec<RegistryFriendlyByteBuf, ?> inputParamsCodec) {
        return new AsyncFunctionDataCodecs(CommandIdentifyAndDataPacket.streamCodec(inputParamsCodec), ByteBufCodecs.BOOL.cast());
    }

    private static AsyncFunctionDataCodecs codecPacket(@Nullable StreamCodec<RegistryFriendlyByteBuf, ?> inputParamsCodec, @Nullable StreamCodec<RegistryFriendlyByteBuf, ?> outputParamsCodec) {
        return new AsyncFunctionDataCodecs(CommandIdentifyAndDataPacket.streamCodec(inputParamsCodec), outputParamsCodec);
    }

    public static final Map<FunctionType, AsyncFunctionDataCodecs> codecs = new HashMap<>() {{
        // Future forwarded command codecs go here
    }};

    // === InputData ===
    public static class InputData extends AsyncFunctionInputData<FunctionType> {
        public InputData(FunctionType function, byte[] encodedParams) {
            super(function, codecs.get(function).inputParamsCodec, encodedParams);
        }

        public InputData(FunctionType function) {
            super(function, codecs.get(function).inputParamsCodec);
        }

        public static <T> InputData of(FunctionType functionType, UUID commandExecutorPlayer, T result) {
            CommandIdentifyAndDataPacket<T> packet = CommandIdentifyAndDataPacket.of(commandExecutorPlayer, result);
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, packet, InputData::new);
        }

        public static InputData of(FunctionType functionType, UUID commandExecutorPlayer) {
            CommandIdentifyAndDataPacket packet = CommandIdentifyAndDataPacket.of(commandExecutorPlayer, null);
            return (InputData) AsyncFunctionInputData.of(codecs.get(functionType).inputParamsCodec, functionType, packet, InputData::new);
        }
    }

    // === OutputData ===
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

    // === Request class for ARRS forwarding ===
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
                info("Sending request to server for command: " + input.function.toString());
            return super.sendRequestToServer(input);
        }

        @Override
        public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
            CommandIdentifyAndDataPacket inputData = input.decodeParams();
            UUID executorPlayer = inputData.commandExecutorPlayer;
            if (executorPlayer == null) {
                error("Commands can only be called by a player! Command: " + input.function.toString());
                return CompletableFuture.completedFuture(OutputData.of(input.function, false));
            }
            String playerName = tryGetPlayerName(executorPlayer);
            String playerInfo = " from player: " + playerName;
            if (playerSender != null) {
                playerName = tryGetPlayerName(playerSender);
                warn("The player '" + playerName + "' try's to call the command: '" + input.function.toString() + "' directly from the client side, which is not allowed!");
                return CompletableFuture.completedFuture(OutputData.of(input.function, false));
            }

            if (AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Received request to handle on master server for command: " + input.function.toString() + playerInfo);

            // Use SM_BACKEND (the stockmarket backend) for command execution
            IServerStockMarketCommandHandler commandHandler = SM_BACKEND.COMMAND_HANDLER.getSync();
            if (commandHandler == null) {
                if (SM_BACKEND.MARKET_MANAGER.getSync() == null) {
                    throw new RuntimeException("[AsyncStockMarketCommandHandler]: This server is configured to be a slave server but the slave seems not to be connected to its master.");
                }
                throw new RuntimeException("StockMarket command handler not found");
            }

            boolean result = false;
            switch (input.function) {
                // Future forwarded command cases go here
            }

            return CompletableFuture.completedFuture(OutputData.of(input.function, result));
        }

        @Override
        protected boolean isAllowedToCallByClient(InputData input) {
            return false;
        }
    }

    // === Network setup ===
    public static void setupNetworkPacket() {
        Request instance = Request.instance;
    }

    private CompletableFuture<OutputData> sendRequest(InputData input) {
        CompletableFuture<OutputData> future = new CompletableFuture<>();
        CompletableFuture<OutputData> tmpFuture = Request.instance.sendRequestToMaster(input);
        tmpFuture.thenAccept(outputData -> {
            if (AsyncForwardingRequest.DEBUG_ENABLE_LOGS)
                info("Response received for request: " + input.function.toString());
            future.complete(outputData);
        });
        return future;
    }

    // === Parameter packaging record ===
    public record CommandIdentifyAndDataPacket<T>(UUID commandExecutorPlayer, T extra) {
        public static <T> CommandIdentifyAndDataPacket<T> of(UUID commandExecutorPlayer, T extra) {
            return new CommandIdentifyAndDataPacket<>(commandExecutorPlayer, extra);
        }

        public static <T> void encode(RegistryFriendlyByteBuf buf, CommandIdentifyAndDataPacket<T> params, StreamCodec<RegistryFriendlyByteBuf, T> extraCodec) {
            buf.writeUUID(params.commandExecutorPlayer);
            ExtraCodecUtils.nullable(extraCodec).encode(buf, params.extra);
        }

        public static <T> CommandIdentifyAndDataPacket<T> decode(RegistryFriendlyByteBuf buf, StreamCodec<RegistryFriendlyByteBuf, T> extraCodec) {
            UUID commandExecutorPlayer = buf.readUUID();
            T extra = ExtraCodecUtils.nullable(extraCodec).decode(buf);
            return new CommandIdentifyAndDataPacket<>(commandExecutorPlayer, extra);
        }

        public static <T> StreamCodec<RegistryFriendlyByteBuf, CommandIdentifyAndDataPacket<T>> streamCodec(StreamCodec<RegistryFriendlyByteBuf, T> extraCodec) {
            return StreamCodec.of(
                    (buf, params) -> encode(buf, params, extraCodec),
                    buf -> decode(buf, extraCodec)
            );
        }
    }

    // === IAsyncStockMarketCommandHandler implementation ===

    @Override
    public CompletableFuture<Boolean> stockmarket_manage_async(@NotNull UUID executor) {
        if (!MultiServerUtils.checkConnectionToMaster(executor))
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SM_BACKEND.MARKET_MANAGER.getAsync().isStockmarketAdminAsync(executor).thenAcceptAsync(result -> {
            ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
            if (result) {
                OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.MANAGEMENT);
                future.complete(true);
            } else {
                ServerPlayerUtilities.printToClientConsole(player, "This command is only for StockMarket admins!");
                future.complete(false);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> stockmarket_devTestScreen_async(@NotNull UUID executor) {
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
        if (player != null) {
            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.DEVELOPMENT);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    // === Response handling ===
    private static void handleResponse(CompletableFuture<OutputData> response, UUID executor) {
        response.whenComplete((result, throwable) -> {
            String playerName = Request.tryGetPlayerName(executor);
            if (throwable != null) {
                String text = "Async command execution failed for player: " + playerName;
                error(text, throwable);
                ServerPlayerUtilities.printToClientConsole(executor, text);
                return;
            }
            boolean success = (Boolean) result.decodeResult();
            String text = "Async command execution result for command " + result.function.name() + " from player: " + playerName + " Result: " + (success ? "Success" : "Failure");
            info(text);
            if (!success) {
                ServerPlayerUtilities.printToClientConsole(executor, text);
            }
        });
    }

    // === Logging ===
    private static void info(String msg) {
        SM_BACKEND.LOGGER.info("[AsyncStockMarketCommandHandler] " + msg);
    }

    private static void error(String msg) {
        SM_BACKEND.LOGGER.error("[AsyncStockMarketCommandHandler] " + msg);
    }

    private static void error(String msg, Throwable e) {
        SM_BACKEND.LOGGER.error("[AsyncStockMarketCommandHandler] " + msg, e);
    }

    private static void warn(String msg) {
        SM_BACKEND.LOGGER.warn("[AsyncStockMarketCommandHandler] " + msg);
    }
}
