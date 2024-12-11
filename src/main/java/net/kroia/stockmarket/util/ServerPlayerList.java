package net.kroia.stockmarket.util;

import net.kroia.stockmarket.banking.BankUser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerPlayerList implements ServerSaveable {

    private static final Map<UUID, String> uuidToNameMap = new HashMap<>();
    private static final Map<String, UUID> nameToUUIDMap = new HashMap<>();

    public static void addPlayer(UUID uuid, String name)
    {
        if(uuidToNameMap.containsKey(uuid))
        {
            String oldName = uuidToNameMap.get(uuid);
            nameToUUIDMap.remove(oldName);
        }
        uuidToNameMap.put(uuid, name);
        nameToUUIDMap.put(name, uuid);
    }
    public static void addPlayer(ServerPlayer player)
    {
        addPlayer(player.getUUID(), player.getName().getString());
    }


    public static void removePlayer(UUID uuid)
    {
        String name = uuidToNameMap.remove(uuid);
        nameToUUIDMap.remove(name);
    }

    public static String getPlayerName(UUID uuid)
    {
        return uuidToNameMap.get(uuid);
    }

    public static UUID getPlayerUUID(String name)
    {
        return nameToUUIDMap.get(name);
    }

    public static boolean hasPlayer(UUID uuid)
    {
        return uuidToNameMap.containsKey(uuid);
    }

    public static boolean hasPlayer(String name)
    {
        return nameToUUIDMap.containsKey(name);
    }


    public static ServerPlayer getPlayer(UUID uuid)
    {
        if(uuid == null)
            return null;

        // Get the Minecraft server_sender instance
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            System.err.println("Server instance is null. Are you calling this from the server_sender?");
            return null;
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayer(uuid); // Returns null if the player is not online
    }
    public static ServerPlayer getPlayer(String name)
    {
        return getPlayer(getPlayerUUID(name));
    }

    public static void saveToTag(CompoundTag tag)
    {
        ServerPlayerList playerList = new ServerPlayerList();
        playerList.save(tag);
    }

    @Override
    public void save(CompoundTag tag) {
        ListTag users = new ListTag();
        for (Map.Entry<UUID, String> entry : uuidToNameMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            bankTag.putUUID("uuid", entry.getKey());
            bankTag.putString("name", entry.getValue());
            users.add(bankTag);
        }
        tag.put("users", users);
    }

    public static void loadFromTag(CompoundTag tag)
    {
        ServerPlayerList playerList = new ServerPlayerList();
        playerList.load(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        ListTag users = tag.getList("users", 10);
        for (int i = 0; i < users.size(); i++) {
            CompoundTag userTag = users.getCompound(i);
            UUID uuid = userTag.getUUID("uuid");
            String name = userTag.getString("name");
            addPlayer(uuid, name);
        }

    }
}
