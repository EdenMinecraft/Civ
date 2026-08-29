package world.edenmc.banstickapi;

/**
 * Discriminator values used in {@link Envelope#type()}.
 */
public final class BanStickMessageType {

    public static final String BAN_CHECK = "BAN_CHECK";
    public static final String ISSUE_BAN = "ISSUE_BAN";
    public static final String ISSUE_UNBAN = "ISSUE_UNBAN";
    public static final String SET_PARDON = "SET_PARDON";
    public static final String GET_EXCLUSIONS = "GET_EXCLUSIONS";
    public static final String CREATE_EXCLUSION = "CREATE_EXCLUSION";
    public static final String DELETE_EXCLUSION = "DELETE_EXCLUSION";
    public static final String BAN_STATUS = "BAN_STATUS";
    public static final String EXCLUSIONS = "EXCLUSIONS";
    public static final String ACK = "ACK";

    private BanStickMessageType() {
    }
}
