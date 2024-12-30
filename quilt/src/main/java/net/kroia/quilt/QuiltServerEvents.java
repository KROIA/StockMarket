package net.kroia.quilt;

import net.kroia.stockmarket.util.ServerEvents;
import org.quiltmc.qsl.lifecycle.api.event.ServerLifecycleEvents;

public class QuiltServerEvents {
    public static void register() {
        // World load event
        ServerLifecycleEvents.STARTING.register(server-> {
            ServerEvents.onServerStart(server); // Handle world load (start)
        });

        // World save event
        ServerLifecycleEvents.STOPPING.register(server -> {
            ServerEvents.onServerStop(server); // Handle world save (stop)
        });
    }
}
