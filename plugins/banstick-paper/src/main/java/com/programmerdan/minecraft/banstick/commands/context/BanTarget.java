package com.programmerdan.minecraft.banstick.commands.context;

import inet.ipaddr.IPAddress;
import java.util.UUID;

/**
 * The result of resolving a single ambiguous "ip[/cidr]" or "[+]name/uuid[/cidr]"
 * command token into either an IP address or a player UUID, with an optional
 * CIDR suffix and an optional leading {@code +} flag (used by commands like
 * {@code /doubletap} to mean "and also ban this one").
 *
 * <p>Deliberately resolves to a raw {@link UUID} rather than a hydrated
 * {@code BSPlayer}, since some callers (e.g. {@code /banstick}) need to be able
 * to target a player BanStick has never seen before.
 */
public final class BanTarget {

    private final IPAddress ip;
    private final UUID playerId;
    private final Integer cidr;
    private final boolean plusFlag;

    private BanTarget(IPAddress ip, UUID playerId, Integer cidr, boolean plusFlag) {
        this.ip = ip;
        this.playerId = playerId;
        this.cidr = cidr;
        this.plusFlag = plusFlag;
    }

    public static BanTarget ofIp(IPAddress ip, Integer cidr, boolean plusFlag) {
        return new BanTarget(ip, null, cidr, plusFlag);
    }

    public static BanTarget ofPlayer(UUID playerId, Integer cidr, boolean plusFlag) {
        return new BanTarget(null, playerId, cidr, plusFlag);
    }

    public boolean isIp() {
        return ip != null;
    }

    public IPAddress getIp() {
        return ip;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean hasCidr() {
        return cidr != null;
    }

    public Integer getCidr() {
        return cidr;
    }

    public boolean hasPlusFlag() {
        return plusFlag;
    }
}
