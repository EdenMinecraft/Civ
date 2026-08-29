package com.programmerdan.minecraft.banstick.handler;

import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.redis.BanStickRedisClient;
import java.util.Set;
import java.util.UUID;

/**
 * banstick-velocity is the single owner of the multi-account graph exclusions
 * (bs_exclusion) -- like ban and pardon state, this used to be a per-server
 * cache in {@link com.programmerdan.minecraft.banstick.data.BSPlayer} that was
 * never evicted, so an exclusion created on one server could stay invisible on
 * another indefinitely. Every read/write here goes through banstick-velocity
 * instead.
 */
public final class ExclusionHandler {

    private static BanStickRedisClient redisClient;

    private ExclusionHandler() {
    }

    /**
     * Wires up the Redis client used to talk to banstick-velocity. Called once
     * from {@link BanStick#onEnable()}.
     */
    public static void setRedisClient(BanStickRedisClient client) {
        redisClient = client;
    }

    private static BanStickRedisClient redisClient() {
        if (redisClient == null) {
            throw new IllegalStateException("BanStick Redis client not initialized");
        }
        return redisClient;
    }

    /**
     * All players excluded from the given player, straight from
     * banstick-velocity.
     */
    public static Set<UUID> getExcludedPlayers(UUID playerId) {
        return redisClient().getExclusions(playerId);
    }

    public static boolean hasExclusionWith(UUID playerId, UUID otherPlayerId) {
        return getExcludedPlayers(playerId).contains(otherPlayerId);
    }

    public static boolean createExclusion(UUID firstPlayerId, UUID secondPlayerId) {
        return redisClient().createExclusion(firstPlayerId, secondPlayerId);
    }

    public static boolean deleteExclusion(UUID firstPlayerId, UUID secondPlayerId) {
        return redisClient().deleteExclusion(firstPlayerId, secondPlayerId);
    }
}
