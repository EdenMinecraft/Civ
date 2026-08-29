package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Reply to a {@link BanCheckRequest}. Carries both the player's active-ban status
 * and their IP/proxy/shared pardon timestamps (all owned by banstick-velocity) in
 * one round trip, since callers frequently need several of these together.
 *
 * @param ipPardonEpochMs     null if not pardoned from future IP bans.
 * @param proxyPardonEpochMs  null if not pardoned from future proxy bans.
 * @param sharedPardonEpochMs null if not pardoned from future share bans.
 */
public record BanStatusResponse(UUID requestId, boolean banned, String message,
                                 Long banEndEpochMs, boolean adminBan,
                                 Long ipPardonEpochMs, Long proxyPardonEpochMs, Long sharedPardonEpochMs) {
}
