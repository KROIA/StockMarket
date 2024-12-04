package net.kroia.stockmarket.menu.custom;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ObjectHolder;

public class ChartMenu extends AbstractContainerMenu {
    private final StockMarketBlockEntity blockEntity;
    private final ContainerLevelAccess containerAccess;

    // ObjectHolder for menu type (to be registered)
    @ObjectHolder(registryName = "chart_menu", value = "mychartmod:chart_menu")
    public static final MenuType<ChartMenu> CHART_MENU = null;

    public ChartMenu(int id, Inventory playerInventory, StockMarketBlockEntity blockEntity, ContainerLevelAccess access) {
        super(CHART_MENU, id);
        this.blockEntity = blockEntity;
        this.containerAccess = access;

        // Optionally, add slots for inventory or custom items here
        // Example: addPlayerInventory(playerInventory);
    }

    // Constructor for creating on the client side
    public ChartMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                id,
                playerInventory,
                (StockMarketBlockEntity) playerInventory.player.level().getBlockEntity(buffer.readBlockPos()),
                ContainerLevelAccess.NULL
        );
    }


    @Override
    public boolean stillValid(Player player) {
        return this.containerAccess.evaluate((level, pos) -> {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof StockMarketBlockEntity;
        }, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // Handle shift-clicking if needed
    }

    public StockMarketBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
