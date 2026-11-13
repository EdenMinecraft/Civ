package com.github.longboyy.eve.listeners;

import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.util.InteractiveChatSanitizer;
import com.github.longboyy.eve.util.XaeroWaypoints;
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
        // Show "[inv]" in Discord rather than the raw InteractiveChat marker people could copy and replay.
        String message = InteractiveChatSanitizer.toLabels(event.getMessage());
        // Shared Xaero waypoints stay in-game; a message that was only a waypoint isn't relayed at all.
        message = XaeroWaypoints.strip(message);
        if(message == null) return;
        this.plugin.getRelayManager().publishGroupChat(event.getPlayer().getName(), group, message);
    }
}
