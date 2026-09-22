package com.kfir.casino.station;

import org.bukkit.Material;

/** The kinds of interaction point a player can right-click. */
public enum StationType {

    CASHIER("Cashier", Material.EMERALD_BLOCK, "<green>"),
    BLACKJACK("Blackjack", Material.LIME_CONCRETE, "<green>"),
    ROULETTE("Roulette", Material.RED_CONCRETE, "<red>"),
    POKER("Poker", Material.LIGHT_BLUE_CONCRETE, "<aqua>");

    private final String displayName;
    private final Material marker;
    private final String color;

    StationType(String displayName, Material marker, String color) {
        this.displayName = displayName;
        this.marker = marker;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** Block the procedural builder places for this station. */
    public Material marker() {
        return marker;
    }

    public String color() {
        return color;
    }

    public static StationType fromString(String input) {
        for (StationType type : values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}
