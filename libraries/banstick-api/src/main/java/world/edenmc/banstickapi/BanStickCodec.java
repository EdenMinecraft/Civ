package world.edenmc.banstickapi;

import com.google.gson.Gson;

/**
 * Encodes/decodes {@link Envelope}s for the banstick Redis channels.
 */
public final class BanStickCodec {

    private static final Gson GSON = new Gson();

    private BanStickCodec() {
    }

    public static String encode(String type, Object payload) {
        return GSON.toJson(new Envelope(type, GSON.toJson(payload)));
    }

    public static Envelope decodeEnvelope(String json) {
        return GSON.fromJson(json, Envelope.class);
    }

    public static <T> T decodePayload(Envelope envelope, Class<T> payloadType) {
        return GSON.fromJson(envelope.payload(), payloadType);
    }
}
