package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity to delete the exclusion between two players, if any.
 */
public record DeleteExclusionRequest(UUID requestId, UUID firstPlayerUuid, UUID secondPlayerUuid) {
}
