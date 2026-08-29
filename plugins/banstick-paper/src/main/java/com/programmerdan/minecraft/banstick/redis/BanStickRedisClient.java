package com.programmerdan.minecraft.banstick.redis;

import com.programmerdan.minecraft.banstick.BanStick;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import world.edenmc.banstickapi.AckResponse;
import world.edenmc.banstickapi.BanCheckRequest;
import world.edenmc.banstickapi.BanStatusResponse;
import world.edenmc.banstickapi.BanStickChannels;
import world.edenmc.banstickapi.BanStickCodec;
import world.edenmc.banstickapi.BanStickMessageType;
import world.edenmc.banstickapi.CreateExclusionRequest;
import world.edenmc.banstickapi.DeleteExclusionRequest;
import world.edenmc.banstickapi.Envelope;
import world.edenmc.banstickapi.ExclusionsResponse;
import world.edenmc.banstickapi.GetExclusionsRequest;
import world.edenmc.banstickapi.IssueBanRequest;
import world.edenmc.banstickapi.IssueUnbanRequest;
import world.edenmc.banstickapi.SetPardonRequest;

/**
 * Talks to banstick-velocity over Redis pub/sub. banstick-velocity is the single
 * owner of direct-ban state, so every ban/unban issuance and every "is this player
 * banned" check goes through here instead of touching bs_player.bid / bs_ban
 * locally -- that's what keeps Main and PVP from ever disagreeing about who's
 * banned.
 */
public class BanStickRedisClient {

    private final Map<UUID, CompletableFuture<Envelope>> pending = new ConcurrentHashMap<>();
    private final JedisPool pool;
    private final long requestTimeoutMs;

    private JedisPubSub subscriber;
    private Thread subscriberThread;

    public BanStickRedisClient(FileConfiguration config) {
        ConfigurationSection redis = config.getConfigurationSection("redis");
        if (redis == null) {
            throw new RuntimeException("Failed to set up BanStick Redis client, no redis section in config.");
        }
        String host = redis.getString("host", "localhost");
        int port = redis.getInt("port", 6379);
        String password = redis.getString("password", "");
        this.requestTimeoutMs = redis.getLong("requestTimeoutMs", 5000L);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        if (password != null && !password.isBlank()) {
            this.pool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            this.pool = new JedisPool(poolConfig, host, port);
        }

        startSubscriber();
    }

    private void startSubscriber() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                handleResponse(message);
            }
        };
        subscriberThread = new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(subscriber, BanStickChannels.RESPONSES);
            } catch (Exception e) {
                BanStick.getPlugin().severe("BanStick Redis subscriber stopped unexpectedly", e);
            }
        }, "banstick-paper-redis-subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void handleResponse(String raw) {
        Envelope envelope;
        try {
            envelope = BanStickCodec.decodeEnvelope(raw);
        } catch (Exception e) {
            BanStick.getPlugin().warning("Failed to decode banstick response envelope: {0}", e.getMessage());
            return;
        }

        UUID requestId = switch (envelope.type()) {
            case BanStickMessageType.BAN_STATUS ->
                BanStickCodec.decodePayload(envelope, BanStatusResponse.class).requestId();
            case BanStickMessageType.ACK ->
                BanStickCodec.decodePayload(envelope, AckResponse.class).requestId();
            case BanStickMessageType.EXCLUSIONS ->
                BanStickCodec.decodePayload(envelope, ExclusionsResponse.class).requestId();
            default -> null;
        };
        if (requestId == null) {
            return;
        }

        CompletableFuture<Envelope> future = pending.get(requestId);
        if (future != null) {
            future.complete(envelope);
        }
    }

    private CompletableFuture<Envelope> registerPending(UUID requestId) {
        CompletableFuture<Envelope> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return future;
    }

    private void publish(String type, Object payload) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(BanStickChannels.REQUESTS, BanStickCodec.encode(type, payload));
        } catch (Exception e) {
            BanStick.getPlugin().severe("Failed to publish banstick request", e);
        }
    }

    /**
     * Asks banstick-velocity for the authoritative ban+pardon status of a player.
     * Blocks the calling thread (with a timeout) -- callers should invoke this off
     * the main server thread where possible. Empty means the request timed out or
     * banstick-velocity is unreachable -- distinct from "not banned", which is a
     * present response with {@code banned() == false}.
     */
    public Optional<BanStatusResponse> getStatus(UUID playerId) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.BAN_CHECK, new BanCheckRequest(requestId, playerId));
        try {
            Envelope envelope = future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            return Optional.of(BanStickCodec.decodePayload(envelope, BanStatusResponse.class));
        } catch (Exception e) {
            BanStick.getPlugin().warning("Timed out waiting for status of {0} from banstick-velocity", playerId);
            return Optional.empty();
        } finally {
            pending.remove(requestId);
        }
    }

    /**
     * Convenience over {@link #getStatus(UUID)} for pure "is this player banned"
     * checks. Empty means "not banned" OR "couldn't reach banstick-velocity in
     * time"; check the log for the latter.
     */
    public Optional<BanStatusResponse> checkBan(UUID playerId) {
        return getStatus(playerId).filter(BanStatusResponse::banned);
    }

    /**
     * Asks banstick-velocity to issue a ban for the given player. Blocks the
     * calling thread (with a timeout) for the acknowledgement.
     */
    public boolean issueBan(UUID playerId, String playerName, String message, Long banEndEpochMs, boolean adminBan) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.ISSUE_BAN,
            new IssueBanRequest(requestId, playerId, playerName, message, banEndEpochMs, adminBan));
        return awaitAck(requestId, future, "issue ban for " + playerId);
    }

    /**
     * Asks banstick-velocity to clear the active ban for the given player, if any.
     * Blocks the calling thread (with a timeout) for the acknowledgement. Unlike
     * the other issue-/set-style methods, this returns the full {@link AckResponse}
     * so callers can tell "was banned, now cleared" apart from "was already
     * unbanned" via {@link AckResponse#wasBanned()}.
     */
    public AckResponse issueUnban(UUID playerId) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.ISSUE_UNBAN, new IssueUnbanRequest(requestId, playerId));
        return awaitAckResponse(requestId, future, "issue unban for " + playerId);
    }

    /**
     * Asks banstick-velocity to set (or, with a null time, clear) one of a
     * player's pardon timestamps. Blocks the calling thread (with a timeout) for
     * the acknowledgement.
     */
    public boolean setPardon(UUID playerId, String pardonType, Long pardonEpochMs) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.SET_PARDON, new SetPardonRequest(requestId, playerId, pardonType, pardonEpochMs));
        return awaitAck(requestId, future, "set " + pardonType + " pardon for " + playerId);
    }

    private boolean awaitAck(UUID requestId, CompletableFuture<Envelope> future, String description) {
        return awaitAckResponse(requestId, future, description).success();
    }

    private AckResponse awaitAckResponse(UUID requestId, CompletableFuture<Envelope> future, String description) {
        try {
            Envelope envelope = future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            AckResponse response = BanStickCodec.decodePayload(envelope, AckResponse.class);
            if (!response.success()) {
                BanStick.getPlugin().warning("banstick-velocity failed to {0}: {1}",
                    description, response.errorMessage());
            }
            return response;
        } catch (Exception e) {
            BanStick.getPlugin().severe("Timed out waiting for banstick-velocity to " + description, e);
            return new AckResponse(requestId, false, "timed out", null);
        } finally {
            pending.remove(requestId);
        }
    }

    /**
     * Asks banstick-velocity for every player excluded from this player (see
     * {@link world.edenmc.banstickapi.GetExclusionsRequest}). Blocks the calling
     * thread (with a timeout). Empty on timeout/error as well as on "no
     * exclusions" -- this is only used as a skip-list during graph traversal, so
     * failing open (treat as no exclusions) is the safe default.
     */
    public Set<UUID> getExclusions(UUID playerId) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.GET_EXCLUSIONS, new GetExclusionsRequest(requestId, playerId));
        try {
            Envelope envelope = future.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            ExclusionsResponse response = BanStickCodec.decodePayload(envelope, ExclusionsResponse.class);
            return new HashSet<>(response.excludedPlayerUuids());
        } catch (Exception e) {
            BanStick.getPlugin().warning("Timed out waiting for exclusions of {0} from banstick-velocity", playerId);
            return Collections.emptySet();
        } finally {
            pending.remove(requestId);
        }
    }

    /**
     * Asks banstick-velocity to create an exclusion between two players. Blocks
     * the calling thread (with a timeout) for the acknowledgement.
     */
    public boolean createExclusion(UUID firstPlayerId, UUID secondPlayerId) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.CREATE_EXCLUSION, new CreateExclusionRequest(requestId, firstPlayerId, secondPlayerId));
        return awaitAck(requestId, future, "create exclusion between " + firstPlayerId + " and " + secondPlayerId);
    }

    /**
     * Asks banstick-velocity to delete the exclusion between two players, if any.
     * Blocks the calling thread (with a timeout) for the acknowledgement.
     */
    public boolean deleteExclusion(UUID firstPlayerId, UUID secondPlayerId) {
        UUID requestId = UUID.randomUUID();
        CompletableFuture<Envelope> future = registerPending(requestId);
        publish(BanStickMessageType.DELETE_EXCLUSION, new DeleteExclusionRequest(requestId, firstPlayerId, secondPlayerId));
        return awaitAck(requestId, future, "delete exclusion between " + firstPlayerId + " and " + secondPlayerId);
    }

    public void shutdown() {
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (Exception ignored) {
                // best effort
            }
        }
        if (pool != null) {
            pool.close();
        }
        pending.values().forEach(f -> f.cancel(false));
        pending.clear();
    }
}
