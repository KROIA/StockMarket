package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class ClientPlugin extends Plugin implements INetworkPayloadConverter
{
    private boolean isRemoved = false;
    private PluginGuiElement guiElement;

    public ClientPlugin(UUID instanceID) {
        super(instanceID);
    }


    public void setRemoved()
    {
        isRemoved = true;
    }
    boolean isRemoved()
    {
        return isRemoved;
    }


    @Override
    public void decode(FriendlyByteBuf buf) {

    }

    @Override
    public void encode(FriendlyByteBuf buf) {

    }
}
