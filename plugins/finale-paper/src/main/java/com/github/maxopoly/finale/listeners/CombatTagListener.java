package com.github.maxopoly.finale.listeners;

import com.github.maxopoly.finale.Finale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minelink.ctplus.event.CombatLogEvent;
import net.minelink.ctplus.event.PlayerCombatTagEvent;
import net.minelink.ctplus.event.SafeLogoutEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class CombatTagListener implements Listener {

    private static final String UNSAFE_LOGOUT_MESSAGE = "You left the game without using /logout, make sure to run /logout to be safe!";

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
        if(!plugin.getSettingsManager().getUnsafeLogout(event.getPlayer().getUniqueId())) return;
        event.getPlayer().sendMessage(Component.text(UNSAFE_LOGOUT_MESSAGE).color(TextColor.color(255,0,0)));
    }

}
