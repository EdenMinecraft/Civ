package vg.civcraft.mc.civchat2.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import vg.civcraft.mc.civchat2.ChatStrings;
import vg.civcraft.mc.civchat2.CivChat2;
import vg.civcraft.mc.civchat2.CivChat2Manager;
import vg.civcraft.mc.civchat2.database.CivChatDAO;
import vg.civcraft.mc.civchat2.event.GlobalChatEvent;
import vg.civcraft.mc.civchat2.utility.CivChat2SettingsManager;
import vg.civcraft.mc.civmodcore.ui.CivText;
import vg.civcraft.mc.civmodcore.ui.CivUI;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

/*
 * @author jjj5311
 *
 */
public class CivChat2Listener implements Listener {

    private CivChat2Manager chatman;
    private CivChatDAO db;
    private CivChat2SettingsManager settings;
    private Set<UUID> localWarn;

    public CivChat2Listener(CivChat2Manager instance) {
        chatman = instance;
        db = CivChat2.getInstance().getDatabaseManager();
        settings = CivChat2.getInstance().getCivChat2SettingsManager();
        localWarn = new HashSet<>();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent) {
        playerDeathEvent.setDeathMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        playerQuitEvent.quitMessage(null);
        Player left = playerQuitEvent.getPlayer();
        if (left.hasPermission("civchat2.leavejoinimmune")) {
            return;
        }
        Component line = presenceLine("- ", CivText.ERROR, left, " left the server");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(left)) {
                continue;
            }
            if (settings.getShowLeaves(p.getUniqueId()) && !db.isIgnoringPlayer(p.getUniqueId(), left.getUniqueId())) {
                p.sendMessage(chatman.adapt(p, line));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        // Replaced by the line below; loginAnnounce would otherwise show both.
        playerJoinEvent.joinMessage(null);
        Player joined = playerJoinEvent.getPlayer();
        if (!joined.hasPermission("civchat2.leavejoinimmune")) {
            Component line = presenceLine("+ ", CivText.SUCCESS, joined, " joined the server");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (settings.getShowJoins(p.getUniqueId()) && !db.isIgnoringPlayer(p.getUniqueId(), joined.getUniqueId())) {
                    p.sendMessage(chatman.adapt(p, line));
                }
            }
        }

        UUID player = chatman.getChannel(playerJoinEvent.getPlayer());
        if (player != null && Bukkit.getPlayer(player) == null) {
            chatman.removeChannel(playerJoinEvent.getPlayer());
        }

        // Set current chat group in scoreboard
        chatman.getScoreboardHUD().updateScoreboardHUD(playerJoinEvent.getPlayer());
        chatman.getScoreboardHUD().updateAFKScoreboardHUD(playerJoinEvent.getPlayer());

        if (CivChat2.getInstance().getPluginConfig().getChatRangeWarn() && !playerJoinEvent.getPlayer().hasPlayedBefore()) {
            localWarn.add(playerJoinEvent.getPlayer().getUniqueId());
        }
        String globalChat = CivChat2.getInstance().getPluginConfig().getGlobalChatGroupName();
        if (globalChat != null && !playerJoinEvent.getPlayer().hasPlayedBefore()) {
            Group group = GroupManager.getGroup(globalChat);
            if (group != null && !group.isCurrentMember(playerJoinEvent.getPlayer().getUniqueId())) {
                group.addMember(playerJoinEvent.getPlayer().getUniqueId(), PlayerType.MEMBERS);
                CivUI.success(playerJoinEvent.getPlayer(), "You autojoined global chat - talk in it with ",
                    CivText.value("/g ! Hello"));
            }
        }
    }

    private Component presenceLine(String marker, TextColor markerColour, Player player, String verb) {
        return Component.text()
            .append(Component.text(marker, markerColour))
            .append(chatman.identityTag(player))
            .append(Component.text(verb, CivText.INFO))
            .build();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent playerKickEvent) {
        playerKickEvent.setLeaveMessage("You have been kicked");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlobalChatEvent(GlobalChatEvent localchat) {

        if (localWarn.contains(localchat.getPlayer().getUniqueId())) {
            CivUI.warn(localchat.getPlayer(), "Only players within ",
                CivText.value(String.valueOf(CivChat2.getInstance().getPluginConfig().getChatRange())),
                " blocks can see your messages - join a group to chat with players farther away");
            localWarn.remove(localchat.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChatEvent(final AsyncChatEvent asyncPlayerChatEvent) {

        asyncPlayerChatEvent.setCancelled(true);
        // This needs to be done sync to avoid a rare deadlock due to minecraft
        // internals
        new BukkitRunnable() {

            @Override
            public void run() {

                Component chatMessage = asyncPlayerChatEvent.message();
                Player sender = asyncPlayerChatEvent.getPlayer();
                UUID chatChannel = chatman.getChannel(sender);
                Group groupChat = chatman.getGroupChatting(sender);


                if (chatChannel != null) {
                    Player receiver = Bukkit.getPlayer(chatChannel);
                    if (receiver != null) {
                        chatman.sendPrivateMsg(sender, receiver, chatMessage);
                        return;
                    } else {
                        chatman.removeChannel(sender);
                        CivUI.info(sender, "The player you were chatting with went offline - you're back in regular chat");
                        return;
                    }
                }
                if (groupChat != null) {
                    // Player is group chatting
                    if (NameLayerAPI.getGroupManager().hasAccess(groupChat, sender.getUniqueId(),
                        PermissionType.getPermission("WRITE_CHAT"))) {
                        chatman.sendGroupMsg(sender, groupChat, chatMessage);
                        return;
                        // Player lost perm to write in the chat
                    } else {
                        chatman.removeGroupChat(sender);
                        CivUI.error(sender, "You were removed from group chat - you lost access or were removed from the group");
                    }
                }
                Set<Player> playerViewers = new HashSet<>();
                for (Audience viewer : asyncPlayerChatEvent.viewers()) {
                    if (viewer instanceof Player playerViewer) {
                        playerViewers.add(playerViewer);
                    }
                }
                chatman.broadcastMessage(sender, chatMessage, ChatStrings.localChatFormat, playerViewers);
            }
        }.runTask(CivChat2.getInstance());
    }
}
