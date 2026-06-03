package vg.civcraft.mc.civmodcore.players.scoreboard.side;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import vg.civcraft.mc.civmodcore.scheduling.CivScheduler;
import vg.civcraft.mc.civmodcore.scheduling.CivTask;

public class CivScoreBoard {

    private String scoreName;
    private Map<UUID, String> currentScoreText;
    private CivTask updater;

    CivScoreBoard(String scoreName) {
        this.scoreName = scoreName;
        // Updater runs on the global region thread while set/hide/purge mutate from player region threads on Folia.
        this.currentScoreText = new ConcurrentHashMap<>();
    }

    public String getName() {
        return scoreName;
    }

    public void updatePeriodically(BiFunction<Player, String, String> updateFunction, long delay) {
        if (updater != null) {
            updater.cancel();
        }
        updater = CivScheduler.runGlobalTimer(() -> {
            Iterator<Entry<UUID, String>> iter = currentScoreText.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<UUID, String> entry = iter.next();
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    String newText = updateFunction.apply(player, entry.getValue());
                    if (newText == null) {
                        hideForPlayer(player);
                        iter.remove();
                        continue;
                    }
                    if (!newText.equals(entry.getValue())) {
                        internalUpdate(player, entry.getValue(), newText);
                        entry.setValue(newText);
                    }
                }
            }
        }, delay, delay);
    }

    public void set(Player p, String newText) {
        if (newText == null) {
            hide(p);
            return;
        }
        String oldText = get(p);
        internalUpdate(p, oldText, newText);
        currentScoreText.put(p.getUniqueId(), newText);
    }

    private static void internalUpdate(Player p, String oldText, String newText) {
        if (oldText != null) {
            p.getScoreboard().resetScores(oldText);
        } else {
            ScoreBoardAPI.adjustScore(p.getUniqueId(), 1);
        }
        if (newText.length() > 40) {
            newText = newText.substring(0, 40);
        }
        Score score = getObjective(p).getScore(newText);
        score.setScore(0);
    }

    public String get(Player p) {
        return currentScoreText.get(p.getUniqueId());
    }

    public void hide(Player p) {
        hideForPlayer(p);
        currentScoreText.remove(p.getUniqueId());
    }

    private void hideForPlayer(Player p) {
        String text = get(p);
        if (text == null) {
            return;
        }
        p.getScoreboard().resetScores(text);
        ScoreBoardAPI.adjustScore(p.getUniqueId(), -1);
    }

    void tearDown() {
        for (Entry<UUID, String> entry : currentScoreText.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) {
                continue;
            }
            p.getScoreboard().resetScores(entry.getValue());
        }
        currentScoreText.clear();
    }

    void purge(Player p) {
        currentScoreText.remove(p.getUniqueId());
    }

    private static Objective getObjective(Player p) {
        Scoreboard scb = p.getScoreboard();
        String title = ScoreBoardAPI.getBoardHeader(p.getUniqueId());
        Objective objective = scb.getObjective(title);
        if (objective == null) {
            scb.getObjectives().forEach(Objective::unregister);
            scb.clearSlot(DisplaySlot.SIDEBAR);
            objective = scb.registerNewObjective(title, title, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        return objective;
    }

}
