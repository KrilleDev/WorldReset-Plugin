package com.example.worldreset.commands;

import com.example.worldreset.WorldResetPlugin;
import com.example.worldreset.manager.ConfirmationManager;
import com.example.worldreset.manager.MessageManager;
import com.example.worldreset.service.WorldResetService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Implementiert "/reset", "/reset all", "/reset confirm" und "/reset &lt;welt&gt;".
 */
public class ResetCommand implements CommandExecutor, TabCompleter {

    /** Interner Sonderwert fuer eine ausstehende Bestaetigung des Komplett-Resets. */
    public static final String ALL_WORLDS_TOKEN = "__ALL__";

    private final WorldResetPlugin plugin;
    private final MessageManager messages;
    private final ConfirmationManager confirmations;
    private final WorldResetService service;

    public ResetCommand(WorldResetPlugin plugin, MessageManager messages,
                         ConfirmationManager confirmations, WorldResetService service) {
        this.plugin = plugin;
        this.messages = messages;
        this.confirmations = confirmations;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /reset  -> Standardwelt zuruecksetzen (nach Bestaetigung)
        if (args.length == 0) {
            return handleDefaultWorldRequest(sender);
        }

        // /reset confirm -> ausstehende Bestaetigung ausfuehren
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            return handleConfirm(sender);
        }

        // /reset all -> KOMPLETTEN Server-Reset anfordern (nach Bestaetigung)
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            return handleAllWorldsRequest(sender);
        }

        // /reset <welt> -> angegebene Welt zuruecksetzen (nach Bestaetigung)
        if (args.length == 1) {
            return handleOtherWorldRequest(sender, args[0]);
        }

        messages.send(sender, "usage");
        return true;
    }

    private boolean handleDefaultWorldRequest(CommandSender sender) {
        if (!sender.hasPermission("worldreset.reset")) {
            messages.send(sender, "no-permission");
            return true;
        }

        String defaultWorld = plugin.getConfig().getString("default-world", "world");

        if (service.isResetting(defaultWorld)) {
            messages.send(sender, "already-resetting");
            return true;
        }

        confirmations.addPending(sender, defaultWorld);
        messages.send(sender, "confirm-required-cascade", "%world%", defaultWorld);
        return true;
    }

    private boolean handleOtherWorldRequest(CommandSender sender, String worldName) {
        if (!sender.hasPermission("worldreset.reset.other")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (!service.existsLoadedOrOnDisk(worldName)) {
            messages.send(sender, "world-not-found", "%world%", worldName);
            return true;
        }

        if (service.isResetting(worldName)) {
            messages.send(sender, "already-resetting");
            return true;
        }

        String defaultWorld = plugin.getConfig().getString("default-world", "world");
        confirmations.addPending(sender, worldName);
        if (worldName.equalsIgnoreCase(defaultWorld)) {
            messages.send(sender, "confirm-required-cascade", "%world%", worldName);
        } else {
            messages.send(sender, "confirm-required", "%world%", worldName);
        }
        return true;
    }

    private boolean handleAllWorldsRequest(CommandSender sender) {
        if (!sender.hasPermission("worldreset.reset.all")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (service.isAnyResetInProgress()) {
            messages.send(sender, "already-resetting");
            return true;
        }

        confirmations.addPending(sender, ALL_WORLDS_TOKEN);
        messages.send(sender, "confirm-required-all");
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        Optional<String> pendingWorld = confirmations.consumePending(sender);
        if (pendingWorld.isEmpty()) {
            messages.send(sender, "no-pending-confirmation");
            return true;
        }

        String worldName = pendingWorld.get();

        if (ALL_WORLDS_TOKEN.equals(worldName)) {
            if (!sender.hasPermission("worldreset.reset.all")) {
                messages.send(sender, "no-permission");
                return true;
            }
            service.resetAllWorlds(sender);
            return true;
        }

        String defaultWorld = plugin.getConfig().getString("default-world", "world");
        boolean isDefaultWorld = worldName.equalsIgnoreCase(defaultWorld);

        // Berechtigung erneut pruefen, falls sie zwischen Anfrage und Bestaetigung entzogen wurde.
        String requiredPermission = isDefaultWorld ? "worldreset.reset" : "worldreset.reset.other";
        if (!sender.hasPermission(requiredPermission)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (isDefaultWorld) {
            service.resetWorldCascade(sender, worldName);
        } else {
            service.resetWorld(sender, worldName, false);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(input)) {
                suggestions.add(name);
            }
        }

        if ("confirm".startsWith(input)) {
            suggestions.add("confirm");
        }
        if ("all".startsWith(input)) {
            suggestions.add("all");
        }

        return suggestions;
    }
}
