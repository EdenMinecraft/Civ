package world.edenmc.banstickapi;

/**
 * Wire envelope for a single Redis pub/sub channel carrying several message shapes.
 * {@code type} is one of the {@link BanStickMessageType} constants; {@code payload}
 * is the JSON-encoded record matching that type.
 */
public record Envelope(String type, String payload) {
}
