package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity for the current, authoritative ban status of a player.
 */
public record BanCheckRequest(UUID requestId, UUID playerUuid) {
}
