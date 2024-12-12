package net.kroia.stockmarket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

public class ModCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // /money                               - Show balance
        // /money add                           - Add money to self
        // /money add <user>                    - Add money to another player
        // /money send <user> <amount>          - Send money to another player
        // /money circulation                   - Show money circulation of all players + bots
        dispatcher.register(
                Commands.literal("money")
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Admin-only for adding money
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = player.getName().getString();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                            // Execute the command on the server_sender
                                            return executeAddMoney(player, username, amount);
                                        })) // Add to self
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();

                                                    // Get arguments
                                                    String username = StringArgumentType.getString(context, "username");
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                                    // Execute the command on the server_sender
                                                    return executeAddMoney(player, username, amount);
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("send")
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();

                                                    // Get arguments
                                                    String fromPlayer = player.getName().getString();
                                                    String toPlayer = StringArgumentType.getString(context, "username");
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                                    // Execute the command on the server_sender
                                                    return executeAddMoneySend(player,fromPlayer, toPlayer, amount);
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("circulation")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    long circulation = ServerBankManager.getMoneyCirculation();
                                    player.sendSystemMessage(Component.literal("Circulation: "+MoneyBank.ITEM_ID + circulation));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server_sender
                            return showBalance(player);
                        })

        );


        // /bank                                                - Show bank balance (money and items)
        // /bank <username> show                                - Show bank balance of another player
        // /bank <username> create <itemID> <amount>            - Create a bank for another player
        // /bank <username> setBalance <itemID> <amount>        - Set balance of a bank for another player
        // /bank <username> delete <itemID>                     - Delete a bank for another player
        dispatcher.register(
                Commands.literal("bank")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server_sender
                            return bank_show(player, player.getName().getString());
                        })
                        .then(Commands.argument("username", StringArgumentType.string()).suggests((context, builder) -> {
                                            builder.suggest("\""+ ModSettings.MarketBot.USER_NAME +"\"");
                                            return builder.buildFuture();
                                        })
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("show")
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();
                                            String username = StringArgumentType.getString(context, "username");

                                            // Execute the balance command on the server_sender
                                            return bank_show(player, username);
                                        })
                                )
                                .then(Commands.literal("create")
                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                    for(String suggestion : suggestions) {
                                                        builder.suggest("\""+suggestion+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("balance", LongArgumentType.longArg(0))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                            long balance = LongArgumentType.getLong(context, "balance");
                                                            String username = StringArgumentType.getString(context, "username");


                                                            // Execute the command on the server_sender
                                                            return bank_create(player, username, itemID, balance);
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("setBalance")
                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                    for(String suggestion : suggestions) {
                                                        builder.suggest("\""+suggestion+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("balance", LongArgumentType.longArg(0))
                                                        .executes(context -> {
                                                            CommandSourceStack source = context.getSource();
                                                            ServerPlayer player = source.getPlayerOrException();

                                                            // Get arguments
                                                            String itemID = StringArgumentType.getString(context, "itemID");
                                                            long balance = LongArgumentType.getLong(context, "balance");
                                                            String username = StringArgumentType.getString(context, "username");


                                                            // Execute the command on the server_sender
                                                            return bank_setBalance(player, username, itemID, balance);
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("itemID", StringArgumentType.string()).suggests((context, builder) -> {
                                                    ArrayList<String> suggestions = ServerMarket.getTradeItemIDs();
                                                    for(String suggestion : suggestions) {
                                                        builder.suggest("\""+suggestion+"\"");
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();

                                                    // Get arguments
                                                    String itemID = StringArgumentType.getString(context, "itemID");
                                                    String username = StringArgumentType.getString(context, "username");


                                                    // Execute the command on the server_sender
                                                    return bank_delete(player, username, itemID);
                                                })
                                        )
                                )
                        )


        );
    }


    private static int executeAddMoney(ServerPlayer executor, String username, int amount) {
        // Server-side logic for adding money
        ServerPlayer targetPlayer = executor.getServer().getPlayerList().getPlayerByName(username);
        if(targetPlayer == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + username + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        ServerBankManager.getMoneyBank(targetPlayer.getUUID()).deposit(amount); // Example call
        executor.sendSystemMessage(
                Component.literal("Added " + amount + " to " + username + "'s account!")
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAddMoneySend(ServerPlayer executor, String fromUser, String toUser, int amount) {
        // Server-side logic for adding money
        ServerPlayer from = executor.getServer().getPlayerList().getPlayerByName(fromUser);
        ServerPlayer to = executor.getServer().getPlayerList().getPlayerByName(toUser);
        if(from == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + fromUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        if(to == null) {
            executor.sendSystemMessage(
                    Component.literal("Player " + toUser + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }
        Bank fromBank = ServerBankManager.getMoneyBank(from.getUUID());
        Bank toBank = ServerBankManager.getMoneyBank(to.getUUID());

        if(fromBank.transfer(amount, toBank))
        {
            StockMarketMod.printToClientConsole(from, "Transfered " + amount + "$ from " + fromUser + " to " + toUser + "'s account!");
            StockMarketMod.printToClientConsole(to, "Received " + amount + "$ from " + fromUser + "'s account!");
        }
        else {
            if(fromBank.getBalance() < amount)
                StockMarketMod.printToClientConsole(from, "You don't have enough money to transfer " + amount + "$!");
            else
                StockMarketMod.printToClientConsole(from, "Failed to transfer " + amount + "$ to " + toUser + "'s account!");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showBalance(ServerPlayer player) {
        // Server-side logic for showing the balance
        long balance = ServerBankManager.getMoneyBank(player.getUUID()).getBalance(); // Example call
        player.sendSystemMessage(Component.literal("Your balance: " + balance));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_show(ServerPlayer player, String targetPlayer) {
        // Server-side logic for showing the balance
        UUID targetPlayerUUID = ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        if(user == null) {
            player.sendSystemMessage(Component.literal("User not found: " + targetPlayer));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal(user.toString()));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_create(ServerPlayer player,String targetPlayer, String itemID, long balance) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        Bank bank = user.getBank(itemID);
        boolean created = bank == null;

        bank = user.createItemBank(itemID, balance);
        if(created)
            player.sendSystemMessage(Component.literal("Bank created for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        else
            player.sendSystemMessage(Component.literal("Bank already exists for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        return Command.SINGLE_SUCCESS;
    }
    private static int bank_setBalance(ServerPlayer player,String targetPlayer, String itemID, long balance) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        Bank bank = user.getBank(itemID);
        if(bank == null) {
            player.sendSystemMessage(Component.literal("Bank not found for " + player.getName().getString()+" ItemID: "+itemID));
            return Command.SINGLE_SUCCESS;
        }
        bank.setBalance(balance);
        player.sendSystemMessage(Component.literal("Bank balance set for " + player.getName().getString()+"\n"+bank.toStringNoOwner()));
        return Command.SINGLE_SUCCESS;
    }

    private static int bank_delete(ServerPlayer player,String targetPlayer, String itemID) {
        // Server-side logic for creating a bank
        UUID targetPlayerUUID =  ServerPlayerList.getPlayerUUID(targetPlayer);

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        if(user.removeBank(itemID))
            player.sendSystemMessage(Component.literal("Bank deleted for " + player.getName().getString()+" ItemID: "+itemID));
        else {
            player.sendSystemMessage(Component.literal("Bank not found for " + player.getName().getString()+" ItemID: "+itemID));
        }
        return Command.SINGLE_SUCCESS;
    }
}
