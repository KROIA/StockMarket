package net.kroia.stockmarket.testing;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Test helper for sp cells: runs code on the client (render) thread of this JVM. Touches client-only classes, so
 * call it only after checking that a client exists here (integrated server, client instances set).
 */
public final class ClientThread {

    private ClientThread() {
    }

    /**
     * @param task the code to run on the client thread
     * @return completes with the task's result
     */
    public static <T> CompletableFuture<T> submit(Supplier<T> task) {
        return Minecraft.getInstance().submit(task);
    }
}
