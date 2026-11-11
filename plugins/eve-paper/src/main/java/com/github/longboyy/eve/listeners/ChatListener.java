package com.github.longboyy.eve.listeners;

import com.github.longboyy.eve.EvePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import vg.civcraft.mc.civchat2.event.GroupChatEvent;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * Mirrors in-game group chat into the group's Discord chat relays.
 *
 * CivChat2 only fires {@link GroupChatEvent} on the server the sender is on (other servers receive the message
 * via plugin messaging without an event), so each message is relayed exactly once even on a multi-server network.
 */
public class ChatListener implements Listener {
    private final EvePlugin plugin;

    public ChatListener(EvePlugin plugin){
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGroupChat(GroupChatEvent event){
        Group group = GroupManager.getGroup(event.getGroup());
        if(group == null) return;
        this.plugin.getRelayManager().publishGroupChat(event.getPlayer().getName(), group, event.getMessage());
    }
}
