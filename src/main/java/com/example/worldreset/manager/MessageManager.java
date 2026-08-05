package com.example.worldreset.manager;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageManager {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration config;
    private String prefix;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);

        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {
        }

        prefix = color(config.getString("prefix", ""));
    }

    public void reload() {
        load();
    }

    private String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    private String raw(String key) {
        return config.getString(key, key);
    }

    public String format(String key, String... placeholders) {
        String message = color(raw(key));
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                message = message.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return message;
    }

    public String formatWithPrefix(String key, String... placeholders) {
        return prefix + format(key, placeholders);
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(formatWithPrefix(key, placeholders));
    }

    public void broadcast(String key, String... placeholders) {
        String message = formatWithPrefix(key, placeholders);
        plugin.getServer().getConsoleSender().sendMessage(message);
        plugin.getServer().getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }
}
