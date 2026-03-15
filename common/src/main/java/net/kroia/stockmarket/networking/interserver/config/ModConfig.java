package net.kroia.stockmarket.networking.interserver.config;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kroia.stockmarket.StockMarketModBackend;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple JSON config loaded from {@code config/hubmod.json}.
 *
 * Example hub config (hubmod.json on the hub server):
 * <pre>
 * {
 *   "isHub": true,
 *   "hubTcpPort": 25575,
 *   "sharedSecret": "change-me-please"
 * }
 * </pre>
 *
 * Example child config (hubmod.json on server_a / server_b):
 * <pre>
 * {
 *   "isHub": false,
 *   "serverId": "server_a",
 *   "hubHost": "127.0.0.1",
 *   "hubTcpPort": 25575,
 *   "sharedSecret": "change-me-please"
 * }
 * </pre>
 */
public class ModConfig {
    private static StockMarketModBackend.CommonInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.CommonInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "hubmod.json");

    private static ModConfig instance;

    // ── Fields (mapped from JSON) ────────────────────────────────────────────

    /** If true, this server acts as the hub and listens for child connections. */
    public boolean isHub = false;

    /** [Hub only] Port the hub TCP listener binds to. */
    public int hubTcpPort = 25575;

    /** [Hub only] Secret token that child servers must send in their handshake. */
    public String sharedSecret = "change-me-please";

    /** [Child only] Unique ID for this child server, e.g. "server_a". */
    public String serverId = "server_a";

    /** [Child only] Hostname or IP of the hub server. */
    public String hubHost = "127.0.0.1";

    // ── Load / Save ──────────────────────────────────────────────────────────

    public static ModConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            defaults.save();
            info("[HubMod] Created default config at "+ CONFIG_PATH);
            return defaults;
        }
        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
            info("[HubMod] Config loaded — isHub="+cfg.isHub+", serverId="+cfg.serverId);
            return cfg;
        } catch (IOException e) {
            error("[HubMod] Failed to read config, using defaults", e);
            return new ModConfig();
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            error("[HubMod] Failed to save default config", e);
        }
    }


    protected static void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("HubMod/Config"+message);
    }
    protected static void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/Config"+message);
    }
    protected static void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/Config"+message, throwable);
    }
    protected static void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("HubMod/Config"+message);
    }
    protected static void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("HubMod/Config"+message);
    }
}