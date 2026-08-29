package world.edenmc.banstickapi;

import java.util.UUID;

/**
 * Ask banstick-velocity for every player a given player is excluded from
 * (multi-account graph exclusions), used to skip specific connections during
 * transitive alt lookups.
 */
public record GetExclusionsRequest(UUID requestId, UUID playerUuid) {
}
