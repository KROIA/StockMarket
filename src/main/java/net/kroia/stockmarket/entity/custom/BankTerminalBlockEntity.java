package net.kroia.stockmarket.entity.custom;

import com.mojang.datafixers.util.Pair;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.menu.custom.BankTerminalContainerMenu;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateBankTerminalBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBankDataPacket;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class BankTerminalBlockEntity  extends BlockEntity implements MenuProvider {
    private static class TransferTask implements ServerSaveable
    {
        // negative values: send to market
        // positive values: send to inventory
        private HashMap<String, Integer> transferItems;
        private UUID playerID;
        public TransferTask(UUID playerID, HashMap<String, Integer>transferItems)
        {
            this.playerID = playerID;
            this.transferItems = transferItems;
        }
        private TransferTask()
        {
            transferItems = new HashMap<>();
            playerID = new UUID(0, 0);
        }
        public UUID getPlayerID() {
            return playerID;
        }
        public HashMap<String, Integer> getItemID() {
            return transferItems;
        }

        public int getAmount(String itemID) {
            return transferItems.getOrDefault(itemID, 0);
        }
        public void setAmount(String itemID, int amount) {
            transferItems.put(itemID, amount);
        }

        public static TransferTask createFromTag(CompoundTag tag)
        {
            TransferTask task = new TransferTask();
            if(task.load(tag))
                return task;
            return null;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putUUID("PlayerID", playerID);
            ListTag transferItemsTag = new ListTag();
            for(String itemID : transferItems.keySet())
            {
                CompoundTag itemTag = new CompoundTag();
                int amount = transferItems.get(itemID);
                if(amount == 0)
                    continue;
                itemTag.putString("ItemID", itemID);
                itemTag.putInt("Amount", amount);
                transferItemsTag.add(itemTag);
            }
            tag.put("TransferItems", transferItemsTag);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            if(!tag.contains("PlayerID") || !tag.contains("TransferItems"))
                return false;
            playerID = tag.getUUID("PlayerID");
            ListTag transferItemsTag = tag.getList("TransferItems", 10);
            for(int i = 0; i < transferItemsTag.size(); i++)
            {
                CompoundTag itemTag = transferItemsTag.getCompound(i);
                String itemID = itemTag.getString("ItemID");
                int amount = itemTag.getInt("Amount");
                transferItems.put(itemID, amount);
            }
            return true;
        }
    }
    private static class TerminalInventory extends ItemStackHandler
    {
        BankTerminalBlockEntity blockEntity;
        public TerminalInventory(BankTerminalBlockEntity blockEntity, int size) {
            super(size);
            this.blockEntity = blockEntity;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            blockEntity.setChanged();
        }

        public int getFreeSpace(String itemID, int amount)
        {
            int freeSpace = 0;
            for (int i = 0; i < this.getSlots(); i++) {
                ItemStack stack = this.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    freeSpace+=64;
                    continue;
                }

                // Get the item's ResourceLocation
                ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());

                // Compare the ResourceLocation to the provided string
                if (itemLocation != null && itemLocation.toString().equals(itemID)) {
                    // Check if the stack can fit the amount
                    freeSpace += stack.getMaxStackSize() - stack.getCount();
                }
            }

            return freeSpace;
        }

        public void fillInventory(String ItemID, int amount)
        {
            for (int i = 0; i < this.getSlots(); i++) {
                if(amount <= 0)
                    return;
                ItemStack stack = this.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    int stackSize = Math.min(amount, 64);
                    amount -= stackSize;
                    this.setStackInSlot(i, StockMarketMod.createItemStackFromId(ItemID, stackSize));
                    continue;
                }

                // Get the item's ResourceLocation
                ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());

                // Compare the ResourceLocation to the provided string
                if (itemLocation != null && itemLocation.toString().equals(ItemID)) {
                    // Check if the stack can fit the amount
                    int freeSpace = stack.getMaxStackSize() - stack.getCount();
                    int stackSize = Math.min(amount, freeSpace);
                    stack.setCount(stack.getCount() + stackSize);
                    amount -= stackSize;
                }
            }
        }

    }
    private static class PlayerData implements ServerSaveable
    {
        private UUID playerID;
        private TerminalInventory inventory;
        private TransferTask transferTask;
        public PlayerData(UUID playerID, BankTerminalBlockEntity blockEntity)
        {
            this.playerID = playerID;
            this.inventory = new TerminalInventory(blockEntity, 27);
            this.transferTask = new TransferTask(playerID, new HashMap<>());
        }
        private PlayerData(BankTerminalBlockEntity blockEntity)
        {
            playerID = new UUID(0, 0);
            inventory = new TerminalInventory(blockEntity, 27);
            transferTask = new TransferTask();
        }
        public UUID getPlayerID() {
            return playerID;
        }
        public TerminalInventory getInventory() {
            return inventory;
        }
        public TransferTask getTransferTask() {
            return transferTask;
        }

        public static PlayerData createFromTag(BankTerminalBlockEntity blockEntity, CompoundTag tag)
        {
            PlayerData playerData = new PlayerData(blockEntity);
            if(playerData.load(tag))
                return playerData;
            return null;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putUUID("PlayerID", playerID);
            tag.put("Inventory", inventory.serializeNBT());
            CompoundTag transferTaskTag = new CompoundTag();
            if(!transferTask.save(transferTaskTag))
                return false;
            tag.put("TransferTask", transferTaskTag);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            if(!tag.contains("PlayerID") || !tag.contains("Inventory") || !tag.contains("TransferTask"))
                return false;
            playerID = tag.getUUID("PlayerID");
            inventory.deserializeNBT(tag.getCompound("Inventory"));
            CompoundTag transferTaskTag = tag.getCompound("TransferTask");
            transferTask = TransferTask.createFromTag(transferTaskTag);
            return transferTask != null;
        }
    }

    private static final Component TITLE =
            Component.translatable("container." + StockMarketMod.MODID + ".bank_terminal_block_entity");

    private final HashMap<UUID, PlayerData> playerDataTable = new HashMap<>();


    private int transferTickAmount = 1;
    private int lastTickCounter = 0;
    private int tickCounter = 0;


    //private final LazyOptional<ItemStackHandler> optional = LazyOptional.of(() -> this.inventory);

    public BankTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.BANK_TERMINAL_BLOCK_ENTITY.get(), pos, state);
    }

    /*
    public void openInventory(Player player) {
        if (level != null && !level.isClientSide) {
            UUID playerUUID = player.getUUID();
            GlobalInventoryData data = GlobalInventoryData.get(level);
            SimpleContainer inventory = data.getOrCreateInventory(playerUUID);

            // Open the inventory in a chest-like GUI
            player.openMenu(new SimpleMenuProvider((id, playerInventory, p) ->
                    ChestMenu.threeRows(id, playerInventory, inventory),
                    new TextComponent("Custom Ender Chest")
            ));
        }
    }*/


    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        CompoundTag data = nbt.getCompound(StockMarketMod.MODID);

       // this.inventory.deserializeNBT(tutorialmodData.getCompound("Inventory"));
        transferTickAmount = data.getInt("TransferTickAmount");

        ListTag playerDataTag = data.getList("PlayerData", ListTag.TAG_COMPOUND);
        for(int i = 0; i < playerDataTag.size(); i++)
        {
            CompoundTag playerDataCompound = playerDataTag.getCompound(i);
            PlayerData playerData = PlayerData.createFromTag(this, playerDataCompound);
            if(playerData != null)
            {
                playerDataTable.put(playerData.getPlayerID(), playerData);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        CompoundTag data = new CompoundTag();
        //tutorialmodData.put("Inventory", this.inventory.serializeNBT());
        data.putInt("TransferTickAmount", transferTickAmount);
        ListTag playerInventoriesTag = new ListTag();
        for(UUID playerID : playerDataTable.keySet())
        {
            CompoundTag dataTag = new CompoundTag();
            if(playerDataTable.get(playerID).save(dataTag))
            {
                playerInventoriesTag.add(dataTag);
            }
        }
        data.put("PlayerData", playerInventoriesTag);
        nbt.put(StockMarketMod.MODID, data);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap) {
        return super.getCapability(cap);


        //return cap == ForgeCapabilities.ITEM_HANDLER ? this.optional.cast() : super.getCapability(cap);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        //this.optional.invalidate();
    }

    /*public LazyOptional<ItemStackHandler> getOptional() {
        return this.optional;
    }*/

    public ItemStackHandler getInventory(UUID playerID) {
        PlayerData playerData = getPlayerData(playerID);
        return playerData.getInventory();
    }

    public HashMap<UUID, ItemStackHandler> getPlayerInventories() {
        HashMap<UUID, ItemStackHandler> playerInventories = new HashMap<>();
        for(UUID playerID : this.playerDataTable.keySet())
        {
            playerInventories.put(playerID, this.playerDataTable.get(playerID).getInventory());
        }
        return playerInventories;
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory, Player pPlayer) {
        return new BankTerminalContainerMenu(pContainerId, pPlayerInventory, this);
    }

    public MenuProvider getMenuProvider() {
        return this;
    }

    private PlayerData getPlayerData(UUID playerID) {
        if(!playerDataTable.containsKey(playerID)) {
            playerDataTable.put(playerID, new PlayerData(playerID, this));
            this.setChanged();
        }
        return playerDataTable.get(playerID);
    }

    public void handlePacket(UpdateBankTerminalBlockEntityPacket packet, ServerPlayer player) {
        String userNameStr  = player.getName().getString();
        BankUser user = ServerBankManager.getUser(player.getUUID());
        if (user == null) {
            StockMarketMod.LOGGER.error("BankUser is null for user: " + userNameStr);
            return;
        }

        PlayerData playerData = getPlayerData(player.getUUID());
        TerminalInventory inventory = playerData.getInventory();

        //boolean invFull = false;
        if(packet.isSendItemsToMarket())
        {
            HashMap<String, Integer> items = new HashMap<>();
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    continue;
                }

                String itemID = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString();

                Bank bankAccount = user.getBank(itemID);
                if(bankAccount == null) {
                    StockMarketMod.LOGGER.error("User: "+userNameStr +" does not have a bank account for item " + itemID);
                    continue;
                }

                if(!items.containsKey(itemID))
                    items.put(itemID, stack.getCount());
                else
                    items.put(itemID, items.get(itemID) + stack.getCount());
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }

            for(String itemID : items.keySet()) {
                int amount = items.get(itemID);
                if(amount <= 0)
                    continue;
                Bank bankAccount = user.getBank(itemID);
                if(bankAccount == null) {
                    StockMarketMod.LOGGER.error("BankAccount is null for user: "+userNameStr +" for item " + itemID);
                    continue;
                }
                bankAccount.deposit(amount);
                StockMarketMod.LOGGER.info("Sent " + amount + " " + itemID + " to market");
            }
        }
        else {
            HashMap<String, Integer> itemTransferToMarketAmounts = packet.getItemTransferToMarketAmounts();
            for(String itemID : itemTransferToMarketAmounts.keySet()) {
                int amount = itemTransferToMarketAmounts.get(itemID);
                if(amount <= 0)
                    continue;
                Bank bankAccount = user.getBank(itemID);
                if(bankAccount == null) {
                    StockMarketMod.LOGGER.error("BankAccount is null for user: "+userNameStr +" for item " + itemID);
                    continue;
                }
                // Add to inventory
                int freeSpace = inventory.getFreeSpace(itemID, amount);
                if(freeSpace < amount)
                {
                    //invFull = true;
                    amount = freeSpace;
                }
                if(bankAccount.withdraw(amount))
                    inventory.fillInventory(itemID, amount);

                StockMarketMod.LOGGER.info("Added " + amount + " " + itemID + " to inventory");

            }
        }



        // mark the block entity for saving
        setChanged();
        SyncBankDataPacket.sendPacket(player);
    }


    // Inside MyBlockEntity
    public void tick(Level level, BlockPos pos, BlockState state) {
        // Your block entity logic here
        //System.out.println("Block entity is ticking!");
        if(tickCounter - lastTickCounter >= transferTickAmount) {
            lastTickCounter = tickCounter;
        }
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
        if(level.isClientSide)
            return;
        if (t instanceof BankTerminalBlockEntity blockEntity) {
            blockEntity.tick(level, blockPos, blockState);
        }
    }
/*
    private final SimpleContainer  inventory = new SimpleContainer(27);


    public BankTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.BANK_TERMINAL_BLOCK_ENTITY.get(), pos, state);
        StockMarketMod.LOGGER.info("BankTerminalBlockEntity created at position " + pos);
    }

    public Direction getFacing() {
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.worldPosition);
            if (state.getBlock() instanceof StockMarketBlock) {
                return state.getValue(StockMarketBlock.FACING);
            }
        }
        return Direction.NORTH; // Default fallback
    }

    public void onBlockPlacedBy(BlockState state, @Nullable LivingEntity placer) {
        // Custom logic when block is placed
    }


    // Inventory access methods
    public SimpleContainer getItems() {
        return inventory;
    }

    public void setItem(int index, ItemStack stack) {
        inventory.setItem(index, stack);
        setChanged(); // Mark the BlockEntity as updated
    }

    public ItemStack getItem(int index) {
        return inventory.getItem(index);
    }

    public void setChangedAndNotify() {
        setChanged(); // Mark for saving
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }



    // Implement MenuProvider for the container
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.my_custom_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new BankTerminalContainerMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(@Nullable CompoundTag tag)
    {
        super.saveAdditional(tag);
        CompoundTag dataTag = new CompoundTag();
        ListTag itemList = new ListTag();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i); // Store slot index
                stack.save(itemTag);      // Save the item stack
                itemList.add(itemTag);
            }
        }

        dataTag.put("Inventory", itemList);
        tag.put(StockMarketMod.MODID, dataTag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag dataTag = tag.getCompound(StockMarketMod.MODID);
        ListTag itemList = dataTag.getList("Inventory", 10);

        for (int i = 0; i < itemList.size(); i++) {
            CompoundTag itemTag = itemList.getCompound(i);
            int slot = itemTag.getInt("Slot");

            if (slot >= 0 && slot < inventory.getContainerSize()) {
                inventory.setItem(slot, ItemStack.of(itemTag));
            }
        }
    }*/


}