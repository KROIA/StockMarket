package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.UtilitiesPlatform;
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
        if(uuid == null)
            return null;
        String name = uuidToNameMap.get(uuid);
        if(name != null)
            return name;

        ServerPlayer player = getPlayer(name);
        if(player == null)
            return null;
        addPlayer(player);
        name = player.getName().toString();
        return name;
    }

    public static Map<UUID, String> getUuidToNameMap() {
        return uuidToNameMap;
    }

    public static UUID getPlayerUUID(String name)
    {
        if(name == null)
            return null;
        UUID playerUUID = nameToUUIDMap.get(name);
        if(playerUUID != null)
            return playerUUID;

        ServerPlayer player = getPlayer(name);
        if(player == null)
            return null;
        addPlayer(player);
        playerUUID = player.getUUID();
        return playerUUID;
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
        MinecraftServer server = UtilitiesPlatform.getServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayer(uuid); // Returns null if the player is not online
    }
    public static ServerPlayer getPlayer(String name)
    {
        if(name == null)
            return null;
        // Get the Minecraft server_sender instance
        MinecraftServer server = UtilitiesPlatform.getServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayerByName(name); // Returns null if the player is not online
    }

    public static boolean saveToTag(CompoundTag tag)
    {
        ServerPlayerList playerList = new ServerPlayerList();
        return playerList.save(tag);
    }
    public static boolean loadFromTag(CompoundTag tag)
    {
        ServerPlayerList playerList = new ServerPlayerList();
        return playerList.load(tag);
    }

    @Override
    public boolean save(CompoundTag tag) {
        ListTag users = new ListTag();
        for (Map.Entry<UUID, String> entry : uuidToNameMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            bankTag.putUUID("uuid", entry.getKey());
            bankTag.putString("name", entry.getValue());
            users.add(bankTag);
        }
        tag.put("users", users);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(!tag.contains("users"))
            return false;
        boolean success = true;
        ListTag users = tag.getList("users", 10);
        for (int i = 0; i < users.size(); i++) {
            CompoundTag userTag = users.getCompound(i);
            if(!userTag.contains("uuid") || !userTag.contains("name")){
                success = false;
                continue;
            }
            UUID uuid = userTag.getUUID("uuid");
            String name = userTag.getString("name");
            addPlayer(uuid, name);
        }
        return success;
    }
}
