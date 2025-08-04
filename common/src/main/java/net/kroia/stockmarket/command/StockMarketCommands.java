package net.kroia.stockmarket.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class StockMarketCommands {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {


        // /StockMarket ManagementGUI      - Open the management GUI to create/remove/change markets
        // /StockMarket save               - Save market data
        // /StockMarket load               - Load market data
        // /StockMarket closeAllMarkets    - Closes the market for trading for all items on the market
        // /StockMarket openAllMarkets     - Opens the market for trading for all items on the market

        dispatcher.register(
                Commands.literal("StockMarket")
                        .then(Commands.literal("ManagementGUI")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if(player != null) {
                                        SyncTradeItemsPacket.sendPacket(player);
                                        OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.STOCKMARKET_MANAGEMENT);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("save")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveAllAsync().thenAccept(success -> {
                                        if(success)
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSavedMessage());
                                        else
                                            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataSaveFailedMessage());
                                    });


                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("load")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    if(BACKEND_INSTANCES.SERVER_DATA_HANDLER.loadAll())
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadedMessage());
                                    else
                                        PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getStockMarketDataLoadFailedMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("closeAllMarkets")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setAllMarketsOpen(false);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowClosedAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("openAllMarkets")
                                .requires(StockMarketCommands::isPlayerAdmin)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setAllMarketsOpen(true);
                                    // Notify all serverPlayers
                                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceIsNowOpenAllMessage());

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }

    private static boolean isPlayerAdmin(CommandSourceStack source)
    {
        return source.hasPermission(BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.ADMIN_PERMISSION_LEVEL.get());
    }
}
