package net.kroia.stockmarket.stockmarket.marketmanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class User implements ServerSaveable {


    public static final StreamCodec<RegistryFriendlyByteBuf, User> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, p -> p.userUUID,
            ByteBufCodecs.STRING_UTF8, p -> p.userName,
            ByteBufCodecs.BOOL, p -> p.isStockMarketAdmin,
            PlayerPreferences.STREAM_CODEC, p -> p.preferences,
            User::new
    );

    private UUID userUUID;
    private String userName;
    private boolean isStockMarketAdmin = false;
    private PlayerPreferences preferences = new PlayerPreferences();

    private User()
    {

    }
    private User(UUID userUUID, String userName, boolean isStockMarketAdmin, PlayerPreferences preferences)
    {
        this.userUUID = userUUID;
        this.userName = userName;
        this.isStockMarketAdmin = isStockMarketAdmin;
        this.preferences = preferences;
    }
    private User(UUID userUUID, String userName, boolean isStockMarketAdmin)
    {
        this(userUUID, userName, isStockMarketAdmin, new PlayerPreferences());
    }
    public User(UUID userUUID, String userName) {
        this.userUUID = userUUID;
        this.userName = userName;
    }
    /**
     * Creates a copy of the user with a changed name, preserving UUID, admin status, and preferences.
     */
    public static User createWithChangedName(User oldUser, String newName)
    {
        return new User(oldUser.userUUID, newName, oldUser.isStockMarketAdmin, oldUser.preferences);
    }
    public static @Nullable User createFromTag(CompoundTag tag)
    {
        User user = new User();
        if(!user.load(tag)) {
            return null; // Invalid data
        }
        return user;
    }


    public UUID getUUID() {
        return userUUID;
    }
    public String getName() {
        return userName;
    }
    public boolean isStockMarketAdmin() {
        return isStockMarketAdmin;
    }
    public void setStockMarketAdmin(boolean isBankModAdmin) {
        this.isStockMarketAdmin = isBankModAdmin;
    }
    public PlayerPreferences getPreferences() {
        return preferences;
    }
    public void setPreferences(PlayerPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putUUID("userUUID", userUUID);
        tag.putString("userName", userName);
        tag.putBoolean("isStockMarketAdmin", isStockMarketAdmin);

        // Save player preferences as a sub-tag
        CompoundTag prefsTag = new CompoundTag();
        preferences.save(prefsTag);
        tag.put("preferences", prefsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("userUUID") || !tag.contains("userName")) {
            StockMarketMod.LOGGER.error("User.load: missing required fields");
            return false; // Invalid data
        }
        this.userUUID = tag.getUUID("userUUID");
        this.userName = tag.getString("userName");
        if(tag.contains("isStockMarketAdmin"))
            this.isStockMarketAdmin = tag.getBoolean("isStockMarketAdmin");
        else
            this.isStockMarketAdmin = false;

        // Load player preferences (backward compatible — defaults to empty if not present)
        if (tag.contains("preferences")) {
            CompoundTag prefsTag = tag.getCompound("preferences");
            preferences = new PlayerPreferences();
            preferences.load(prefsTag);
        } else {
            preferences = new PlayerPreferences();
        }
        return true;
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("userUUID", userUUID.toString());
        jsonObject.addProperty("userName", userName);
        jsonObject.addProperty("isStockMarketAdmin", isStockMarketAdmin);
        return jsonObject;
    }
    @Override
    public String toString()
    {
        return JsonUtilities.toPrettyString(toJson());
    }
}
