package com.example.worldreset.service;

import com.example.worldreset.WorldResetPlugin;
import com.example.worldreset.manager.MessageManager;
import com.example.worldreset.manager.SeedManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Fuehrt den eigentlichen Welt-Reset durch: Spieler in Sicherheit bringen,
 * Welt entladen, Weltordner loeschen und die Welt mit einem garantiert neuen
 * Seed neu erstellen. Bietet zusaetzlich:
 * - eine automatische Nether/End-Kaskade beim Reset der Standardwelt
 * - einen kompletten Server-Reset inkl. Neustart ("/reset all")
 */
public class WorldResetService {

    private static final String TEMP_SAFE_WORLD_NAME = "worldreset_temp_lobby";

    /** Interner Marker im "resettingWorlds"-Set fuer einen laufenden Komplett-Reset. */
    private static final String ALL_MARKER = "__worldreset_all__";

    private final WorldResetPlugin plugin;
    private final MessageManager messages;
    private final SeedManager seedManager;
    private final Set<String> resettingWorlds = ConcurrentHashMap.newKeySet();

    public WorldResetService(WorldResetPlugin plugin, MessageManager messages, SeedManager seedManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.seedManager = seedManager;
    }

    public boolean isResetting(String worldName) {
        return resettingWorlds.contains(normalize(worldName)) || resettingWorlds.contains(ALL_MARKER);
    }

    public boolean isAnyResetInProgress() {
        return !resettingWorlds.isEmpty();
    }

    public boolean existsLoadedOrOnDisk(String worldName) {
        return Bukkit.getWorld(worldName) != null || worldFolder(worldName).isDirectory();
    }

    // ------------------------------------------------------------------
    // Einzel-Reset (z.B. "/reset survival", "/reset world_nether")
    // ------------------------------------------------------------------

    /**
     * Setzt genau die angegebene Welt zurueck (kein Nether/End-Automatismus).
     * Der neue Seed wird ueber den {@link SeedManager} ermittelt: entweder der
     * in der config.yml fest eingetragene Seed, oder ein zufaelliger Seed, der
     * garantiert vom zuletzt fuer diese Welt verwendeten abweicht.
     */
    public void resetWorld(CommandSender initiator, String worldName, boolean teleportAllOnlineToNewWorld) {
        String key = normalize(worldName);
        if (isResetting(worldName) || !resettingWorlds.add(key)) {
            messages.send(initiator, "already-resetting");
            return;
        }

        long seed = seedManager.determineNewSeed(worldName);
        performReset(initiator, worldName, seed, teleportAllOnlineToNewWorld, () -> resettingWorlds.remove(key));
    }

    // ------------------------------------------------------------------
    // Kaskaden-Reset: Standardwelt + zugehoeriges Nether + End
    // ------------------------------------------------------------------

    /**
     * Setzt die angegebene Basiswelt UND ihre zugehoerigen Nether-/End-Welten
     * (Namenskonvention "&lt;basis&gt;_nether" / "&lt;basis&gt;_the_end") zurueck.
     * Alle drei Dimensionen erhalten denselben neuen Seed - genau wie in Vanilla
     * Minecraft, wo ein einziger Welt-Seed alle drei Standarddimensionen steuert.
     * Erst nachdem alle Dimensionen fertig sind, werden Online-Spieler in den
     * neuen Overworld-Spawn teleportiert.
     */
    public void resetWorldCascade(CommandSender initiator, String baseWorldName) {
        if (!existsLoadedOrOnDisk(baseWorldName)) {
            messages.send(initiator, "world-not-found", "%world%", baseWorldName);
            return;
        }

        List<String> targets = buildCascadeTargets(baseWorldName);
        List<String> keys = new ArrayList<>();
        for (String target : targets) {
            keys.add(normalize(target));
        }

        if (isAnyResetInProgress()) {
            messages.send(initiator, "already-resetting");
            return;
        }
        keys.forEach(resettingWorlds::add);

        long seed = seedManager.determineNewSeed(baseWorldName);
        messages.send(initiator, "cascade-start", "%world%", baseWorldName);

        runCascadeStep(initiator, targets, 0, seed, keys);
    }

    private List<String> buildCascadeTargets(String baseWorldName) {
        List<String> targets = new ArrayList<>();
        String nether = baseWorldName + "_nether";
        String end = baseWorldName + "_the_end";
        if (existsLoadedOrOnDisk(nether)) {
            targets.add(nether);
        }
        if (existsLoadedOrOnDisk(end)) {
            targets.add(end);
        }
        // Die Basiswelt (Overworld) wird zuletzt zurueckgesetzt: so koennen Spieler,
        // die aus Nether/End evakuiert werden, zwischenzeitlich sicher in der
        // (noch existierenden) Overworld stehen, bevor am Ende ALLE gemeinsam
        // in den frischen Overworld-Spawn teleportiert werden.
        targets.add(baseWorldName);
        return targets;
    }

    private void runCascadeStep(CommandSender initiator, List<String> targets, int index, long seed, List<String> allKeys) {
        if (index >= targets.size()) {
            allKeys.forEach(resettingWorlds::remove);
            return;
        }
        String worldName = targets.get(index);
        boolean isLastStep = index == targets.size() - 1;
        performReset(initiator, worldName, seed, isLastStep,
                () -> runCascadeStep(initiator, targets, index + 1, seed, allKeys));
    }

    // ------------------------------------------------------------------
    // Kernlogik fuer genau eine Welt (wird von Einzel-Reset & Kaskade genutzt)
    // ------------------------------------------------------------------

    /**
     * Fuehrt den Reset einer einzelnen Welt mit vorgegebenem Seed durch.
     * "onComplete" wird garantiert genau einmal aufgerufen - egal ob der
     * Vorgang erfolgreich war oder mit einem Fehler abgebrochen wurde. So
     * bleiben Sperren (resettingWorlds) und Kaskaden-Ketten immer konsistent.
     */
    private void performReset(CommandSender initiator, String worldName, long seed,
                               boolean teleportAllOnlineToNewWorld, Runnable onComplete) {
        World target = Bukkit.getWorld(worldName);

        // Welteinstellungen VOR dem Loeschen sichern, damit die neue Welt identisch konfiguriert wird.
        Environment environment = target != null ? target.getEnvironment() : guessEnvironment(worldName);
        WorldType worldType = target != null ? target.getWorldType() : WorldType.NORMAL;
        boolean generateStructures = target != null ? target.canGenerateStructures() : true;
        ChunkGenerator generator = target != null ? target.getGenerator() : null;

        messages.send(initiator, "progress-saving");

        if (target != null) {
            if (plugin.getConfig().getBoolean("auto-save-before-reset", false)) {
                target.save();
            }

            World safeWorld = getOrCreateSafeWorld(target);
            List<Player> playersInWorld = new ArrayList<>(target.getPlayers());
            for (Player player : playersInWorld) {
                player.teleport(safeWorld.getSpawnLocation());
                messages.send(player, "teleported-away");
            }

            messages.send(initiator, "progress-unloading");
            boolean unloaded = Bukkit.unloadWorld(target, false);
            if (!unloaded) {
                messages.send(initiator, "error-generic", "%error%",
                        "Welt '" + worldName + "' konnte nicht entladen werden. Hinweis: Die primaere " +
                                "Server-Welt (level-name) kann laut Bukkit-API nicht zur Laufzeit entladen " +
                                "werden - nutze dafuer '/reset all'.");
                onComplete.run();
                return;
            }
        }

        File worldFolder = worldFolder(worldName);
        messages.send(initiator, "progress-deleting");

        // Loeschen des (potenziell grossen) Weltordners asynchron ausfuehren,
        // um den Hauptthread nicht zu blockieren. Welterstellung erfolgt danach
        // wieder synchron, da die Bukkit/Paper-API dies zwingend voraussetzt.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean deleted = deleteDirectory(worldFolder);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!deleted && worldFolder.exists()) {
                    messages.send(initiator, "error-generic", "%error%",
                            "Der Weltordner von '" + worldName + "' konnte nicht vollstaendig geloescht werden.");
                    onComplete.run();
                    return;
                }

                messages.send(initiator, "progress-creating");

                WorldCreator creator = new WorldCreator(worldName)
                        .environment(environment)
                        .type(worldType)
                        .generateStructures(generateStructures)
                        .seed(seed);
                if (generator != null) {
                    creator.generator(generator);
                }

                World newWorld;
                try {
                    newWorld = creator.createWorld();
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Welt '" + worldName + "'", exception);
                    messages.send(initiator, "error-generic", "%error%", String.valueOf(exception.getMessage()));
                    onComplete.run();
                    return;
                }

                if (newWorld == null) {
                    messages.send(initiator, "error-generic", "%error%", "Welt '" + worldName + "' konnte nicht erstellt werden.");
                    onComplete.run();
                    return;
                }

                // Neuen Seed erst JETZT als "zuletzt verwendet" festschreiben, da die
                // Welterstellung erfolgreich war.
                seedManager.recordUsedSeed(worldName, seed);

                if (teleportAllOnlineToNewWorld) {
                    messages.send(initiator, "teleporting-players");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.teleport(newWorld.getSpawnLocation());
                    }
                }

                messages.send(initiator, "progress-done", "%world%", worldName);
                messages.send(initiator, "seed-info", "%seed%", String.valueOf(seed));
                plugin.getLogger().info("Welt '" + worldName + "' wurde zurueckgesetzt. Neuer Seed: " + seed);

                onComplete.run();
            });
        });
    }

    // ------------------------------------------------------------------
    // Kompletter Server-Reset ("/reset all")
    // ------------------------------------------------------------------

    /**
     * Kompletter Server-Reset: alle Spieler werden gekickt, alle Weltordner geloescht
     * und anschliessend wird ein Server-Neustart ausgeloest, damit auch die primaere
     * Welt (die laut Bukkit-API nicht zur Laufzeit entladen werden kann) sauber mit
     * einem neuen Seed neu erzeugt wird. Der neue Seed wird zusaetzlich in
     * server.properties (level-seed) geschrieben, da nach dem Neustart der Server
     * selbst - nicht dieses Plugin - die Welten erzeugt.
     */
    public void resetAllWorlds(CommandSender initiator) {
        if (isAnyResetInProgress() || !resettingWorlds.add(ALL_MARKER)) {
            messages.send(initiator, "already-resetting");
            return;
        }

        String defaultWorld = plugin.getConfig().getString("default-world", "world");
        long seed = seedManager.determineNewSeed(defaultWorld);

        messages.broadcast("broadcast-all-start");
        plugin.getLogger().warning("Kompletter Server-Reset ('/reset all') wurde ausgeloest von: "
                + initiator.getName() + ". Neuer Seed: " + seed);

        // Alle Spieler kicken, bevor irgendetwas geloescht wird.
        String kickMessage = messages.format("kick-message-all-reset");
        Component kickComponent = LegacyComponentSerializer.legacySection().deserialize(kickMessage);
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            player.kick(kickComponent);
        }

        // Namen aller aktuell bekannten Welten VOR dem Entladen sichern.
        List<World> worldsSnapshot = new ArrayList<>(Bukkit.getWorlds());
        List<String> worldNames = new ArrayList<>();
        for (World world : worldsSnapshot) {
            worldNames.add(world.getName());
        }
        String primaryWorldName = worldsSnapshot.isEmpty() ? null : worldsSnapshot.get(0).getName();

        messages.broadcast("progress-deleting-all");

        // Alle Welten AUSSER der primaeren Welt lassen sich sauber entladen.
        // Die primaere Welt (Index 0 / level-name) kann Bukkit zur Laufzeit
        // grundsaetzlich nicht entladen - sie bleibt bis zum Neustart geladen.
        for (World world : worldsSnapshot) {
            if (world.getName().equalsIgnoreCase(primaryWorldName)) {
                continue;
            }
            Bukkit.unloadWorld(world, false);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String worldName : worldNames) {
                deleteDirectory(worldFolder(worldName));
            }

            boolean serverPropertiesUpdated = updateServerPropertiesSeed(seed);

            Bukkit.getScheduler().runTask(plugin, () -> {
                seedManager.recordUsedSeed(defaultWorld, seed);

                if (primaryWorldName != null) {
                    plugin.getLogger().warning("Hinweis: Die primaere Welt '" + primaryWorldName + "' konnte "
                            + "laut Bukkit-API nicht zur Laufzeit entladen werden. Ihr Ordner wurde geloescht; "
                            + "beim finalen Speichern waehrend des Neustarts koennen jedoch vereinzelt "
                            + "Spawn-Chunk-Dateien mit dem alten Seed neu geschrieben werden. Dies betrifft "
                            + "nur einen kleinen Bereich um den alten Spawn.");
                }
                if (!serverPropertiesUpdated) {
                    plugin.getLogger().warning("server.properties konnte nicht automatisch aktualisiert werden. "
                            + "Bitte trage den Seed '" + seed + "' bei Bedarf manuell als 'level-seed' ein.");
                } else {
                    plugin.getLogger().info("server.properties wurde mit level-seed=" + seed + " aktualisiert.");
                }

                messages.broadcast("progress-restarting");
                String restartCommand = plugin.getConfig().getString("restart-command", "restart");
                plugin.getLogger().info("Loese Server-Neustart ueber Befehl '" + restartCommand + "' aus...");

                // Kleine Verzoegerung, damit die letzten Chat-/Konsolen-Nachrichten
                // sicher versendet werden, bevor der Neustart beginnt.
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), restartCommand), 5L);

                resettingWorlds.remove(ALL_MARKER);
            });
        });
    }

    /**
     * Traegt den neuen Seed als "level-seed" in server.properties ein, damit der
     * Server ihn beim gleich folgenden Neustart tatsaechlich verwendet.
     */
    private boolean updateServerPropertiesSeed(long seed) {
        Path propertiesPath = Path.of(System.getProperty("user.dir"), "server.properties");
        File propertiesFile = propertiesPath.toFile();
        if (!propertiesFile.isFile()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(propertiesPath, StandardCharsets.UTF_8);
            Pattern levelSeedLine = Pattern.compile("^level-seed=.*$");
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (levelSeedLine.matcher(lines.get(i)).matches()) {
                    lines.set(i, "level-seed=" + seed);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add("level-seed=" + seed);
            }
            Files.write(propertiesPath, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Konnte server.properties nicht aktualisieren.", exception);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private File worldFolder(String worldName) {
        return new File(Bukkit.getWorldContainer(), worldName);
    }

    private String normalize(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    /**
     * Leitet anhand gaengiger Namenskonventionen (z.B. "_nether", "_the_end")
     * eine plausible Umgebung ab, falls die Welt aktuell nicht geladen ist.
     */
    private Environment guessEnvironment(String worldName) {
        String lower = normalize(worldName);
        if (lower.endsWith("_nether")) {
            return Environment.NETHER;
        }
        if (lower.endsWith("_the_end") || lower.endsWith("_end")) {
            return Environment.THE_END;
        }
        return Environment.NORMAL;
    }

    /**
     * Ermittelt eine sichere Welt fuer Spieler, waehrend "excluding" zurueckgesetzt wird.
     * Reihenfolge: konfigurierte Lobby -> irgendeine andere geladene Welt -> temporaere Welt.
     */
    private World getOrCreateSafeWorld(World excluding) {
        String configuredName = plugin.getConfig().getString("teleport-safe-world", "lobby");
        World configured = Bukkit.getWorld(configuredName);
        if (configured != null && !configured.equals(excluding)) {
            return configured;
        }

        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(excluding)) {
                return world;
            }
        }

        World temp = Bukkit.getWorld(TEMP_SAFE_WORLD_NAME);
        if (temp == null) {
            plugin.getLogger().info("Keine andere Welt vorhanden - erstelle temporaere Sicherheitswelt '" + TEMP_SAFE_WORLD_NAME + "'.");
            WorldCreator tempCreator = new WorldCreator(TEMP_SAFE_WORLD_NAME)
                    .type(WorldType.FLAT)
                    .environment(Environment.NORMAL)
                    .generateStructures(false);
            temp = tempCreator.createWorld();
        }
        return temp;
    }

    private boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return true;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }
}
