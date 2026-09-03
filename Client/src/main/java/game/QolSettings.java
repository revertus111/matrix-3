package game;

import java.awt.event.KeyEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Client-side quality-of-life preferences and menu-selection helpers.
 *
 * These helpers only select existing Matrix3 menu entries. The normal client
 * action dispatcher remains authoritative for the resulting interaction.
 */
public final class QolSettings {

    private static final String KEY_SHIFT_CLICK_DROP = "shiftClickDrop";
    private static final Preferences PREFS = Preferences.userNodeForPackage(QolSettings.class).node("qol");

    private QolSettings() {
    }

    public static boolean isShiftClickDropEnabled() {
        return PREFS.getBoolean(KEY_SHIFT_CLICK_DROP, false);
    }

    public static void setShiftClickDropEnabled(boolean enabled) {
        try {
            PREFS.putBoolean(KEY_SHIFT_CLICK_DROP, enabled);
            PREFS.flush();
        } catch (BackingStoreException | RuntimeException ex) {
            System.err.println("Unable to save QOL preferences.");
            ex.printStackTrace();
        }
    }

    /**
     * Returns the existing option-5 Drop entry while Shift is held, or null when
     * normal Matrix3 menu selection should remain unchanged.
     */
    static Class572_Sub12_Sub10 resolveShiftClickDropEntry(Class675 entries) {
        if (!isShiftClickDropEnabled() || !isShiftHeld() || entries == null)
            return null;

        String dropLabel = Class279.aClass279_2964.method3758(Class594.aClass435_7823, 16711935);
        for (Class572 node = entries.aClass572_8547.aClass572_6433;
                node != entries.aClass572_8547;
                node = node.aClass572_6433) {
            Class572_Sub12_Sub10 entry = (Class572_Sub12_Sub10) node;
            int opcode = entry.anInt11402 * -44467871;
            if (opcode >= 2000)
                opcode -= 2000;
            int option = (int) (entry.aLong11395 * -6760453999157901937L);
            if ((opcode == 57 || opcode == 1007)
                    && option == 5
                    && dropLabel.equals(entry.aString11393))
                return entry;
        }
        return null;
    }

    private static boolean isShiftHeld() {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(KeyEvent.VK_SHIFT, (byte) 1);
    }
}
