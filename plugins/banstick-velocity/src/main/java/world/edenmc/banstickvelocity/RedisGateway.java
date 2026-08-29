package world.edenmc.banstickvelocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
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
 * Handles the Redis side of the protocol: subscribes to {@link BanStickChannels#REQUESTS},
 * dispatches each request to {@link BanStore}, and publishes the reply on
 * {@link BanStickChannels#RESPONSES}. Also kicks an already-connected player the
 * instant a ban against them is processed -- since this plugin is the one enacting
 * the ban, no polling delay is needed.
 */
public class RedisGateway {

    private final ProxyServer proxy;
    private final Logger logger;
    private final JedisPool pool;
    private final BanStore banStore;
    private final ExclusionStore exclusionStore;
    private final String defaultBanMessage;

    private JedisPubSub subscriber;
    private Thread subscriberThread;

    public RedisGateway(ProxyServer proxy, Logger logger, JedisPool pool, BanStore banStore,
                         ExclusionStore exclusionStore, String defaultBanMessage) {
        this.proxy = proxy;
        this.logger = logger;
        this.pool = pool;
        this.banStore = banStore;
        this.exclusionStore = exclusionStore;
        this.defaultBanMessage = defaultBanMessage;
    }

    public void start() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                handleRequest(message);
            }
        };
        subscriberThread = new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(subscriber, BanStickChannels.REQUESTS);
            } catch (Exception e) {
                logger.error("banstick-velocity Redis subscriber stopped unexpectedly", e);
            }
        }, "banstick-velocity-redis-subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void stop() {
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private void handleRequest(String raw) {
        logger.info("Redis <- received on {}: {}", BanStickChannels.REQUESTS, raw);

        Envelope envelope;
        try {
            envelope = BanStickCodec.decodeEnvelope(raw);
        } catch (Exception e) {
            logger.warn("Failed to decode banstick request envelope", e);
            return;
        }

        try {
            switch (envelope.type()) {
                case BanStickMessageType.BAN_CHECK ->
                    handleBanCheck(BanStickCodec.decodePayload(envelope, BanCheckRequest.class));
                case BanStickMessageType.ISSUE_BAN ->
                    handleIssueBan(BanStickCodec.decodePayload(envelope, IssueBanRequest.class));
                case BanStickMessageType.ISSUE_UNBAN ->
                    handleIssueUnban(BanStickCodec.decodePayload(envelope, IssueUnbanRequest.class));
                case BanStickMessageType.SET_PARDON ->
                    handleSetPardon(BanStickCodec.decodePayload(envelope, SetPardonRequest.class));
                case BanStickMessageType.GET_EXCLUSIONS ->
                    handleGetExclusions(BanStickCodec.decodePayload(envelope, GetExclusionsRequest.class));
                case BanStickMessageType.CREATE_EXCLUSION ->
                    handleCreateExclusion(BanStickCodec.decodePayload(envelope, CreateExclusionRequest.class));
                case BanStickMessageType.DELETE_EXCLUSION ->
                    handleDeleteExclusion(BanStickCodec.decodePayload(envelope, DeleteExclusionRequest.class));
                default -> logger.warn("Unknown banstick request type: {}", envelope.type());
            }
        } catch (Exception e) {
            logger.error("Failed to handle banstick request of type {}", envelope.type(), e);
        }
    }

    private static Long epochOrNull(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private void handleBanCheck(BanCheckRequest request) {
        BanStore.PlayerStatus status = banStore.getPlayerStatus(request.playerUuid());
        BanStatusResponse response = status.ban()
            .map(s -> new BanStatusResponse(request.requestId(), true, s.message(), epochOrNull(s.banEnd()),
                s.adminBan(), epochOrNull(status.ipPardon()), epochOrNull(status.proxyPardon()),
                epochOrNull(status.sharedPardon())))
            .orElseGet(() -> new BanStatusResponse(request.requestId(), false, null, null, false,
                epochOrNull(status.ipPardon()), epochOrNull(status.proxyPardon()), epochOrNull(status.sharedPardon())));
        publish(BanStickMessageType.BAN_STATUS, response);
    }

    private void handleSetPardon(SetPardonRequest request) {
        Instant pardonTime = request.pardonEpochMs() != null ? Instant.ofEpochMilli(request.pardonEpochMs()) : null;
        boolean success = banStore.setPardon(request.playerUuid(), null, request.pardonType(), pardonTime);
        publish(BanStickMessageType.ACK,
            new AckResponse(request.requestId(), success, success ? null : "Failed to set pardon", null));
    }

    private void handleGetExclusions(GetExclusionsRequest request) {
        publish(BanStickMessageType.EXCLUSIONS,
            new ExclusionsResponse(request.requestId(), exclusionStore.getExcludedPlayers(request.playerUuid())));
    }

    private void handleCreateExclusion(CreateExclusionRequest request) {
        boolean success = exclusionStore.createExclusion(request.firstPlayerUuid(), request.secondPlayerUuid());
        publish(BanStickMessageType.ACK,
            new AckResponse(request.requestId(), success, success ? null : "Failed to create exclusion", null));
    }

    private void handleDeleteExclusion(DeleteExclusionRequest request) {
        boolean success = exclusionStore.deleteExclusion(request.firstPlayerUuid(), request.secondPlayerUuid());
        publish(BanStickMessageType.ACK,
            new AckResponse(request.requestId(), success, success ? null : "Failed to delete exclusion", null));
    }

    private void handleIssueBan(IssueBanRequest request) {
        Instant banEnd = request.banEndEpochMs() != null ? Instant.ofEpochMilli(request.banEndEpochMs()) : null;
        boolean success = banStore.issueBan(request.playerUuid(), request.playerName(), request.message(),
            banEnd, request.adminBan());
        publish(BanStickMessageType.ACK,
            new AckResponse(request.requestId(), success, success ? null : "Failed to persist ban", null));

        if (success) {
            kickIfOnline(request.playerUuid(), request.message());
        }
    }

    private void handleIssueUnban(IssueUnbanRequest request) {
        BanStore.ClearBanResult result = banStore.clearBan(request.playerUuid());
        publish(BanStickMessageType.ACK,
            new AckResponse(request.requestId(), result.success(),
                result.success() ? null : "Failed to clear ban", result.wasBanned()));
    }

    private void kickIfOnline(UUID uuid, String message) {
        proxy.getPlayer(uuid).ifPresent(player ->
            player.disconnect(Component.text(message != null ? message : defaultBanMessage)));
    }

    private void publish(String type, Object payload) {
        String encoded = BanStickCodec.encode(type, payload);
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(BanStickChannels.RESPONSES, encoded);
            logger.info("Redis -> sent on {}: {}", BanStickChannels.RESPONSES, encoded);
        } catch (Exception e) {
            logger.error("Failed to publish banstick response: {}", encoded, e);
        }
    }
}
