package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity to set or clear one of a player's pardon timestamps.
 *
 * @param pardonType     one of {@link PardonType}.
 * @param pardonEpochMs  the new pardon time, or null to clear the pardon.
 */
public record SetPardonRequest(UUID requestId, UUID playerUuid, String pardonType, Long pardonEpochMs) {
}
