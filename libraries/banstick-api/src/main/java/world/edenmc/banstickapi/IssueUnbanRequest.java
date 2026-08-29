package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity to clear the active ban for a specific player, if any.
 */
public record IssueUnbanRequest(UUID requestId, UUID playerUuid) {
}
