package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity to create an exclusion between two players (they'll no
 * longer be treated as connected alts during transitive lookups). Idempotent --
 * if the exclusion already exists, this is a no-op success.
 */
public record CreateExclusionRequest(UUID requestId, UUID firstPlayerUuid, UUID secondPlayerUuid) {
}
