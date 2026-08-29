package world.edenmc.banstickapi;

import java.util.List;
import java.util.UUID;

/**
 * Reply to a {@link GetExclusionsRequest}.
 */
public record ExclusionsResponse(UUID requestId, List<UUID> excludedPlayerUuids) {
}
