package com.example.worldreset.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class SeedManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SeedManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Long getFixedSeed(String worldName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("seeds.fixed");
        if (section == null || !section.isSet(worldName)) {
            return null;
        }
        return section.getLong(worldName);
    }

    public Long getLastUsedSeed(String worldName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("seeds.last-used");
        if (section == null || !section.isSet(worldName)) {
            return null;
        }
        return section.getLong(worldName);
    }

    public long determineNewSeed(String worldName) {
        Long fixed = getFixedSeed(worldName);
        if (fixed != null) {
            return fixed;
        }

        Long last = getLastUsedSeed(worldName);
        long candidate;
        do {
            candidate = random.nextLong();
        } while (last != null && candidate == last);
        return candidate;
    }

    public void recordUsedSeed(String worldName, long seed) {
        plugin.getConfig().set("seeds.last-used." + worldName, seed);
        plugin.saveConfig();
    }
}
