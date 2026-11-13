package com.github.longboyy.eve.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles InteractiveChat's internal chat markers, e.g. {@code <chat=29f37c41-553c-4bec-817c-ad692febbefb:[inv]:>}.
 *
 * When a player types a keyword like {@code [inv]}, InteractiveChat swaps it for one of these markers, and later
 * expands the marker into the shared inventory/item wherever it shows up in chat sent to players. That means:
 * <ul>
 *     <li>Game to Discord: the raw marker leaks into Discord unless it's turned back into its label.</li>
 *     <li>Discord to game: anyone can paste a marker and show another player's shared inventory in-game, or use
 *     other InteractiveChat syntax such as {@code <IC^...>} to format their text.</li>
 * </ul>
 */
public final class InteractiveChatSanitizer {

    /** A complete marker: {@code <type=uuid:label:>}. Group 1 is the label shown to players. */
    private static final Pattern MARKER = Pattern.compile(
        "<\\w+=[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}:(.*?):>");

    /**
     * Any {@code <} that could open a tag: one directly followed by a non-space character. InteractiveChat has more
     * syntax than {@code <chat=...>} (e.g. {@code <IC^...>}), so defusing only known tags isn't enough. A spaced
     * {@code <} as in "x < y" can't start a tag and is left as-is.
     */
    private static final Pattern TAG_START = Pattern.compile("<(?=\\S)");

    private InteractiveChatSanitizer(){}

    /**
     * Replaces each marker with its visible label (e.g. {@code [inv]}), for text leaving the game.
     */
    public static String toLabels(String message){
        return MARKER.matcher(message).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }

    /**
     * Makes text from outside the game unable to trigger InteractiveChat. Markers become their labels so they stay
     * readable, then every tag-like {@code <} (including any inside those labels) is swapped for a look-alike
     * angle bracket. That last step is what makes this safe: no tag can survive it, whatever the input.
     */
    public static String neutralize(String message){
        return TAG_START.matcher(toLabels(message)).replaceAll("‹");
    }
}
