package vg.civcraft.mc.civmodcore.players.scoreboard.bottom;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vg.civcraft.mc.civmodcore.scheduling.CivScheduler;
import vg.civcraft.mc.civmodcore.scheduling.CivTask;

public class BottomLine implements Comparable<BottomLine> {

    private Map<UUID, String> texts;
    private String identifier;
    private CivTask updater;
    private int priority;

    BottomLine(String identifier, int priority) {
        this.identifier = identifier;
        this.priority = priority;
        // Mutated by event handlers on region/entity threads while the updatePeriodically task iterates it
        // on the global thread under Folia; UUID keys are never sorted, so a hash map is enough.
        this.texts = new ConcurrentHashMap<>();
    }

    public String getIdentifier() {
        return identifier;
    }

    public void updatePlayer(Player player, String text) {
        updatePlayer(player.getUniqueId(), text);
    }

    public void updatePlayer(UUID player, String text) {
        texts.put(player, text);
    }

    public String getCurrentText(UUID uuid) {
        return texts.get(uuid);
    }

    public void updatePeriodically(BiFunction<Player, String, String> updateFunction, long delay) {
        if (updater != null) {
            updater.cancel();
        }
        updater = CivScheduler.runGlobalTimer(() -> {
            Iterator<Entry<UUID, String>> iter = texts.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<UUID, String> entry = iter.next();
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    String newText = updateFunction.apply(player, entry.getValue());
                    if (newText == null) {
                        iter.remove();
                        BottomLineAPI.refreshIndividually(player.getUniqueId());
                        continue;
                    }
                    if (!newText.equals(entry.getValue())) {
                        entry.setValue(newText);
                        BottomLineAPI.refreshIndividually(player.getUniqueId());
                    }
                }
            }
        }, delay, delay);
    }

    public void removePlayer(Player player) {
        removePlayer(player.getUniqueId());
    }

    public void removePlayer(UUID uuid) {
        texts.remove(uuid);
        BottomLineAPI.refreshIndividually(uuid);
    }

    Map<UUID, String> getAll() {
        return texts;
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public int compareTo(BottomLine o) {
        int prio = Integer.compare(priority, o.priority);
        if (prio != 0) {
            return prio;
        }
        return Integer.compare(hashCode(), o.hashCode());
    }

}
