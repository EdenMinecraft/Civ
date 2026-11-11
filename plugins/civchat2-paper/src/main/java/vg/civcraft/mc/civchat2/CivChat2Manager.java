package vg.civcraft.mc.civchat2;

import com.google.common.base.Preconditions;
import io.papermc.paper.chat.ChatRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import vg.civcraft.mc.civchat2.broadcaster.ServerBroadcaster;
import vg.civcraft.mc.civchat2.database.CivChatDAO;
import vg.civcraft.mc.civchat2.event.GlobalChatEvent;
import vg.civcraft.mc.civchat2.event.GroupChatEvent;
import vg.civcraft.mc.civchat2.event.PrivateMessageEvent;
import vg.civcraft.mc.civchat2.prefix.StarManager;
import vg.civcraft.mc.civchat2.utility.CivChat2Config;
import vg.civcraft.mc.civchat2.utility.CivChat2FileLogger;
import vg.civcraft.mc.civchat2.utility.ScoreboardHUD;
import vg.civcraft.mc.civmodcore.players.settings.impl.LongSetting;
import vg.civcraft.mc.civmodcore.ui.Bedrock;
import vg.civcraft.mc.civmodcore.ui.CivText;
import vg.civcraft.mc.civmodcore.ui.CivUI;
import vg.civcraft.mc.civmodcore.utilities.TextUtil;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;


public class CivChat2Manager {

    private CivChat2Config config;

    private CivChat2FileLogger chatLog;

    private CivChat2 instance;

    private CivChatDAO DBM;

    // chatChannels in hashmap with (Player 1 name, player 2 name)
    private HashMap<UUID, UUID> chatChannels;

    // groupChatChannels have (Player, Group)
    private final HashMap<UUID, Group> groupChatChannels;

    // replyList has (playerName, whotoreplyto)
    private final HashMap<UUID, UUID> replyList;

    private final HashMap<UUID, Component> afkPlayers;

    private ScoreboardHUD scoreboardHUD;

    protected static final GroupManager GM = NameLayerAPI.getGroupManager();

    private static final Sound MESSAGE_SOUND = Sound.sound(Key.key("block.note_block.chime"), Sound.Source.UI, 0.3F, 1.5F);

    private String defaultColor;

    private static Map<UUID, Component> customNames = new HashMap<>();

    private final ServerBroadcaster broadcaster;

    private LongSetting banSetting;

    private String filterRelayGroup;

    private int muteTimeSeconds;

    private Group modsGroup;

    private final StarManager starManager;

    // Load banned words once when CivChat2Manager is created
    private final Set<String> bannedWords;
    private final HashMap<String, Pattern> bannedWordPatterns;

    public CivChat2Manager(CivChat2 pluginInstance, ServerBroadcaster broadcaster, StarManager starManager) {


        instance = pluginInstance;
        this.broadcaster = broadcaster;
        this.starManager = starManager;
        config = instance.getPluginConfig();
        chatLog = instance.getCivChat2FileLogger();
        DBM = instance.getDatabaseManager();
        defaultColor = config.getDefaultColor();
        chatChannels = new HashMap<>();
        groupChatChannels = new HashMap<>();
        replyList = new HashMap<>();
        afkPlayers = new HashMap<>();
        scoreboardHUD = new ScoreboardHUD();
        bannedWords = loadBannedWords();
        bannedWordPatterns = loadBannedWordPatterns();
        muteTimeSeconds = config.getMuteTimeSeconds();
        banSetting = instance.getCivChat2SettingsManager().getGlobalChatMuteSetting();
        filterRelayGroup = config.getFilterRelayGroup();
    }





    /**
     * Gets the channel for player to player chat
     *
     * @param player Player name of the channel
     * @return Returns a String of channel name, null if doesn't exist
     */
    public UUID getChannel(Player player) {

        Preconditions.checkNotNull(player, "player");

        return chatChannels.get(player.getUniqueId());
    }

    /**
     * Removes the channel from the channel storage
     *
     * @param player Player Name of the channel
     */
    public void removeChannel(Player player) {

        Preconditions.checkNotNull(player, "player");

        chatChannels.remove(player.getUniqueId());
        scoreboardHUD.updateScoreboardHUD(player);
    }

    /**
     * Adds a channel for player to player chat, if player1 is currently in a
     * chatChannel this will overwrite it
     *
     * @param player1 Sender's name
     * @param player2 Receiver's name
     */
    public void addChatChannel(Player player1, Player player2) {

        Preconditions.checkNotNull(player1, "player1");
        Preconditions.checkNotNull(player2, "player2");

        // A player is locked into at most one chat target - entering a private chat leaves any group chat.
        groupChatChannels.remove(player1.getUniqueId());
        chatChannels.put(player1.getUniqueId(), player2.getUniqueId());
        scoreboardHUD.updateScoreboardHUD(player1);
    }

    /**
     * Method to Send private message between to players
     *
     * @param sender      Player sending the message
     * @param receiver    Player Receiving the message
     * @param chatMessage Message to send from sender to receive
     */
    public void sendPrivateMsg(Player sender, Player receiver, Component chatMessage) {

        PrivateMessageEvent event = new PrivateMessageEvent(sender, receiver, chatMessage);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        Component senderName = identityTag(sender);
        Component receiverName = identityTag(receiver);

        if (DBM.isIgnoringPlayer(receiver.getUniqueId(), sender.getUniqueId())) {
            CivUI.error(sender, "That player is ignoring you");
            return;
        } else if (DBM.isIgnoringPlayer(sender.getUniqueId(), receiver.getUniqueId())) {
            CivUI.error(sender, "You need to unignore ", receiverName);
            return;
        }
        chatLog.logPrivateMessage(sender, PlainTextComponentSerializer.plainText().serialize(chatMessage), receiver.getName());
        replyList.put(receiver.getUniqueId(), sender.getUniqueId());
        replyList.put(sender.getUniqueId(), receiver.getUniqueId());

        sender.sendMessage(adapt(sender, privateLine(sender, CivText.info("You"), receiverName, chatMessage)));
        receiver.sendMessage(adapt(receiver, privateLine(receiver, senderName, CivText.info("you"), chatMessage)));
        if (instance.getCivChat2SettingsManager().getMessageSound(receiver.getUniqueId())) {
            receiver.playSound(MESSAGE_SOUND);
        }

        if (isPlayerAfk(receiver)) {
            Component afkMessage = getPlayerAfkMessage(receiver);
            if (afkMessage.equals(ChatStrings.chatPlayerAfk)) {
                CivUI.info(sender, CivText.subject(receiver.getName()), " is AFK");
            } else {
                CivUI.info(sender, CivText.subject(receiver.getName()), " is AFK: ", afkMessage);
            }
        }
    }

    private Component privateLine(Player viewer, Component from, Component to, Component message) {
        TextColor messageColor = instance.getCivChat2SettingsManager().getPrivateMessageColor(viewer.getUniqueId());
        Component arrow = Component.text(" → ", CivText.MUTED);

        return Component.text()
            .append(from)
            .append(arrow)
            .append(to)
            .append(Component.text(" » ", CivText.MUTED))
            .append(Component.empty().color(messageColor).append(message))
            .build();
    }

    /**
     * Method to broadcast a message in global chat
     *
     * @param sender      Player who sent the message
     * @param chatMessage Message to send
     * @param recipients  Players in range to receive the message
     */
    public void broadcastMessage(Player sender, Component chatMessage, ChatRenderer messageFormat, Set<Player> recipients) {



        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(chatMessage, "chatMessage");
        Preconditions.checkNotNull(recipients, "recipients");

        GlobalChatEvent event = new GlobalChatEvent(sender, chatMessage);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }


        long mutedUntil = instance.getCivChat2SettingsManager().getGlobalChatMuteSetting().getValue(sender);
        Group targetChatGroup = groupChatChannels.get(sender.getUniqueId());
        if (mutedUntil > System.currentTimeMillis()) {
            if (targetChatGroup == null || targetChatGroup.getName().equals(instance.getPluginConfig().getGlobalChatGroupName())) {
                CivUI.error(sender, "You're muted from global and local chat for ",
                    CivText.value(TextUtil.formatDuration(mutedUntil - System.currentTimeMillis())));
                return;
            }
        }
        // Chat filter check - block message if it contains banned words
        if (containsBannedWord(PlainTextComponentSerializer.plainText().serialize(chatMessage)) && !sender.hasPermission("civchat2.globalmute")) {
            flagMessage(sender, chatMessage);
            return;
        }

        int range = config.getChatRange();
        int height = config.getYInc();
        Location location = sender.getLocation();
        int y = location.getBlockY();
        double scale = (config.getYScale()) / 1000;

        // Do height check
        // Player is above chat increase range
        if (range > 0 && y > height) {
            int above = y - height;
            int newRange = (int) (range + (range * (scale * above)));
            range = newRange;
        }

        Set<String> receivers = new HashSet<>();
        // Loop through players and send to those that are close enough
        for (Player receiver : recipients) {
            if (!DBM.isIgnoringPlayer(receiver.getUniqueId(), sender.getUniqueId())) {
                if (range <= 0 || receiver.getWorld().equals(sender.getWorld())) {
                    double receiverDistance = range <= 0 ? 0 : location.distance(receiver.getLocation());
                    if (receiverDistance <= range) {
                        TextColor newColor;
                        if (config.useDynamicRangeColoring()) {
                            int comp = (int) (255 - (128.0 * receiverDistance) / range);
                            newColor = TextColor.color(comp, comp, comp);
                        } else {
                            newColor = NamedTextColor.NAMES.valueOrThrow(config.getColorAtDistance(receiverDistance).toLowerCase());
                        }

                        receiver.sendMessage(adapt(receiver, messageFormat.render(sender, identityTag(sender), Component.empty().color(newColor).append(chatMessage), receiver)));
                        receivers.add(receiver.getName());
                    }
                }
            }
        }
        receivers.remove(sender.getName());
        chatLog.logGlobalMessage(sender, PlainTextComponentSerializer.plainText().serialize(chatMessage), receivers);
    }

    /**
     * Rank stars, the player's face, then the name - no brackets. Used by every chat surface and by join/quit lines.
     * A name without a chosen colour is GRAY, so it never inherits the surrounding line's colour.
     * Send lines carrying it through {@link #adapt} so viewers who turned heads off don't see the face.
     */
    public Component identityTag(Player player) {
        Component name = customNames.containsKey(player.getUniqueId())
            ? customNames.get(player.getUniqueId())
            : player.displayName();
        return Component.text()
            .color(NamedTextColor.GRAY)
            .append(starManager.getPrefix(player))
            .append(CivText.head(player))
            .append(Component.space())
            .append(name)
            .build()
            .hoverEvent(starManager.hover(player));
    }


    /**
     * The line as this viewer should see it: without player heads if they turned them off, then adapted for Bedrock.
     */
    public Component adapt(Player viewer, Component message) {
        Component line = instance.getCivChat2SettingsManager().showPlayerHeads(viewer.getUniqueId())
            ? message
            : stripHeads(message);
        return Bedrock.adapt(viewer, line);
    }

    /** Removes every player head, and the space that followed it, leaving other sprites alone. */
    private static Component stripHeads(Component component) {
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return component;
        }
        List<Component> result = new ArrayList<>(children.size());
        boolean changed = false;
        boolean dropNextSpace = false;
        for (Component child : children) {
            if (dropNextSpace) {
                dropNextSpace = false;
                if (child instanceof TextComponent text && text.content().equals(" ") && text.children().isEmpty()) {
                    changed = true;
                    continue;
                }
            }
            if (child instanceof ObjectComponent object && object.contents() instanceof PlayerHeadObjectContents) {
                changed = true;
                dropNextSpace = true;
                continue;
            }
            Component stripped = stripHeads(child);
            changed |= stripped != child;
            result.add(stripped);
        }
        return changed ? component.children(result) : component;
    }

    /**
     * Flags a message as inappropriate and mutes the sender
     *
     * @param sender     The player who sent the message
     * @param chatMessage The message content
     */
    public void flagMessage(Player sender, Component chatMessage) {
        //Flag inappropriate message, mute sender for X seconds (defined in config)
        CivUI.error(sender, "Your message was flagged for inappropriate content");
        if (muteTimeSeconds > 0) {
            banSetting.setValue(sender, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis((long)muteTimeSeconds)); // mute player automatically
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(chatMessage);
        Group modsGroup = GroupManager.getGroup(filterRelayGroup);
        if (modsGroup == null) {
            instance.getLogger().warning(sender.getName() + " sent a filtered message: " + plain + " No filter relay group set, if this is unintentional please set filterRelayGroup in the config.yml");
            return;
        }


        // Log the filtered message to console and mods
        Component senderName = identityTag(sender);

        Component filtered = Component.text("[Filtered]: " + plain, NamedTextColor.RED);
        Set<String> modPlayers = doSendGroupMsg(sender.getUniqueId(), senderName, modsGroup, filtered);

        broadcaster.broadcastGroup(sender.getUniqueId(), sender.getDisplayName(), senderName, modsGroup.getName(), filtered);

        chatLog.logGroupMessage(sender, plain, modsGroup.getName(), modPlayers);
    }

    /**
     * Gets whether a player is AFK
     *
     * @param player The player to check
     * @return true if the player is AFK
     */
    public boolean isPlayerAfk(Player player) {
        Preconditions.checkNotNull(player, "player");

        return afkPlayers.get(player.getUniqueId()) != null;
    }

    /**
     * Sets the AFK status of a player
     *
     * @param player The player to change
     * @return The player AFK status
     */
    public boolean setPlayerAfk(Player player, boolean afkStatus, Component afkMsg) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(afkMsg, "afk message");

        if (afkStatus) {
            afkPlayers.put(player.getUniqueId(), afkMsg);
            // afkPlayers.add(player.getUniqueId());
        } else {
            afkPlayers.remove(player.getUniqueId());
        }

        scoreboardHUD.updateAFKScoreboardHUD(player);
        return afkStatus;
    }

    /**
     * Toggles the AFK status of a player
     *
     * @param player Player to toggle state for
     * @return Whether afk is turned on afterwards
     */
    public boolean togglePlayerAfk(Player player, Component afkMsg) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(afkMsg, "afk message");
        if (isPlayerAfk(player)) {
            afkPlayers.remove(player.getUniqueId());

            scoreboardHUD.updateAFKScoreboardHUD(player);
            return false;
        }
        afkPlayers.put(player.getUniqueId(), afkMsg);

        scoreboardHUD.updateAFKScoreboardHUD(player);
        return true;
    }

    public Component getPlayerAfkMessage(Player player) {
        Preconditions.checkNotNull(player, "player");
        return afkPlayers.get(player.getUniqueId());
    }

    /**
     * Gets the player to send reply to
     *
     * @param sender the person sending reply command
     * @return the UUID of the person to reply to, null if none
     */
    public UUID getPlayerReply(Player sender) {
        Preconditions.checkNotNull(sender, "sender");
        return replyList.get(sender.getUniqueId());
    }

    /**
     * Add a player to the replyList
     *
     * @param player      The player using the reply command.
     * @param replyPlayer The the player that will receive the reply
     */
    public void addPlayerReply(Player player, Player replyPlayer) {

        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(replyPlayer, "replyPlayer");

        replyList.put(player.getUniqueId(), replyPlayer.getUniqueId());
    }

    /**
     * Method to add a group chat channel
     *
     * @param player Player sending the message
     * @param group  Group sending the message to
     */
    public void addGroupChat(Player player, Group group) {

        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(group, "group");

        // A player is locked into at most one chat target - entering a group chat leaves any private chat.
        chatChannels.remove(player.getUniqueId());
        groupChatChannels.put(player.getUniqueId(), group);
        scoreboardHUD.updateScoreboardHUD(player);
    }

    private Set<String> loadBannedWords() {

        Set<String> words = new HashSet<>();
        try {
            File file = new File(instance.getDataFolder(), "banned-words.txt");
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    String cleanWord = line.strip().toLowerCase();
                    if (!cleanWord.isEmpty()) {
                        words.add(cleanWord);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return words;
    }

    private HashMap<String, Pattern> loadBannedWordPatterns(){
        HashMap<String, Pattern> patterns = new HashMap<>();
        for (String word : bannedWords) {
            String pattern = "\\b" + Pattern.quote(word) + "\\b";
            patterns.put(word, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    public boolean containsBannedWord(String message) {
        for (Pattern pattern : bannedWordPatterns.values()) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }


    /**
     * Method to send a message to a group
     *
     * @param sender  sender sending the message
     * @param group   Group to send the message too
     * @param message Message to send to the group
     */
    public void sendGroupMsg(Player sender, Group group, Component message) {

        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(group, "group");

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);

        if (group.getName().equals(config.getGlobalChatGroupName())) {
            long mutedUntil = instance.getCivChat2SettingsManager().getGlobalChatMuteSetting().getValue(sender);
            if (mutedUntil > System.currentTimeMillis()) {
                CivUI.error(sender, "You're muted from global and local chat for ",
                    CivText.value(TextUtil.formatDuration(mutedUntil - System.currentTimeMillis())));
                return;
            }
            if (containsBannedWord(plainMessage) && !sender.hasPermission("civchat2.globalmute")) {
                flagMessage(sender, message);
                return;
            }
        }

        GroupChatEvent event = new GroupChatEvent(sender, group.getName(), plainMessage);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        Component senderName = identityTag(sender);
        Set<String> players = doSendGroupMsg(sender.getUniqueId(), senderName, group, message);
        chatLog.logGroupMessage(sender, plainMessage, group.getName(), players);
        broadcaster.broadcastGroup(sender.getUniqueId(), sender.getName(), senderName, group.getName(), message);
    }

    public void sendRemoteGroupMsg(UUID senderId, String senderName, Component senderDisplayName, String groupName, Component message) {
        Group group = GroupManager.getGroup(groupName);
        if (group == null) {
            return;
        }
        Set<String> players = doSendGroupMsg(senderId, senderDisplayName, group, message);
        chatLog.logRemoteGroupMessage(senderName, PlainTextComponentSerializer.plainText().serialize(message), group.getName(), players);
    }

    private Set<String> doSendGroupMsg(UUID senderId, Component senderName, Group group, Component message) {
        List<Player> members = new ArrayList<>();
        List<UUID> membersUUID = group.getAllMembers();
        for (UUID uuid : membersUUID) {
            // Only add online players to members
            Player toAdd = Bukkit.getPlayer(uuid);
            if (toAdd != null && toAdd.isOnline() && NameLayerAPI.getGroupManager().hasAccess(group, toAdd.getUniqueId(),
                PermissionType.getPermission("READ_CHAT"))) {
                members.add(toAdd);
            }
        }

        for (Player receiver : members) {
            if (DBM.isIgnoringGroup(receiver.getUniqueId(), group.getName())) {
                continue;
            }
            if (DBM.isIgnoringPlayer(receiver.getUniqueId(), senderId)) {
                continue;
            }

            TextColor color =
                CivChat2.getInstance().getCivChat2SettingsManager()
                    .showGroupColors(receiver.getUniqueId())
                    ? group.getGroupColor() : NamedTextColor.GRAY;

            // A neutral, uncoloured root - senderName must not inherit the group's colour as an ancestor.
            Component separator = Component.text(" » ", CivText.MUTED);
            Component compMessage = Component.text()
                .append(Component.text(group.getName(), color))
                .append(separator)
                .append(senderName)
                .append(separator)
                .append(Component.empty().color(NamedTextColor.WHITE).append(message))
                .build();

            receiver.sendMessage(adapt(receiver, compMessage));
        }

        Set<String> players = new HashSet<>();
        for (Player player : members) {
            if (!senderId.equals(player.getUniqueId())) {
                players.add(NameLayerAPI.getCurrentName(player.getUniqueId()));
            }
        }
        return players;
    }

    /**
     * Method to remove player from a group chat
     *
     * @param player The player to remove from chat
     */
    public void removeGroupChat(Player player) {

        Preconditions.checkNotNull(player, "player");

        groupChatChannels.remove(player.getUniqueId());
        scoreboardHUD.updateScoreboardHUD(player);
    }

    /**
     * Method to get the group player is currently chatting in
     *
     * @param player Players name
     * @return Group they are currently chatting in
     */
    public Group getGroupChatting(Player player) {

        Preconditions.checkNotNull(player, "player");

        return groupChatChannels.get(player.getUniqueId());
    }

    public void setCustomName(UUID player, Component name) {
        customNames.put(player, name);
    }

    public void removeCustomName(UUID player) {
        customNames.remove(player);
    }

    public ScoreboardHUD getScoreboardHUD() {
        return scoreboardHUD;
    }
}
