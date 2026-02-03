package su.nightexpress.coinsengine;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.command.CommandManager;
import su.nightexpress.coinsengine.config.Config;
import su.nightexpress.coinsengine.config.Lang;
import su.nightexpress.coinsengine.config.Perms;
import su.nightexpress.coinsengine.currency.CurrencyManager;
import su.nightexpress.coinsengine.currency.CurrencyRegistry;
import su.nightexpress.coinsengine.data.DataHandler;
import su.nightexpress.coinsengine.hook.HookPlugin;
import su.nightexpress.coinsengine.hook.impl.DeluxeCoinflipHook;
import su.nightexpress.coinsengine.hook.impl.PlaceholderAPIHook;
import su.nightexpress.coinsengine.migration.MigrationManager;
import su.nightexpress.coinsengine.tops.TopManager;
import su.nightexpress.coinsengine.user.UserManager;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.config.PluginDetails;
import su.nightexpress.nightcore.util.Plugins;

import java.util.Optional;

public class CoinsEnginePlugin extends NightPlugin {

    private DataHandler      dataHandler;
    private UserManager      userManager;
    private CurrencyRegistry currencyRegistry;
    private CurrencyManager  currencyManager;
    private TopManager       topManager;
    private MigrationManager migrationManager;
    private CommandManager   commandManager;
    private BukkitTask       databaseSyncTask;

    @Override
    protected void onStartup() {
        super.onStartup();
        CoinsEngineAPI.load(this);
        this.currencyRegistry = new CurrencyRegistry();
    }

    @Override
    public void enable() {
        this.dataHandler = new DataHandler(this);
        this.userManager = new UserManager(this, this.currencyRegistry, this.dataHandler);
        this.currencyManager = new CurrencyManager(this, this.currencyRegistry, this.dataHandler, this.userManager);
        this.commandManager = new CommandManager(this, this.currencyRegistry, this.currencyManager);

        this.dataHandler.setup();
        this.userManager.setup();
        this.currencyManager.setup();

        this.scheduleDatabaseSynchronization();

        if (Config.isTopsEnabled()) {
            this.topManager = new TopManager(this, this.currencyRegistry);
            this.topManager.setup();
        }

        if (Config.isMigrationEnabled()) {
            this.migrationManager = new MigrationManager(this, this.dataHandler, this.userManager, this.currencyRegistry, this.currencyManager);
            this.migrationManager.setup();
        }

        this.commandManager.setup();

        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderAPIHook.setup(this);
        }

        if (Plugins.isInstalled(HookPlugin.DELUXE_COINFLIP)) {
            this.runTask(task -> DeluxeCoinflipHook.setup(this));
        }
    }

    @Override
    protected boolean disableCommandManager() {
        return true;
    }

    @Override
    protected void addRegistries() {
        this.registerLang(Lang.class);
    }

    @Override
    public void disable() {
        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderAPIHook.shutdown();
        }

        if (this.commandManager != null) this.commandManager.shutdown();
        if (this.topManager != null) this.topManager.shutdown();
        if (this.migrationManager != null) this.migrationManager.shutdown();
        if (this.userManager != null) this.userManager.shutdown();
        if (this.dataHandler != null) this.dataHandler.shutdown();
        if (this.currencyManager != null) this.currencyManager.shutdown();
        if (this.databaseSyncTask != null) {
            this.databaseSyncTask.cancel();
            this.databaseSyncTask = null;
        }
    }

    @Override
    protected void onShutdown() {
        super.onShutdown();
        this.currencyRegistry.removeAll();
        CoinsEngineAPI.clear();
    }

    @Override
    @NotNull
    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Economy", new String[]{"coinsengine", "coe"})
            .setConfigClass(Config.class)
            .setPermissionsClass(Perms.class);
    }

    private void scheduleDatabaseSynchronization() {
        String databaseType = Config.DATABASE_TYPE.get();
        if ("SQLITE".equalsIgnoreCase(databaseType)) return;

        int intervalSeconds = Config.DATABASE_SYNC_INTERVAL.get();
        if (intervalSeconds < 0) return;

        long periodTicks = intervalSeconds * 20L;
        if (periodTicks <= 0L) return;

        this.databaseSyncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (this.dataHandler != null) {
                this.dataHandler.onSynchronize();
            }
        }, periodTicks, periodTicks);
    }

    @NotNull
    public CommandManager getCommander() {
        return this.commandManager;
    }

    @NotNull
    public CurrencyRegistry getCurrencyRegistry() {
        return this.currencyRegistry;
    }

    @NotNull
    public CurrencyManager getCurrencyManager() {
        return this.currencyManager;
    }

    @NotNull
    public Optional<TopManager> getTopManager() {
        return Optional.ofNullable(this.topManager);
    }

    @NotNull
    public Optional<MigrationManager> getMigrationManager() {
        return Optional.ofNullable(this.migrationManager);
    }

    @NotNull
    public DataHandler getDataHandler() {
        return this.dataHandler;
    }

    @NotNull
    public UserManager getUserManager() {
        return this.userManager;
    }
}
