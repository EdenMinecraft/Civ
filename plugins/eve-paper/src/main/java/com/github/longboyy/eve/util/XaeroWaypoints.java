package com.github.longboyy.eve.util;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps Xaero's Minimap shared waypoints in-game only.
 *
 * Sharing a waypoint sends a chat message like
 * {@code xaero-waypoint:/dest RADS W1 Tidehold:/:-7303:82:131:8:false:0:Internal-overworld}
 * (name:initials:x:y:z:colour:rotate:yaw:dimension), which the mod turns into a clickable waypoint for players who
 * have it. Waypoints carry coordinates, so they must never be relayed to Discord.
 */
public final class XaeroWaypoints {

    /**
     * One shared waypoint. Covers the current {@code xaero-waypoint:} prefix and the older
     * {@code xaero_waypoint_add:} one. Y can be {@code ~} when the sharer's waypoint has no fixed height.
     */
    private static final Pattern WAYPOINT = Pattern.compile(
        "xaero[-_]waypoint(?:_add)?:[^:]*:[^:]*:-?\\d+:(?:~|-?\\d+):-?\\d+:\\d+:(?:true|false):-?\\d+:\\S*",
        Pattern.CASE_INSENSITIVE);

    private XaeroWaypoints(){}

    /**
     * @return The message with every waypoint removed, or null if nothing safe is left to relay: either the
     *     message was only waypoints, or it still mentions a waypoint prefix the pattern couldn't parse (a format
     *     variant), in which case the whole message is withheld rather than risk leaking coordinates.
     */
    public static @Nullable String strip(String message){
        String stripped = WAYPOINT.matcher(message).replaceAll("").replaceAll("\\s{2,}", " ").trim();
        if(stripped.isEmpty() || stripped.toLowerCase(Locale.ROOT).matches("(?s).*xaero[-_]waypoint.*")){
            return null;
        }
        return stripped;
    }
}
