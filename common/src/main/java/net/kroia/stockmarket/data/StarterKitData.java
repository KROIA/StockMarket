package net.kroia.stockmarket.data;

import net.kroia.banksystem.minecraft.block.BankSystemBlocks;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have claimed the starter kit.
 * Claim data is persisted as a JSON set of UUID strings.
 */
public class StarterKitData {

    private Set<String> claimedPlayerUUIDs = new HashSet<>();

    /**
     * Checks whether the given player has already claimed the starter kit.
     */
    public boolean hasClaimed(UUID playerUUID) {
        return claimedPlayerUUIDs.contains(playerUUID.toString());
    }

    /**
     * Marks the given player as having claimed the starter kit.
     */
    public void markClaimed(UUID playerUUID) {
        claimedPlayerUUIDs.add(playerUUID.toString());
    }

    /**
     * Returns the internal set of claimed player UUID strings (for serialization).
     */
    public Set<String> getClaimedPlayerUUIDs() {
        return claimedPlayerUUIDs;
    }

    /**
     * Replaces the internal set of claimed player UUID strings (for deserialization).
     */
    public void setClaimedPlayerUUIDs(Set<String> claimedPlayerUUIDs) {
        this.claimedPlayerUUIDs = claimedPlayerUUIDs != null ? claimedPlayerUUIDs : new HashSet<>();
    }

    /**
     * Returns the list of items included in the starter kit.
     * Contains a reasonable set of resources for testing the stock market.
     */
    public static List<ItemStack> getStarterKitItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(BankSystemBlocks.BANK_TERMINAL_BLOCK.get()));
        items.add(new ItemStack(BankSystemBlocks.BANK_UPLOAD_BLOCK.get()));
        items.add(new ItemStack(BankSystemBlocks.BANK_DOWNLOAD_BLOCK.get()));
        items.add(new ItemStack(BankSystemBlocks.BANKSYSTEM_DISPLAY_BLOCK.get(),5));
        items.add(new ItemStack(BankSystemBlocks.ATM_BLOCK.get(),5));
        items.add(new ItemStack(BankSystemItems.MONEY100.get(),2));
        items.add(new ItemStack(StockMarketBlocks.STOCK_MARKET_BLOCK.get()));
        items.add(new ItemStack(StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get(), 5));
        items.add(new ItemStack(Items.IRON_INGOT, 10));
        items.add(new ItemStack(Items.BREAD, 32));
        items.add(new ItemStack(Items.OAK_LOG, 10));
        items.add(new ItemStack(Items.CRAFTING_TABLE, 1));
        items.add(new ItemStack(Items.FURNACE, 1));
        items.add(new ItemStack(Items.CHEST, 2));
        return items;
    }
}
