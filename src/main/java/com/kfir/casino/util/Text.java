package com.kfir.casino.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** MiniMessage helpers. Item names default to non-italic so they read cleanly in a GUI. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Parses MiniMessage and strips the default italic styling applied to item names. */
    public static Component mm(String input) {
        return MM.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    /** Parses MiniMessage keeping whatever decoration the input asks for. */
    public static Component raw(String input) {
        return MM.deserialize(input);
    }

    /** Formats a chip amount with thousands separators. */
    public static String chips(long amount) {
        return String.format("%,d", amount);
    }
}
