package net.kroia.stockmarket.entity.custom;

import com.mojang.datafixers.util.Pair;
import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.menu.custom.BankTerminalContainerMenu;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateBankTerminalBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBankDataPacket;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.Item;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class BankTerminalBlockEntity  extends BlockEntity implements MenuProvider {
    private static class TransferTask implements ServerSaveable
    {
        // negative values: send to market
        // positive values: send to inventory
        private final BankTerminalBlockEntity blockEntity;
        private final HashMap<String, Long> transferItems;
        private UUID playerID;
        public TransferTask(BankTerminalBlockEntity blockEntity, UUID playerID, HashMap<String, Long>transferItems)
        {
            this.blockEntity = blockEntity;
            this.playerID = playerID;
            this.transferItems = transferItems;
        }
        private TransferTask(BankTerminalBlockEntity blockEntity)
        {
            this.blockEntity = blockEntity;
            this.transferItems = new HashMap<>();
            this.playerID = new UUID(0, 0);
        }
        public UUID getPlayerID() {
            return playerID;
        }
        private HashMap<String, Long> getItemID() {
            return transferItems;
        }

        private long getAmount(String itemID) {
            return transferItems.getOrDefault(itemID, 0L);
        }
        private void setAmount(String itemID, long amount) {
            if(amount == 0 && transferItems.containsKey(itemID))
                transferItems.remove(itemID);
            else
                transferItems.put(itemID, amount);
            blockEntity.setChanged();
        }

        public boolean createTask(String itemID, long amount)
        {
            if(amount == 0)
                return false;
            BankUser bank = ServerBankManager.getUser(playerID);
            if(bank == null) {
                // Create bank account for this item if it can be traded
                ArrayList<String> keys = new ArrayList<>();
                keys.add(itemID);
                bank = ServerBankManager.createUser(playerID, keys, true,0 );
            }

            Bank bankAccount = bank.getBank(itemID);
            if(bankAccount == null) {
                // Create item bank account
                if (ServerMarket.hasItem(itemID)) {
                    bankAccount = bank.createItemBank(itemID, 0);
                } else
                    return false;
            }

            setAmount(itemID, getAmount(itemID) + amount);
            return true;
        }

        public void cancelTask(String itemID)
        {
            setAmount(itemID, 0);
        }
        public void cancelTasks()
        {
            transferItems.clear();
        }
        public int taskCount()
        {
            return transferItems.size();
        }

        public boolean processTaskStep(long amountToProcess)
        {
            if(amountToProcess<=0)
            {
                cancelTasks();
                return false;
            }
            BankUser bank = ServerBankManager.getUser(playerID);
            if(bank == null) {
                cancelTasks();
                return false;
            }
            ArrayList<String> keys = new ArrayList<>(transferItems.keySet());
            for(String itemID : keys)
            {
                long amount = transferItems.get(itemID);
                if(amount == 0)
                    continue;
                Bank bankAccount = bank.getBank(itemID);
                if(bankAccount == null) {
                    cancelTask(itemID);
                    continue;
                }
                TerminalInventory inventory = blockEntity.playerDataTable.get(playerID).inventory;

                if(amount < 0)
                {
                    amount = Math.min(-amount, amountToProcess);
                    if(amount <= 0) {
                        cancelTask(itemID);
                        continue;
                    }
                    long removedAmount = inventory.removeItem(itemID, amount);
                    if(removedAmount > 0)
                    {
                        bankAccount.deposit(removedAmount);
                        setAmount(itemID, getAmount(itemID) + removedAmount);
                        return true;
                    }
                    else {
                        cancelTask(itemID);
                        continue;
                    }
                }
                else
                {
                    amount = Math.min(amount, amountToProcess);
                    amount = Math.min(amount, bankAccount.getBalance());
                    if(amount <= 0) {
                        cancelTask(itemID);
                        continue;
                    }
                    long addedAmount = inventory.addItem(itemID, amount);
                    if(addedAmount > 0)
                    {
                        if(!bankAccount.withdraw(addedAmount))
                        {
                            // error
                            StockMarketMod.LOGGER.error("Failed to withdraw " + addedAmount + " " + itemID + " from bank account of user " + playerID);
                            inventory.removeItem(itemID, addedAmount);
                            cancelTask(itemID);
                            continue;
                        }
                        setAmount(itemID, getAmount(itemID) - addedAmount);
                        return true;
                    }
                    else
                        cancelTask(itemID); // inventory full
                }
            }
            return false;
        }

        public static TransferTask createFromTag(BankTerminalBlockEntity blockEntity,CompoundTag tag)
        {
            TransferTask task = new TransferTask(blockEntity);
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
                long amount = transferItems.get(itemID);
                if(amount == 0)
                    continue;
                itemTag.putString("ItemID", itemID);
                itemTag.putLong("Amount", amount);
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
                long amount = itemTag.getLong("Amount");
                transferItems.put(itemID, amount);
            }
            return true;
        }
    }
    private static class TerminalInventory extends ItemStackHandler implements ServerSaveable
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

        public long getFreeSpace(String itemID, int amount)
        {
            long freeSpace = 0;
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
        public HashMap<String, Long> getItemCount()
        {
            HashMap<String, Long> items = new HashMap<>();
            for (int i = 0; i < this.getSlots(); i++) {
                ItemStack stack = this.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    continue;
                }

                // Get the item's ResourceLocation
                ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());

                // Compare the ResourceLocation to the provided string
                if (itemLocation != null) {
                    // Check if the stack can fit the amount
                    String itemID = itemLocation.toString();
                    if(!items.containsKey(itemID))
                        items.put(itemID, (long)stack.getCount());
                    else
                        items.put(itemID, items.get(itemID) + stack.getCount());
                }
            }
            return items;
        }

        public long addItem(String ItemID, long amount)
        {
            if(amount <=0)
                return 0;
            long orgAmount = amount;
            for (int i = 0; i < this.getSlots(); i++) {
                if(amount <= 0)
                    return orgAmount;
                ItemStack stack = this.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    int stackSize = (int)Math.min(amount, 64);
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
                    int stackSize = (int)Math.min(amount, freeSpace);
                    stack.setCount(stack.getCount() + stackSize);
                    amount -= stackSize;
                }
            }
            blockEntity.setChanged();
            return orgAmount - amount;
        }
        public long removeItem(String itemID, long amount)
        {
            long orgAmount = amount;
            for (int i = 0; i < this.getSlots(); i++) {
                if(amount <= 0)
                    return orgAmount-amount;
                ItemStack stack = this.getStackInSlot(i);

                // If the slot is empty, it has space
                if (stack.isEmpty()) {
                    continue;
                }

                // Get the item's ResourceLocation
                ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());

                // Compare the ResourceLocation to the provided string
                if (itemLocation != null && itemLocation.toString().equals(itemID)) {
                    // Check if the stack can fit the amount
                    int stackSize = (int)Math.min(amount, stack.getCount());
                    stack.shrink(stackSize);
                    amount -= stackSize;
                    if(stack.isEmpty())
                        this.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            blockEntity.setChanged();
            return orgAmount - amount;
        }

        @Override
        public boolean save(CompoundTag tag) {
            ListTag itemList = new ListTag();

            for (int i = 0; i < this.getSlots(); i++) {
                ItemStack stack = this.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    CompoundTag stackTag = new CompoundTag();
                    stackTag.putInt("Slot", i);

                    // Manually store the item ID and count
                    Item item = stack.getItem();
                    stackTag.putString("Item", ForgeRegistries.ITEMS.getKey(item).toString()); // Save the item ID
                    stackTag.putInt("Count", stack.getCount()); // Save the stack count

                    // Optionally save any NBT data attached to the ItemStack
                    /*if (stack.has()) {
                        stackTag.put("tag", stack.getTag());
                    }*/

                    itemList.add(stackTag);
                }
            }

            tag.put("Items", itemList);
            return false;
        }

        @Override
        public boolean load(CompoundTag tag) {
            ListTag itemList = tag.getList("Items", CompoundTag.TAG_COMPOUND); // 10 is the tag type for CompoundTag
            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag stackTag = itemList.getCompound(i);

                int slot = stackTag.getInt("Slot");
                String itemID = stackTag.getString("Item");
                int count = stackTag.getInt("Count");

                // Get the item using the itemID
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemID));
                ItemStack stack = new ItemStack(item, count);

                // Set the NBT data if it exists
                /*if (stackTag.contains("tag")) {
                    stack.setTag(stackTag.getCompound("tag"));
                }*/

                this.setStackInSlot(slot, stack);
            }
            return false;
        }
    }
    private static class PlayerData implements ServerSaveable
    {
        private UUID playerID;
        private TerminalInventory inventory;
        private TransferTask transferTask;

        private final BankTerminalBlockEntity blockEntity;
        public PlayerData(UUID playerID, BankTerminalBlockEntity blockEntity)
        {
            this.blockEntity = blockEntity;
            this.playerID = playerID;
            this.inventory = new TerminalInventory(blockEntity, 27);
            this.transferTask = new TransferTask(blockEntity, playerID, new HashMap<>());
        }
        private PlayerData(BankTerminalBlockEntity blockEntity)
        {
            this.blockEntity = blockEntity;
            playerID = new UUID(0, 0);
            inventory = new TerminalInventory(blockEntity, 27);
            transferTask = new TransferTask(blockEntity);
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
            CompoundTag inventoryTag = new CompoundTag();
            inventory.save(inventoryTag);
            tag.put("Inventory", inventoryTag);
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
            inventory.load(tag.getCompound("Inventory"));
            CompoundTag transferTaskTag = tag.getCompound("TransferTask");
            transferTask = TransferTask.createFromTag(blockEntity,transferTaskTag);
            return transferTask != null;
        }
    }

    private static final Component TITLE =
            Component.translatable("container." + StockMarketMod.MODID + ".bank_terminal_block_entity");

    private final HashMap<UUID, PlayerData> playerDataTable = new HashMap<>();


    private int transferTickAmount = ModSettings.Bank.ITEM_TRANSFER_TICK_INTERVAL;
    private int lastTickCounter = 0;
    private int tickCounter = 0;

    public BankTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntities.BANK_TERMINAL_BLOCK_ENTITY.get(), pos, state);
    }


    @Override
    public void loadAdditional(CompoundTag nbt, HolderLookup.Provider pRegistries) {
        super.loadAdditional(nbt, pRegistries);
        CompoundTag data = nbt.getCompound(StockMarketMod.MODID);

       // this.inventory.deserializeNBT(tutorialmodData.getCompound("Inventory"));
        //transferTickAmount = data.getInt("TransferTickAmount");

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
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider pRegistries) {
        super.saveAdditional(nbt, pRegistries);
        CompoundTag data = new CompoundTag();
        //tutorialmodData.put("Inventory", this.inventory.serializeNBT());
        //data.putInt("TransferTickAmount", transferTickAmount);
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

        HashMap<String, Long> items;
        int sendToMarketSign = 1;
        if(packet.isSendItemsToMarket())
        {
            items = inventory.getItemCount();
            sendToMarketSign = -1;
        }
        else
        {
            // Send to inventory
            items = packet.getItemTransferFromMarket();
        }
        for(String itemID : items.keySet()) {
            long amount = items.get(itemID);
            playerData.transferTask.createTask(itemID, (int)amount*sendToMarketSign);
        }

        /*
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

        */


        // mark the block entity for saving
        setChanged();
        SyncBankDataPacket.sendPacket(player);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        // Your block entity logic here
        //System.out.println("Block entity is ticking!");
        tickCounter++;
        if(tickCounter - lastTickCounter >= transferTickAmount) {
            lastTickCounter = tickCounter;
            for(UUID playerID : playerDataTable.keySet())
            {
                PlayerData playerData = playerDataTable.get(playerID);
                if(playerData.getTransferTask().taskCount() > 0)
                {
                    playerData.getTransferTask().processTaskStep(1);
                }
            }
        }
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
        if(level.isClientSide)
            return;
        if (t instanceof BankTerminalBlockEntity blockEntity) {
            blockEntity.tick(level, blockPos, blockState);
        }
    }


}