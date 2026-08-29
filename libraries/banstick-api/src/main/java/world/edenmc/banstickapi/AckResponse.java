package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Reply to an {@link IssueBanRequest}, {@link IssueUnbanRequest},
 * {@link SetPardonRequest}, {@link CreateExclusionRequest}, or
 * {@link DeleteExclusionRequest}.
 *
 * @param wasBanned only meaningful for {@link IssueUnbanRequest} replies: true if
 *                   the player actually had an active ban that was cleared, false
 *                   if they were already unbanned (a no-op success). Null/ignored
 *                   for replies to other request types.
 */
public record AckResponse(UUID requestId, boolean success, String errorMessage, Boolean wasBanned) {
}
