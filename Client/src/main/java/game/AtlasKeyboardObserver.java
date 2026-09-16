package game;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Failure-isolated keyboard observer used by Client Atlas tracing.
 *
 * The observer mirrors Matrix3's verified Class549_Sub1 normalization rules
 * without taking ownership of input handling or consuming AWT events.
 */
final class AtlasKeyboardObserver {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private AtlasKeyboardObserver() {
    }

    static void ensureInstalled() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                @Override
                public void eventDispatched(AWTEvent event) {
                    dispatch(event);
                }
            }, AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK);
        } catch (RuntimeException ex) {
            INSTALLED.set(false);
        }
    }

    private static void dispatch(AWTEvent event) {
        try {
            if (event instanceof KeyEvent) {
                observeKey((KeyEvent) event);
            } else if (event instanceof FocusEvent && event.getID() == FocusEvent.FOCUS_LOST) {
                AtlasRuntimeBridge.observeKeyboardEvent(-1, '\0', 0);
            }
        } catch (RuntimeException ex) {
            // Atlas observation must never interfere with Matrix3 input handling.
        }
    }

    private static void observeKey(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_TYPED) {
            char character = event.getKeyChar();
            if (character != '\uffff' && Class461.method5468(character, (short) 616)) {
                AtlasRuntimeBridge.observeKeyboardEvent(3, character, -1);
            }
            return;
        }

        int action;
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            action = 0;
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            action = 1;
        } else {
            return;
        }

        int keyCode = event.getKeyCode();
        if (keyCode != 0) {
            if (keyCode >= 0 && keyCode < Class549_Sub1.anIntArray8901.length) {
                keyCode = Class549_Sub1.anIntArray8901[keyCode];
                if (action == 0 && (keyCode & 0x80) != 0) {
                    keyCode = 0;
                } else {
                    keyCode &= ~0x80;
                }
            } else {
                keyCode = 0;
            }
        }

        if (keyCode != 0) {
            AtlasRuntimeBridge.observeKeyboardEvent(action, '\uffff', keyCode);
        }
    }
}
