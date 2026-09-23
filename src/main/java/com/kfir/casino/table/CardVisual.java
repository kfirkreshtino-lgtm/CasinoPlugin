package com.kfir.casino.table;

import com.kfir.casino.game.card.Card;
import com.kfir.casino.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One playing card standing on the table.
 *
 * <p>A card is a text display with an opaque background, standing on the felt and always
 * turned towards whoever looks at it. There is deliberately no sliding or turning over:
 * the card simply appears where it belongs, face up, or face down for the dealer's hole
 * card, and a face-down card is revealed by changing what it shows.
 */
public final class CardVisual {

    /**
     * Uniform scale of the text display. The card's shape comes from its text, three lines
     * with a padded middle line, which gives a background of roughly 24 by 32 text pixels.
     * At 0.025 blocks per text pixel this makes a card about 0.3 by 0.4 blocks.
     */
    private static final float CARD_SCALE = 0.5f;

    private static final Color FACE_BACKGROUND = Color.fromARGB(255, 250, 250, 248);
    private static final Color BACK_BACKGROUND = Color.fromARGB(255, 130, 20, 28);
    private static final String BACK_TEXT = "<color:#d8b45a> \n  ◆  \n </color>";

    private final TextDisplay display;

    private Card card;
    private boolean faceUp;

    private CardVisual(TextDisplay display) {
        this.display = display;
    }

    /**
     * Places a card on the felt.
     *
     * @param where the card's spot on the table; the card stands on it
     * @param shown the card to show face up, or null to place it face down
     */
    public static CardVisual place(Location where, Card shown) {
        TextDisplay display = where.getWorld().spawn(where, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.VERTICAL);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(40);
            entity.setSeeThrough(false);
            entity.setShadowed(false);
            entity.setViewRange(0.4f);
            entity.setPersistent(false);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE),
                    new Quaternionf()));
        });
        CardVisual visual = new CardVisual(display);
        if (shown != null) {
            visual.reveal(shown);
        } else {
            visual.conceal();
        }
        return visual;
    }

    /** Moves the card to a new spot, for example when its row re-centres. */
    public void moveTo(Location target) {
        display.teleport(target);
    }

    /** Shows the card's rank and suit. */
    public void reveal(Card revealed) {
        this.card = revealed;
        this.faceUp = true;
        display.setBackgroundColor(FACE_BACKGROUND);
        display.text(Text.mm(face(revealed)));
    }

    /** Shows the back of the card. */
    public void conceal() {
        this.faceUp = false;
        display.setBackgroundColor(BACK_BACKGROUND);
        display.text(Text.mm(BACK_TEXT));
    }

    /**
     * Rank, suit, rank, coloured for the suit, which reads as a card at a glance. The
     * spaces around the suit keep every card the same width whatever the rank.
     */
    private static String face(Card card) {
        String color = card.suit().isRed() ? "#c01722" : "#101014";
        String rank = "<bold>" + card.rank().symbol() + "</bold>";
        return "<color:" + color + ">" + rank + "\n  " + card.suit().symbol() + "  \n" + rank + "</color>";
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public Card card() {
        return card;
    }

    public void remove() {
        if (!display.isDead()) {
            display.remove();
        }
    }
}
