package net.kroia.stockmarket.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kroia.stockmarket.bank.MoneyBank;
import net.kroia.stockmarket.bank.ServerBank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class ModCommands {
    // Method to register commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        /*dispatcher.register(Commands.literal("mycommand")
                .then(Commands.literal("subcommand")
                        .executes(ModCommands::runSubCommand))
                .executes(ModCommands::runMainCommand));*/

        //dispatcher.register(Commands.literal("money")
        //        .executes(ModCommands::command_money));

        dispatcher.register(Commands.literal("money")
                .then(Commands.literal("add").executes(ModCommands::command_money_add))
                .executes(ModCommands::command_money));
    }

    // Main command logic
    /*private static int runMainCommand(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess((Supplier<Component>) Component.literal("Main command executed!"), false);
        return 1; // Return a result code
    }

    // Subcommand logic
    private static int runSubCommand(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess((Supplier<Component>) Component.literal("Subcommand executed!"), false);
        return 1; // Return a result code
    }*/

    private static int command_money(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if(player == null) {
            return 0;
        }

        MoneyBank bank = ServerBank.getBank(player.getStringUUID());
        if(bank == null) {
            return 0;
        }

        context.getSource().sendSuccess((Supplier<Component>) Component.literal("You have $"+bank.getBalance()), false);
        return 1; // Return a result code
    }

    private static int command_money_add(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if(player == null) {
            return 0;
        }

        MoneyBank bank = ServerBank.getBank(player.getStringUUID());
        if(bank == null) {
            return 0;
        }

        int amount = context.getArgument("amount", Integer.class);
        bank.deposit(amount);


        context.getSource().sendSuccess((Supplier<Component>) Component.literal("You have $"+bank.getBalance()), false);
        return 1; // Return a result code
    }
}
