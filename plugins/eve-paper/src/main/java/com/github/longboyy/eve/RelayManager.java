package com.github.longboyy.eve;

import com.github.longboyy.eve.model.Relay;
import com.github.longboyy.eve.model.RelayType;
import com.github.longboyy.eve.model.SnitchHitType;
import com.untamedears.jukealert.model.Snitch;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ChannelType;
import github.scarsz.discordsrv.dependencies.jda.api.entities.GuildChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Message;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.utils.MarkdownSanitizer;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import vg.civcraft.mc.civmodcore.chat.ChatUtils;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import vg.civcraft.mc.namelayer.group.Group;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelayManager {

    private final static MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final EvePlugin plugin;

    private final Map<Integer, Set<Relay>> relaysByGroupId;
    private final Map<String, Set<Relay>> relaysByChannelId;

    public RelayManager(EvePlugin plugin){
        this.plugin = plugin;
        // Relays are read from chat threads, JDA threads and the main thread, so the indexes must be thread-safe.
        this.relaysByGroupId = new ConcurrentHashMap<>();
        this.relaysByChannelId = new ConcurrentHashMap<>();
    }

    public boolean reloadRelays(){
        List<Relay> relayList = this.plugin.getDatabase().loadRelays();
        if(relayList == null){
            return false;
        }

        this.relaysByGroupId.clear();
        this.relaysByChannelId.clear();
        relayList.forEach(this::registerRelay);
        return true;
    }

    public boolean hasRelay(int groupId, String channelId, RelayType type){
        return this.findGroupRelayByChannel(groupId, channelId, type) != null;
    }

    public boolean createRelay(int groupId, String guildId, String channelId, RelayType type){
        if(this.hasRelay(groupId, channelId, type)) return false;

        Relay relay = this.plugin.getDatabase().insertRelay(groupId, guildId, channelId, type);
        if(relay == null) return false;

        this.registerRelay(relay);
        return true;
    }

    public boolean removeRelay(int groupId, String channelId, RelayType type){
        var foundRelay = findGroupRelayByChannel(groupId, channelId, type);

        if(foundRelay == null) return false;

        if(this.plugin.getDatabase().removeRelay(foundRelay.getRelayId())){
            this.relaysByGroupId.values().forEach(relays -> relays.removeIf(relay -> relay.getRelayId() == foundRelay.getRelayId()));
            this.relaysByChannelId.values().forEach(relays -> relays.removeIf(relay -> relay.getRelayId() == foundRelay.getRelayId()));
            return true;
        }

        return false;
    }

    /**
     * @return The relays of the given type for a group. Never null.
     */
    public Set<Relay> getGroupRelays(int groupId, RelayType type){
        return filterByType(this.relaysByGroupId.get(groupId), type);
    }

    /**
     * @return The relays of the given type bridged into a Discord channel. Never null.
     */
    public Set<Relay> getChannelRelays(String channelId, RelayType type){
        return filterByType(this.relaysByChannelId.get(channelId), type);
    }

    /**
     * Mirrors an in-game group chat message to every chat relay of that group.
     */
    public void publishGroupChat(String senderName, Group group, String message){
        Set<Relay> relays = getGroupRelays(group.getGroupId(), RelayType.CHAT);
        if(relays.isEmpty()) return;

        // Same Discord timestamp style as snitch alerts; it renders in each reader's local time zone.
        String formatted = String.format("<t:%d:T> `[%s]` **%s**: %s",
            getCurrentTime(),
            group.getName().replace("`", "'"),
            MarkdownSanitizer.escape(senderName),
            escapeForDiscord(message)
        );

        relays.forEach(relay -> publishMessageToDiscord(relay.getChannelId(), formatted));
    }

    /**
     * Makes player-typed text show in Discord exactly as typed.
     *
     * Besides markdown, Discord renders {@code <...>} tags: user/role/channel mentions ({@code <@id>}), custom
     * emoji ({@code <:name:id>}), timestamps ({@code <t:...>}) and more. A leading backslash makes Discord show a
     * tag as literal text. Backslashes the player typed are doubled first (MarkdownSanitizer leaves them alone),
     * otherwise typing {@code \<@id>} would cancel our escape and render the mention.
     */
    static String escapeForDiscord(String text){
        String escaped = MarkdownSanitizer.escape(text.replace("\\", "\\\\"));
        return escaped.replace("<", "\\<");
    }

    public void publishSnitchHit(Player player, Snitch snitch, SnitchHitType hitType){
        if(snitch.getGroup() == null) return;
        var relays = getGroupRelays(snitch.getGroup().getGroupId(), RelayType.SNITCH);
        if(relays.isEmpty()) return;

        long currentTime = getCurrentTime();

        String snitchName = snitch.getName();
        if(snitchName == null || snitchName.isBlank()){
            snitchName = "unnamed_snitch";
        }
        // Escape any backticks in the snitch name
        snitchName = snitchName.replace("`", "'");

        String groupName = snitch.getGroup().getName().replace("`", "'");
        String playerName = player.getName().replace("`", "'");

        String message = String.format("<t:%d:T> `[%s]` **%s** %s snitch `%s` at `%s`",
            currentTime,
            groupName,
            playerName,
            hitType.toString().toLowerCase(),
            snitchName,
            locationToString(player.getLocation())
        );

        relays.forEach(relay -> publishMessageToDiscord(relay.getChannelId(), message));
    }

    public void publishPurchase(Player player, Snitch snitch, Location location, ItemStack[] inputs, ItemStack[] outputs){
        if(snitch.getGroup() == null) return;
        var relays = getGroupRelays(snitch.getGroup().getGroupId(), RelayType.SNITCH);
        if(relays.isEmpty()) return;

        long currentTime = getCurrentTime();

        String snitchName = snitch.getName();
        if(snitchName == null || snitchName.isBlank()){
            snitchName = "unnamed_snitch";
        }
        // Escape any backticks in the snitch name
        snitchName = snitchName.replace("`", "'");

        String groupName = snitch.getGroup().getName().replace("`", "'");
        String playerName = player.getName().replace("`", "'");

        ItemStack[] mergedInputs = mergeItemStacks(inputs);
        ItemStack[] mergedOutputs = mergeItemStacks(outputs);

        StringBuilder messageBuilder = new StringBuilder();
        String purchased = mergedOutputs.length == 0 ? "donated" : "purchased";
        messageBuilder.append(String.format("<t:%d:T> `[%s]` **%s** %s at snitch `%s` | `%s`:\n\n",
            currentTime,
            groupName,
            playerName,
            purchased,
            snitchName,
            locationToString(location)
        ));

        if(mergedInputs.length > 0){
            messageBuilder.append("**Input Items:**").append("\n");
            for(ItemStack item : mergedInputs){
                messageBuilder.append("* ").append(ItemStackToString(item)).append("\n");
            }
            messageBuilder.append("\n");
        }

        if(mergedOutputs.length > 0){
            messageBuilder.append("**Output Items:**").append("\n");
            for(ItemStack item : mergedOutputs){
                messageBuilder.append("* ").append(ItemStackToString(item)).append("\n");
            }
        }

        String message = messageBuilder.toString().trim();

        relays.forEach(relay -> publishMessageToDiscord(relay.getChannelId(), message));

        /*
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("Purchase detected at snitch %s", snitchName));
        embedBuilder.setTimestamp(Instant.ofEpochSecond(currentTime));
        StringBuilder

        MessageEmbed embed = embedBuilder.build();
        relays.forEach(relay -> publishEmbedToDiscord(relay.getChannelId(), embed));
         */

    }

    private @Nullable Relay findGroupRelayByChannel(int groupId, String channelId, RelayType type){
        return getGroupRelays(groupId, type).stream()
            .filter(relay -> relay.getChannelId().equals(channelId))
            .findFirst()
            .orElse(null);
    }

    private void registerRelay(Relay relay){
        this.relaysByGroupId.computeIfAbsent(relay.getGroupId(), id -> ConcurrentHashMap.newKeySet()).add(relay);
        this.relaysByChannelId.computeIfAbsent(relay.getChannelId(), id -> ConcurrentHashMap.newKeySet()).add(relay);
    }

    private static Set<Relay> filterByType(@Nullable Set<Relay> relays, RelayType type){
        if(relays == null) return Set.of();
        return relays.stream().filter(relay -> relay.getType() == type).collect(Collectors.toUnmodifiableSet());
    }

    private String locationToString(Location location){
        return "[" + location.getWorld().getName() + " | " +
            location.getBlockX() + ", " +
            location.getBlockY() + ", " +
            location.getBlockZ() + "]";
    }

    private void publishMessageToDiscord(String channelId, String message){
        try {
            GuildChannel channel = DiscordUtil.getJda().getGuildChannelById(channelId);
            if(channel == null || channel.getType() != ChannelType.TEXT) return;
            TextChannel textChannel = (TextChannel)channel;
            // Player-controlled text (chat, snitch names) must never be able to ping @everyone, roles or users.
            textChannel.sendMessage(message).allowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue();
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("Failed to publish message to Discord channel(%s)", channelId));
        }
    }

    /*
    private void publishEmbedToDiscord(String channelId, MessageEmbed embed){
        try {
            GuildChannel channel = DiscordUtil.getJda().getGuildChannelById(channelId);
            if(channel == null || channel.getType() != ChannelType.TEXT) return;
            TextChannel textChannel = (TextChannel)channel;
            textChannel.sendMessageEmbeds(embed).queue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
     */

    private long getCurrentTime(){
        return System.currentTimeMillis() / 1000L;
    }

    private static final Pattern ITEMSTACK_PATTERN =  Pattern.compile("\\[(.*)]");
    private static String ItemStackToString(ItemStack itemStack){
        StringBuilder builder = new StringBuilder();
        builder.append("x").append(itemStack.getAmount()).append(" ");

        String displayName = ChatUtils.stringify(itemStack.displayName());
        Matcher matcher = ITEMSTACK_PATTERN.matcher(displayName);
        builder.append(matcher.find() ? matcher.group(1) : displayName);

        return builder.toString();
    }

    private static ItemStack[] mergeItemStacks(ItemStack[] items){
        List<ItemStack> itemList = new ArrayList<>();
        for(ItemStack item : items){
            if(item == null) continue;
            ItemStack existingItem = itemList.stream().filter(itemStack -> ItemUtils.areItemsSimilar(item, itemStack)).findFirst().orElse(null);
            if(existingItem != null){
                itemList.remove(existingItem);
                int totalStackSize = existingItem.getAmount() + item.getAmount();
                while(totalStackSize > 0){
                    int addAmount = Math.min(totalStackSize, item.getMaxStackSize());
                    ItemStack newItem = existingItem.clone();
                    newItem.setAmount(addAmount);
                    itemList.add(newItem);
                    totalStackSize -= addAmount;
                }
            }else{
                itemList.add(item);
            }
        }

        return itemList.toArray(ItemStack[]::new);
    }
}
