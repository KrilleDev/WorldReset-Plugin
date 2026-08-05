package com.example.worldreset;

import com.example.worldreset.commands.ResetCommand;
import com.example.worldreset.manager.ConfirmationManager;
import com.example.worldreset.manager.MessageManager;
import com.example.worldreset.manager.SeedManager;
import com.example.worldreset.service.WorldResetService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldResetPlugin extends JavaPlugin {

    private MessageManager messageManager;
    private ConfirmationManager confirmationManager;
    private SeedManager seedManager;
    private WorldResetService worldResetService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.confirmationManager = new ConfirmationManager();
        this.seedManager = new SeedManager(this);
        this.worldResetService = new WorldResetService(this, messageManager, seedManager);

        ResetCommand resetCommand = new ResetCommand(this, messageManager, confirmationManager, worldResetService);
        PluginCommand command = getCommand("reset");
        if (command != null) {
            command.setExecutor(resetCommand);
            command.setTabCompleter(resetCommand);
        } else {
            getLogger().severe("Der Befehl 'reset' konnte nicht registriert werden. Bitte plugin.yml pruefen.");
        }

        getLogger().info("WorldReset wurde erfolgreich aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("WorldReset wurde deaktiviert.");
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public SeedManager getSeedManager() {
        return seedManager;
    }

    public WorldResetService getWorldResetService() {
        return worldResetService;
    }
}
