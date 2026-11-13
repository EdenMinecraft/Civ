package com.github.longboyy.eve.discord.listeners;

import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.model.Relay;
import com.github.longboyy.eve.model.RelayType;
import com.github.longboyy.eve.util.InteractiveChatSanitizer;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.emoji.EmojiParser;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import vg.civcraft.mc.civchat2.CivChat2;
import vg.civcraft.mc.civchat2.CivChat2Manager;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

/**
 * Carries messages typed in a chat relay's Discord channel into the relayed group's in-game chat.
 *
 * DiscordSRV fires {@link DiscordGuildMessageReceivedEvent} for every guild channel (not just its own linked
 * channels), excluding the bot's own messages, so relayed output never loops back in.
 */
public class DiscordChatListener extends DiscordListener {

    private static final int MAX_MESSAGE_LENGTH = 256;

    private static final String LINK_NOTICE = "Your message wasn't sent to the game because your Discord account "
        + "isn't linked to a Minecraft account. Run `/discord link` in-game and follow the instructions to link it.";
    /** How often one user can be told to link, so spamming the channel doesn't spam them back. */
    private static final long LINK_NOTICE_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    /** How long the in-channel fallback notice stays up when the user's DMs are closed. */
    private static final long CHANNEL_NOTICE_LIFETIME_SECONDS = 15;

    private static final String STATUS_DOT = "● ";

    private final EvePlugin plugin;
    private final Map<String, Long> lastLinkNotice = new ConcurrentHashMap<>();

    public DiscordChatListener(EvePlugin plugin){
        this.plugin = plugin;
    }

    @Subscribe
    public void onDiscordMessage(DiscordGuildMessageReceivedEvent event) {
        if(event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;

        String channelId = event.getChannel().getId();
        Set<Relay> relays = this.plugin.getRelayManager().getChannelRelays(channelId, RelayType.CHAT);
        if(relays.isEmpty()) return;

        Message message = event.getMessage();

        // Unlinked Discord users have no in-game identity, so their messages can't be relayed. Remove them so
        // the channel only shows what actually reached the game.
        UUID playerUUID = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getAuthor().getId());
        if(playerUUID == null){
            deleteMessage(message, "author has no linked Minecraft account");
            notifyUnlinked(event.getAuthor(), message);
            return;
        }

        String content = buildContent(message);
        if(content.isEmpty()) return;

        boolean fromMainGuild = isMainGuild(event.getGuild());

        // NameLayer and CivChat2 aren't thread-safe; JDA delivers this on its own thread.
        Bukkit.getScheduler().runTask(this.plugin, () -> relays.forEach(relay -> deliver(relay, message, playerUUID, content, fromMainGuild)));
    }

    private void deliver(Relay relay, Message message, UUID playerUUID, String content, boolean fromMainGuild){
        Group group = GroupManager.getGroup(relay.getGroupId());
        if(group == null) return;

        CivChat2 civChat = CivChat2.getInstance();
        CivChat2Manager chatManager = civChat.getCivChat2Manager();

        if(group.getName().equals(civChat.getPluginConfig().getGlobalChatGroupName())){
            // Global chat is only writable from the server's own Discord, not from any guild that relays it.
            if(!fromMainGuild) return;

            // Apply the same mute and banned-word rules CivChat2 enforces on in-game global chat.
            long mutedUntil = civChat.getCivChat2SettingsManager().getGlobalChatMuteSetting().getValue(playerUUID);
            if(mutedUntil > System.currentTimeMillis()){
                deleteMessage(message, "author is muted from global chat");
                return;
            }
            if(chatManager.containsBannedWord(content)){
                deleteMessage(message, "message contains a banned word");
                return;
            }
        }

        if(!NameLayerAPI.getGroupManager().hasAccess(group, playerUUID, PermissionType.getPermission("WRITE_CHAT"))) {
            deleteMessage(message, "author doesn't have permission to write chat for that group");
            return;
        }

        String playerName = NameLayerAPI.getCurrentName(playerUUID);
        if(playerName == null) return;

        // Green when the sender is also online in-game on this server, light blue when they're only on Discord.
        boolean onlineInGame = Bukkit.getPlayer(playerUUID) != null;
        Component statusDot = Component.text(STATUS_DOT, onlineInGame ? NamedTextColor.GREEN : NamedTextColor.AQUA)
            .hoverEvent(HoverEvent.showText(Component.text(onlineInGame
                ? "Online in-game, chatting from Discord"
                : "Chatting from Discord")));
        Component displayName = Component.text()
            .append(statusDot)
            .append(Component.text(playerName, NamedTextColor.GRAY))
            .build();

        // Delivers to players on this server only, without firing GroupChatEvent (so it isn't echoed back to
        // Discord). If several servers run Eve on the same bot, each one receives the Discord message itself,
        // so broadcasting it cross-server here would duplicate it.
        chatManager.sendRemoteGroupMsg(playerUUID, playerName, displayName, group.getName(), Component.text(content));
    }

    private boolean isMainGuild(Guild guild){
        Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
        return mainGuild != null && mainGuild.getId().equals(guild.getId());
    }

    /**
     * Tells an unlinked user why their message vanished: by DM, or briefly in the channel if their DMs are closed.
     */
    private void notifyUnlinked(User user, Message message){
        long now = System.currentTimeMillis();
        Long last = lastLinkNotice.get(user.getId());
        if(last != null && now - last < LINK_NOTICE_COOLDOWN_MS) return;
        lastLinkNotice.put(user.getId(), now);

        user.openPrivateChannel()
            .flatMap(channel -> channel.sendMessage(LINK_NOTICE))
            .queue(null, dmError -> message.getChannel()
                .sendMessage(user.getAsMention() + " " + LINK_NOTICE)
                .queue(notice -> notice.delete().queueAfter(CHANNEL_NOTICE_LIFETIME_SECONDS, TimeUnit.SECONDS, null, ignored -> {}),
                    channelError -> this.plugin.getLogger().warning("Couldn't tell " + user.getName()
                        + " to link their account: " + channelError.getMessage())));
    }

    private void deleteMessage(Message message, String reason){
        // Needs the bot to have Manage Messages in the relay channel.
        message.delete().queue(null, error -> this.plugin.getLogger().warning(
            "Couldn't delete Discord message in #" + message.getChannel().getName() + " (" + reason + "): " + error.getMessage()));
    }

    private static String buildContent(Message message){
        // Display content resolves <@id> mentions to readable names and strips nothing else. Attachments aren't
        // forwarded, so an attachment-only message comes out empty and is skipped.
        String content = message.getContentDisplay().replace('\n', ' ').trim();
        // Minecraft's font has no glyphs for most emoji, so turn them into shortcodes (e.g. :grinning:), dropping
        // skin tones, which would otherwise come out as ":thumbsup|type_3:". Emoji newer than DiscordSRV's bundled
        // emoji list are left as-is.
        content = EmojiParser.parseToAliases(content, EmojiParser.FitzpatrickAction.REMOVE)
            // The parser leaves behind invisible emoji variation selectors (e.g. after a heart), which Minecraft
            // can draw as boxes.
            .replace("\uFE0F", "").replace("\uFE0E", "");
        // Pasted InteractiveChat markers would otherwise show other players' shared inventories in-game.
        content = InteractiveChatSanitizer.neutralize(content);
        if(content.length() > MAX_MESSAGE_LENGTH){
            content = content.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        return content;
    }

}
