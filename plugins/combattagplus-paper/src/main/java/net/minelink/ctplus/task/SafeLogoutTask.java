package net.minelink.ctplus.task;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minelink.ctplus.CombatTagPlus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.NumberConversions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SafeLogoutTask extends BukkitRunnable {

	private final static Map<UUID, SafeLogoutTask> tasks = new HashMap<>();

	private final CombatTagPlus plugin;

	private final UUID playerId;

	private final Location loc;

	private final long logoutTime;

	private int remainingSeconds = Integer.MAX_VALUE;

	private boolean finished;

	SafeLogoutTask(CombatTagPlus plugin, Player player, long logoutTime) {
		this.plugin = plugin;
		this.playerId = player.getUniqueId();
		this.loc = player.getLocation();
		this.logoutTime = logoutTime;
	}

	private int getRemainingSeconds() {
		long currentTime = System.currentTimeMillis();
		return logoutTime > currentTime ? NumberConversions.ceil((logoutTime - currentTime) / 1000D) : 0;
	}

	@Override
	public void run() {
		// Cancel the task if player is no longer online
		Player player = plugin.getPlayerCache().getPlayer(playerId);
		if (player == null) {
			cancel();
			return;
		}

		// Cancel the task if player has moved
		if (hasMoved(player)) {
			player.showTitle(Title.title(
					Component.text("LOGOUT CANCELLED").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
					Component.text("You moved or entered PvP").color(NamedTextColor.RED),
					Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));

			cancel();
			return;
		}

		// Safely logout the player once timer is up
		int remainingSeconds = getRemainingSeconds();
		if (remainingSeconds <= 0) {
			finished = true;
			plugin.getTagManager().untag(playerId);

			if (!plugin.getSettings().getLogoutSuccessMessage().isEmpty()) {
				player.kickPlayer(plugin.getSettings().getLogoutSuccessMessage());
			}

			cancel();
			return;
		}

		if (remainingSeconds < this.remainingSeconds) {
			String remaining = plugin.getSettings().formatDuration(remainingSeconds);
			NamedTextColor color;

			if (remainingSeconds <= 3) {
				color = NamedTextColor.DARK_RED;
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
			} else if (remainingSeconds <= 6) {
				color = NamedTextColor.RED;
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
			} else {
				color = NamedTextColor.GOLD;
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
			}

			player.showTitle(Title.title(Component.text("SAFE LOGOUT").color(color).decorate(TextDecoration.BOLD),
					Component.text("Logging out in ").color(NamedTextColor.WHITE)
							.append(Component.text(remaining).color(color).decorate(TextDecoration.BOLD))
							.append(Component.text("...").color(NamedTextColor.WHITE)),
					Title.Times.times(Duration.ofMillis(500),
							Duration.ofSeconds(2), 
							Duration.ofMillis(500)
					)));

			this.remainingSeconds = remainingSeconds;
		}
	}

	private boolean hasMoved(Player player) {
		Location l = player.getLocation();
		return loc.getWorld() != l.getWorld() || loc.getBlockX() != l.getBlockX() || loc.getBlockY() != l.getBlockY()
				|| loc.getBlockZ() != l.getBlockZ();
	}

	public static void run(CombatTagPlus plugin, Player player) {
		// Do nothing if player already has a task
		if (hasTask(player))
			return;

		// Calculate logout time
		long logoutTime = System.currentTimeMillis() + (plugin.getSettings().getLogoutWaitTime() * 1000);

		// Run the task every few ticks for accuracy
		SafeLogoutTask task = new SafeLogoutTask(plugin, player, logoutTime);
		task.runTaskTimer(plugin, 0, 5);

		// Cache the task
		tasks.put(player.getUniqueId(), task);
	}

	public static boolean hasTask(Player player) {
		SafeLogoutTask task = tasks.get(player.getUniqueId());
		if (task == null)
			return false;

		BukkitScheduler s = Bukkit.getScheduler();
		if (s.isQueued(task.getTaskId()) || s.isCurrentlyRunning(task.getTaskId())) {
			return true;
		}

		tasks.remove(player.getUniqueId());
		return false;
	}

	public static boolean isFinished(Player player) {
		return hasTask(player) && tasks.get(player.getUniqueId()).finished;
	}

	public static boolean cancel(Player player) {
		// Do nothing if player has no logout task
		if (!hasTask(player))
			return false;

		// Cancel logout task
		Bukkit.getScheduler().cancelTask(tasks.get(player.getUniqueId()).getTaskId());

		// Remove task early to prevent exploits
		tasks.remove(player.getUniqueId());

		return true;
	}

	public static void purgeFinished() {
		Iterator<SafeLogoutTask> iterator = tasks.values().iterator();
		BukkitScheduler s = Bukkit.getScheduler();

		// Loop over each task
		while (iterator.hasNext()) {
			int taskId = iterator.next().getTaskId();

			// Remove entry if task isn't running anymore
			if (!s.isQueued(taskId) && !s.isCurrentlyRunning(taskId)) {
				iterator.remove();
			}
		}
	}

}
