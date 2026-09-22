package com.kfir.casino.table;

import com.kfir.casino.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * A line of floating text on or above a table, used for hand totals, the current bet and
 * the result of a hand.
 *
 * <p>Unlike the cards these face the viewer, so they stay readable from any seat.
 */
public final class Hologram {

    private final TextDisplay display;

    private Hologram(TextDisplay display) {
        this.display = display;
    }

    public static Hologram spawn(Location where, String miniMessage, float scale) {
        TextDisplay display = where.getWorld().spawn(where, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setViewRange(0.5f);
            entity.setPersistent(false);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.text(Text.mm(miniMessage));
        });
        Hologram hologram = new Hologram(display);
        hologram.setScale(scale);
        return hologram;
    }

    public void setText(String miniMessage) {
        display.text(Text.mm(miniMessage));
    }

    public void setScale(float scale) {
        org.bukkit.util.Transformation current = display.getTransformation();
        display.setTransformation(new org.bukkit.util.Transformation(
                current.getTranslation(),
                current.getLeftRotation(),
                new org.joml.Vector3f(scale, scale, scale),
                current.getRightRotation()));
    }

    public void moveTo(Location target) {
        display.teleport(target);
    }

    public void remove() {
        if (!display.isDead()) {
            display.remove();
        }
    }
}
