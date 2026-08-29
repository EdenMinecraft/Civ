package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity to issue (or update) a ban for a specific player. This is
 * intentionally UUID-only: whether the ban originated from an admin command or a
 * heuristic (IP/proxy/share) detection on banstick-paper is not banstick-velocity's
 * concern, it just needs to know who to ban and why.
 *
 * @param playerName    best-known display name, used only if banstick-velocity has
 *                      to create a new player record for a UUID it hasn't seen.
 * @param banEndEpochMs null for a permanent ban.
 */
public record IssueBanRequest(UUID requestId, UUID playerUuid, String playerName, String message,
                               Long banEndEpochMs, boolean adminBan) {
}
