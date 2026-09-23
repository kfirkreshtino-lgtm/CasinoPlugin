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
 * One playing card as a physical object on the table.
 *
 * <p>A card is a text display with an opaque background, laid flat and scaled to the
 * proportions of a real card rather than a whole block. Display entities are used instead
 * of item frames holding filled maps because a filled map only renders its picture inside a
 * frame, which locks the card to a full block face, cannot move smoothly and cannot turn
 * over.
 *
 * <p>Two different smoothing mechanisms are used, which is why this class looks the way it
 * does. Sliding across the felt is a teleport with a teleport duration set, so the client
 * tweens the position. Turning face up is a transformation change with an interpolation
 * duration set, so the client tweens the rotation into a real flip.
 */
public final class CardVisual {

    /**
     * Uniform scale of the text display. The card's shape comes from its text, three lines
     * with a padded middle line, which gives a background of roughly 24 by 32 text pixels.
     * At 0.025 blocks per text pixel this makes a card about 0.42 by 0.56 blocks.
     */
    private static final float CARD_SCALE = 0.7f;
    /** Height of the card in blocks, used to pivot it about its centre. */
    private static final float CARD_HEIGHT = 32 * 0.025f * CARD_SCALE;
    /** How far the card is propped up off the felt towards the player, in degrees. */
    private static final float TILT = 25f;

    private static final Color FACE_BACKGROUND = Color.fromARGB(255, 250, 250, 248);
    private static final Color BACK_BACKGROUND = Color.fromARGB(255, 130, 20, 28);
    private static final String BACK_TEXT = "<color:#d8b45a> \n  ◆  \n </color>";

    private final TextDisplay display;
    private final float yaw;

    private Card card;
    private boolean faceUp;

    private CardVisual(TextDisplay display, float yaw) {
        this.display = display;
        this.yaw = yaw;
    }

    /**
     * Spawns a card lying flat and face down.
     *
     * @param where where the card starts, usually the dealer shoe
     * @param yaw   direction the table faces, so the card reads the right way up
     */
    public static CardVisual spawn(Location where, float yaw) {
        Location spot = where.clone();
        spot.setYaw(yaw);
        spot.setPitch(0f);

        TextDisplay display = spot.getWorld().spawn(spot, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setLineWidth(40);
            entity.setBackgroundColor(BACK_BACKGROUND);
            entity.setSeeThrough(false);
            entity.setShadowed(false);
            entity.setViewRange(0.4f);
            entity.setPersistent(false);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setTeleportDuration(1);
            entity.text(Text.mm(BACK_TEXT));
            entity.setTransformation(flatTransform(false));
        });
        return new CardVisual(display, yaw);
    }

    /**
     * Lays the card flat on the table.
     *
     * <p>A text display stands upright facing the viewer by default, so it is turned
     * almost a quarter turn about X to lie down, stopping short by {@link #TILT} so the face
     * leans towards the seated player instead of being seen edge on. Face down adds a half
     * turn about Y, and removing that half turn is what the client animates as the card
     * turning over.
     *
     * <p>A text display pivots on the bottom edge of its text, so the card is shifted by
     * half its height to turn about its centre, then lifted clear of the felt.
     */
    private static Transformation flatTransform(boolean faceUp) {
        Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(90 - TILT));
        if (!faceUp) {
            rotation.rotateY((float) Math.toRadians(180));
        }
        Vector3f centre = rotation.transform(new Vector3f(0f, -CARD_HEIGHT / 2f, 0f));
        centre.y += (float) (CARD_HEIGHT / 2f * Math.sin(Math.toRadians(TILT))) + 0.02f;
        return new Transformation(
                centre,
                rotation,
                new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE),
                new Quaternionf());
    }

    /** Slides the card to a spot on the felt over the given number of ticks. */
    public void moveTo(Location target, int ticks) {
        Location spot = target.clone();
        spot.setYaw(yaw);
        spot.setPitch(0f);
        display.setTeleportDuration(clampTeleport(ticks));
        display.teleport(spot);
    }

    /** Turns the card face up, revealing its rank and suit. */
    public void reveal(Card revealed, int ticks) {
        this.card = revealed;
        this.faceUp = true;
        display.setBackgroundColor(FACE_BACKGROUND);
        display.text(Text.mm(face(revealed)));
        interpolate(true, ticks);
    }

    /** Turns the card back over and hides what it is. */
    public void conceal(int ticks) {
        this.faceUp = false;
        display.setBackgroundColor(BACK_BACKGROUND);
        display.text(Text.mm(BACK_TEXT));
        interpolate(false, ticks);
    }

    private void interpolate(boolean up, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(flatTransform(up));
    }

    /** Teleport duration is capped at 59 ticks by the server. */
    private static int clampTeleport(int ticks) {
        return Math.max(1, Math.min(59, ticks));
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
