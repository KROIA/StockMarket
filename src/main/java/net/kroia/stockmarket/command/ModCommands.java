package net.kroia.stockmarket.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public class ModCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // /money add <amount> [username]
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

                                            // Execute the command on the server
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

                                                    // Execute the command on the server
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

                                                    // Execute the command on the server
                                                    return executeAddMoneySend(player,fromPlayer, toPlayer, amount);
                                                })
                                        )
                                )
                        )
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayerOrException();

                            // Execute the balance command on the server
                            return showBalance(player);
                        })

        );

        // /bank
        dispatcher.register(
                Commands.literal("bank")
                        .then(Commands.literal("show")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();

                                    // Execute the balance command on the server
                                    return bank_show(player, player.getName().getString());
                                })
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerPlayer player = source.getPlayerOrException();

                                            // Get arguments
                                            String username = StringArgumentType.getString(context, "username");


                                            // Execute the command on the server
                                            return bank_show(player, username);
                                        })
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
        MinecraftServer server = player.getServer();
        UUID targetPlayerUUID = null;
        assert server != null;
        GameProfile profile = Objects.requireNonNull(server.getProfileCache()).get(targetPlayer).get();
        if (profile != null) {
            targetPlayerUUID = profile.getId(); // Returns the UUID as a string
        }
        if(targetPlayerUUID == null) {
            player.sendSystemMessage(
                    Component.literal("Player " + targetPlayer + " not found.")
            );
            return Command.SINGLE_SUCCESS;
        }

        BankUser user = ServerBankManager.getUser(targetPlayerUUID);
        player.sendSystemMessage(Component.literal(user.toString()));
        return Command.SINGLE_SUCCESS;
    }
}
