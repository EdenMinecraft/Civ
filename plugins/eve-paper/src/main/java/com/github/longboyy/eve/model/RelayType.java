package com.github.longboyy.eve.model;

import java.util.Locale;

public enum RelayType {
    /** Two-way bridge between a NameLayer group's chat and a Discord channel. */
    CHAT,
    /** One-way feed of snitch hits, logins/logouts and shop purchases for a group. */
    SNITCH;

    public static RelayType fromString(String s){
        return valueOf(s.trim().toUpperCase(Locale.ROOT));
    }

    public String displayName(){
        return name().toLowerCase(Locale.ROOT);
    }
}
