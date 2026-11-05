package net.minelink.ctplus.task;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.util.Messages;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.NumberConversions;
import vg.civcraft.mc.civmodcore.ui.CivText;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SafeLogoutTask extends BukkitRunnable {

	private final static Map<UUID, SafeLogoutTask> tasks = new HashMap<>();

	/** Players kicked by a completed /logout. The quit event can arrive after the task is gone, so it isn't tracked there. */
	private final static Set<UUID> loggedOutSafely = new HashSet<>();

	private final CombatTagPlus plugin;

	private final UUID playerId;

	private final Location loc;

	private final long logoutTime;

	private int remainingSeconds = Integer.MAX_VALUE;

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
			showCancelled(player, "You moved");

			cancel();
			return;
		}

		// Safely logout the player once timer is up
		int remainingSeconds = getRemainingSeconds();
		if (remainingSeconds <= 0) {
			loggedOutSafely.add(playerId);
			plugin.getTagManager().untag(playerId);

			String message = plugin.getSettings().getLogoutSuccessMessage();
			if (!Messages.isEmpty(message)) {
				player.kick(Messages.body(CivText.SUCCESS, message), PlayerKickEvent.Cause.PLUGIN);
			}

			cancel();
			return;
		}

		if (remainingSeconds < this.remainingSeconds) {
			String remaining = plugin.getSettings().formatDuration(remainingSeconds);
			// Escalates as the timer runs down
			TextColor color;
			Sound sound;
			float pitch;
			if (remainingSeconds <= 3) {
				color = CivText.ERROR;
				sound = Sound.BLOCK_NOTE_BLOCK_PLING;
				pitch = 1.6f;
			} else if (remainingSeconds <= 6) {
				color = CivText.WARN;
				sound = Sound.BLOCK_NOTE_BLOCK_PLING;
				pitch = 1.2f;
			} else {
				color = CivText.HEADING;
				sound = Sound.BLOCK_NOTE_BLOCK_BASS;
				pitch = 0.8f;
			}
			if (plugin.getSettings().playLogoutSound(playerId)) {
				player.playSound(player.getLocation(), sound, 1.0f, pitch);
			}

			player.showTitle(Title.title(
					Component.text("Safe logout", color, TextDecoration.BOLD),
					Component.text("Logging out in ", CivText.INFO)
							.append(Component.text(remaining, color, TextDecoration.BOLD)),
					Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));

			this.remainingSeconds = remainingSeconds;
		}
	}

	/** Shared by every way a safe logout can be cancelled. */
	public static void showCancelled(Player player, String reason) {
		player.showTitle(Title.title(
				Component.text("Logout cancelled", CivText.ERROR, TextDecoration.BOLD),
				Component.text(reason, CivText.INFO),
				Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
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

	/** True once per completed /logout - call it from the quit that logout caused. */
	public static boolean consumeFinished(Player player) {
		return loggedOutSafely.remove(player.getUniqueId());
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
