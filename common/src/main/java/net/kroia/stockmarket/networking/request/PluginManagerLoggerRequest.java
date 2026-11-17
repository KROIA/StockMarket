package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SM-329: ARRS request that reads or sets the master's plugin manager logger switch
 * ({@link ServerPluginManager#isLoggerEnabled()}), shown as a checkbox at the top of the Plugin management screen.
 * Slaves forward it to the master like every other plugin-management request. Admin only.
 * <p>
 * Input: {@code null} = only read the current state; {@code true}/{@code false} = set it (applied at once and
 * saved with the plugin manager's world data on the next save).<br>
 * Output: the master's state after the call, or {@code null} if the request was refused (not an admin, gated slave,
 * no plugin manager).
 */
public class PluginManagerLoggerRequest extends StockMarketGenericRequest<Boolean, Boolean> {

    @Override
    public String getRequestTypeID() {
        return PluginManagerLoggerRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(@Nullable Boolean enable, String slaveID, @Nullable UUID playerSender) {
        // Same admin flag as the rest of the plugin management screen; fail closed on a missing sender.
        if (playerSender == null || !playerIsAdmin(playerSender))
            return CompletableFuture.completedFuture(null);
        // T-123 / T-129 slave gates: setting is mutating, reading is management data.
        boolean allowed = enable == null
                ? NetworkGate.isManagementReadAllowed(slaveID, "PluginManagerLoggerRequest")
                : NetworkGate.isMutatingCallAllowed(slaveID, "PluginManagerLoggerRequest");
        if (!allowed || !(getPluginManager() instanceof ServerPluginManager pluginManager))
            return CompletableFuture.completedFuture(null);
        if (enable != null)
            pluginManager.setLoggerEnabled(enable);
        return CompletableFuture.completedFuture(pluginManager.isLoggerEnabled());
    }

    /** Writes a nullable boolean: presence flag, then the value. */
    private static void writeNullable(RegistryFriendlyByteBuf buf, @Nullable Boolean value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeBoolean(value);
    }

    private static @Nullable Boolean readNullable(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBoolean() : null;
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Boolean input) {
        writeNullable(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        writeNullable(buf, output);
    }

    @Override
    public Boolean decodeInput(RegistryFriendlyByteBuf buf) {
        return readNullable(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return readNullable(buf);
    }
}
