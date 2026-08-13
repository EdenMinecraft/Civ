package com.github.maxopoly.finale.listeners;

import java.time.Duration;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.github.maxopoly.finale.Finale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minelink.ctplus.event.CombatLogEvent;
import net.minelink.ctplus.event.SafeLogoutEvent;

public class CombatTagListener implements Listener {

    private final Finale plugin;

    public CombatTagListener(Finale plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCombatTag(CombatLogEvent event){
        plugin.getSettingsManager().setUnsafeLogout(event.getPlayer().getUniqueId(), event.getReason() == CombatLogEvent.Reason.UNSAFE_LOGOUT);
    }

    @EventHandler
    public void onSafeLogout(SafeLogoutEvent event){
        plugin.getSettingsManager().setUnsafeLogout(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        if(!plugin.getSettingsManager().getUnsafeLogout(event.getPlayer().getUniqueId())) return;
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        player.showTitle(Title.title(
				Component.text("UNSAFE LOGOUT").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
				Component.text("You left the game without using /logout").color(NamedTextColor.RED),
				Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
    }

}
