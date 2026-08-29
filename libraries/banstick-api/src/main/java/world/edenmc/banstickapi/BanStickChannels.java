package world.edenmc.banstickapi;

/**
 * Redis pub/sub channel names shared between banstick-paper and banstick-velocity.
 */
public final class BanStickChannels {

    /** banstick-paper -&gt; banstick-velocity. Carries {@link BanCheckRequest}, {@link IssueBanRequest}, {@link IssueUnbanRequest}. */
    public static final String REQUESTS = "banstick.requests";

    /** banstick-velocity -&gt; banstick-paper. Carries {@link BanStatusResponse}, {@link AckResponse}. */
    public static final String RESPONSES = "banstick.responses";

    private BanStickChannels() {
    }
}
