package com.programmerdan.minecraft.banstick.commands.context;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.BukkitCommandExecutionContext;
import co.aikar.commands.CommandCompletions;
import co.aikar.commands.CommandContexts;
import co.aikar.commands.InvalidCommandArgument;
import com.programmerdan.minecraft.banstick.commands.DrillDownCommand;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringException;
import inet.ipaddr.IPAddressTypeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vg.civcraft.mc.namelayer.NameLayerAPI;

/**
 * Registers BanStick's shared ACF typed contexts and tab completions --
 * {@link BSPlayer} (resolve a name/uuid token to a known BanStick player
 * record) and {@link BanTarget} (resolve an ambiguous "ip[/cidr]" or
 * "[+]name/uuid[/cidr]" token, used by the commands that accept either).
 *
 * <p>This replaces the ~20-line name/uuid resolution block that used to be
 * hand-copied into most of the commands in this package.
 */
public final class BanStickContexts {

    private BanStickContexts() {
    }

    public static void register(CommandContexts<BukkitCommandExecutionContext> contexts,
                                 CommandCompletions<BukkitCommandCompletionContext> completions) {
        contexts.registerContext(BSPlayer.class, context -> resolveBSPlayer(context.popFirstArg()));

        contexts.registerContext(BSPlayer[].class, context -> {
            List<BSPlayer> players = new ArrayList<>();
            while (!context.getArgs().isEmpty()) {
                players.add(resolveBSPlayer(context.popFirstArg()));
            }
            if (players.isEmpty()) {
                throw new InvalidCommandArgument();
            }
            return players.toArray(new BSPlayer[0]);
        });

        contexts.registerContext(BanTarget.class, context -> parseBanTarget(context.popFirstArg()));

        contexts.registerContext(DrillDownCommand.Action.class, context -> {
            String token = context.popFirstArg();
            try {
                return DrillDownCommand.Action.match(token);
            } catch (IllegalArgumentException iae) {
                throw new InvalidCommandArgument("Unrecognized action: " + token
                    + " -- use SUMMARY, PLAYER, IP, IPDATA, or IPDATASUMMARY");
            }
        });

        completions.registerCompletion("banstickPlayers",
            context -> Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        completions.setDefaultCompletion("banstickPlayers", BSPlayer.class);
        completions.setDefaultCompletion("banstickPlayers", BanTarget.class);

        completions.registerStaticCompletion("banPardonTypes", List.of("BAN", "IP", "PROXY", "SHARED"));

        completions.registerStaticCompletion("drillDownActions",
            Arrays.stream(DrillDownCommand.Action.values()).map(Enum::name).collect(Collectors.toList()));
        completions.setDefaultCompletion("drillDownActions", DrillDownCommand.Action.class);

        completions.registerStaticCompletion("ipDataAttributes", List.of("CONTINENT", "COUNTRY", "REGION", "STATE",
            "CITY", "POSTAL", "ZIP", "DOMAIN", "PROVIDER", "REGISTEREDAS", "CONNECTION"));
    }

    /**
     * Resolves a single name/uuid token to a known {@link BSPlayer} record,
     * throwing {@link InvalidCommandArgument} if the token can't be resolved
     * to a player, or resolves but BanStick has no record of them.
     */
    private static BSPlayer resolveBSPlayer(String token) {
        UUID playerId = resolvePlayerUuid(token);
        BSPlayer player = BSPlayer.byUUID(playerId);
        if (player == null) {
            throw new InvalidCommandArgument("BanStick has no record of player: " + token);
        }
        return player;
    }

    /**
     * Resolves a single "ip[/cidr]" or "[+]name/uuid[/cidr]" token. Used both
     * as the {@link BanTarget} ACF context resolver and directly by commands
     * (like {@code /doubletap}) whose grammar doesn't reduce to a single fixed
     * positional parameter.
     */
    public static BanTarget parseBanTarget(String raw) {
        boolean plusFlag = raw.startsWith("+");
        String stripped = plusFlag ? raw.substring(1) : raw;
        int locCIDR = stripped.indexOf('/');
        boolean hasCIDR = locCIDR > -1;
        String core = hasCIDR ? stripped.substring(0, locCIDR) : stripped;

        Integer cidr = null;
        if (hasCIDR) {
            try {
                cidr = Integer.valueOf(stripped.substring(locCIDR + 1));
            } catch (NumberFormatException e) {
                throw new InvalidCommandArgument("Invalid CIDR suffix in " + raw);
            }
        }

        try {
            IPAddress ip = new IPAddressString(core).toAddress();
            if (ip != null) {
                return BanTarget.ofIp(ip, cidr, plusFlag);
            }
        } catch (IPAddressStringException | IPAddressTypeException ignored) {
            // not an IP -- fall through to player resolution.
        }

        UUID playerId = resolvePlayerUuid(core);
        return BanTarget.ofPlayer(playerId, cidr, plusFlag);
    }

    /**
     * Resolves a name or UUID string to a player UUID: NameLayer first, then
     * online players, then a raw UUID string. Matches the resolution order
     * every command in this package used to hand-roll individually.
     */
    public static UUID resolvePlayerUuid(String token) {
        if (token.length() <= 16) {
            UUID playerId = null;
            try {
                playerId = NameLayerAPI.getUUID(token);
            } catch (NoClassDefFoundError ncde) {
                // no namelayer
            }
            if (playerId == null) {
                Player match = Bukkit.getPlayer(token);
                if (match != null) {
                    playerId = match.getUniqueId();
                }
            }
            if (playerId == null) {
                throw new InvalidCommandArgument("Unable to find player: " + token);
            }
            return playerId;
        } else if (token.length() == 36) {
            try {
                return UUID.fromString(token);
            } catch (IllegalArgumentException iae) {
                throw new InvalidCommandArgument("Unable to process uuid: " + token);
            }
        } else {
            throw new InvalidCommandArgument("Unable to interpret: " + token);
        }
    }
}
