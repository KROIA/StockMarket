package net.kroia.stockmarket.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private Netty channel of a {@link Connection}. Vanilla 1.21.1 has no public getter
 * (NeoForge adds {@code channel()}, Fabric does not). Used by the test helper {@code PacketTap}.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {

    /** @return the connection's Netty channel */
    @Accessor("channel")
    Channel stockmarket$getChannel();
}
