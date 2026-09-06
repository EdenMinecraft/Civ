package com.github.longboyy.eve;

import com.github.longboyy.eve.model.Relay;
import com.github.longboyy.eve.model.SnitchHitType;
import com.untamedears.jukealert.model.Snitch;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.ChannelType;
import github.scarsz.discordsrv.dependencies.jda.api.entities.GuildChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelayManager {

    private final static MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final EvePlugin plugin;

    private final Map<Integer, Set<Relay>> relaysByGroupId;
    private final Map<String, Set<Relay>> relaysByChannelId;

    public RelayManager(EvePlugin plugin){
        this.plugin = plugin;
        this.relaysByGroupId = new HashMap<>();
        this.relaysByChannelId = new HashMap<>();
    }

    public boolean reloadRelays(){
        List<Relay> relayList = this.plugin.getDatabase().loadRelays();
        if(relayList == null){
            return false;
        }

        this.relaysByGroupId.clear();
        relayList.forEach(this::registerRelay);
        return true;
    }

    public boolean hasRelay(int groupId, String channelId){
        return this.findGroupRelayByChannel(groupId, channelId) != null;
    }

    public boolean createRelay(int groupId, String guildId, String channelId){
        if(this.hasRelay(groupId, channelId)) return false;

        Relay relay = this.plugin.getDatabase().insertRelay(groupId, guildId, channelId);
        if(relay == null) return false;

        this.registerRelay(relay);
        return true;
    }

    public boolean removeRelay(int groupId, String channelId){
        var foundRelay = findGroupRelayByChannel(groupId, channelId);

        if(foundRelay == null) return false;

        if(this.plugin.getDatabase().removeRelay(foundRelay.getRelayId())){
            this.relaysByGroupId.values().forEach(relays -> relays.removeIf(relay -> relay.getRelayId() == foundRelay.getRelayId()));
            this.relaysByChannelId.values().forEach(relays -> relays.removeIf(relay -> relay.getRelayId() == foundRelay.getRelayId()));
            return true;
        }

        return false;
    }

    public void publishSnitchHit(Player player, Snitch snitch, SnitchHitType hitType){
        if(!snitchAndRelayExists(snitch)) return;

        long currentTime = getCurrentTime();
        var relays = this.relaysByGroupId.get(snitch.getGroup().getGroupId());

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
        if(!this.relaysByGroupId.containsKey(snitch.getGroup().getGroupId())) return;

        long currentTime = getCurrentTime();
        var relays = this.relaysByGroupId.get(snitch.getGroup().getGroupId());

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

    private @Nullable Relay findGroupRelayByChannel(int groupId, String channelId){
        return relaysByGroupId.values().stream()
            .flatMap(Set::stream)
            .filter(relay -> relay.getGroupId() == groupId && relay.getChannelId().equals(channelId))
            .findFirst()
            .orElse(null);

    }

    private void registerRelay(Relay relay){
        if(!this.relaysByGroupId.containsKey(relay.getGroupId())){
            this.relaysByGroupId.put(relay.getGroupId(), new HashSet<>());
        }

        this.relaysByGroupId.get(relay.getGroupId()).add(relay);
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
            textChannel.sendMessage(message).queue();
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private boolean snitchAndRelayExists(Snitch snitch){
        return snitch.getGroup() != null && this.relaysByGroupId.containsKey(snitch.getGroup().getGroupId());
    }

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
