package com.example.worldreset.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Verwaltet Welt-Seeds in der config.yml:
 * - "seeds.fixed.<welt>": optionaler fester Seed, der bei jedem Reset dieser Welt
 *   verwendet wird (z.B. fuer reproduzierbare Test-Welten).
 * - "seeds.last-used.<welt>": vom Plugin automatisch gepflegter, zuletzt tatsaechlich
 *   verwendeter Seed. Wird genutzt, um sicherzustellen, dass ein neu zufaellig
 *   generierter Seed niemals mit dem vorherigen identisch ist.
 */
public class SeedManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SeedManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Liefert den in der config.yml fest eingetragenen Seed fuer diese Welt,
     * oder null, wenn keiner konfiguriert ist (-> zufaelliger Seed wird genutzt).
     */
    public Long getFixedSeed(String worldName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("seeds.fixed");
        if (section == null || !section.isSet(worldName)) {
            return null;
        }
        return section.getLong(worldName);
    }

    /**
     * Liefert den zuletzt fuer diese Welt verwendeten Seed, oder null, wenn noch keiner bekannt ist.
     */
    public Long getLastUsedSeed(String worldName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("seeds.last-used");
        if (section == null || !section.isSet(worldName)) {
            return null;
        }
        return section.getLong(worldName);
    }

    /**
     * Ermittelt den beim naechsten Reset zu verwendenden Seed:
     * - Ist ein fester Seed konfiguriert, wird IMMER dieser zurueckgegeben.
     * - Ansonsten wird ein zufaelliger Seed erzeugt, der garantiert nicht dem
     *   zuletzt verwendeten Seed dieser Welt entspricht.
     */
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

    /**
     * Speichert den tatsaechlich verwendeten Seed als "zuletzt verwendet" in der config.yml.
     */
    public void recordUsedSeed(String worldName, long seed) {
        plugin.getConfig().set("seeds.last-used." + worldName, seed);
        plugin.saveConfig();
    }
}
