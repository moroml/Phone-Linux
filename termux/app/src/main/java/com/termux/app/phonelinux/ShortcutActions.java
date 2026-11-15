package com.termux.app.phonelinux;

import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;

/** Actions the hardware keyboard shortcuts can trigger. */
public final class ShortcutActions {

    private ShortcutActions() {}

    /** Whether holding the key should repeat the action. */
    static boolean isRepeatable(String action) {
        return KeysConfig.FONT_UP.equals(action) || KeysConfig.FONT_DOWN.equals(action);
    }

    public static void run(String action, TermuxActivity activity) {
        switch (action) {
            case KeysConfig.COPY: {
                String text = activity.getTerminalView().getSelectedText();
                if (text != null && !text.isEmpty()) ShareUtils.copyTextToClipboard(activity, text);
                break;
            }
            case KeysConfig.PASTE:
                activity.getTermuxTerminalViewClient().doPaste();
                break;
            case KeysConfig.NEW_SESSION:
                activity.getTermuxTerminalSessionClient().addNewSession(false, null);
                break;
            case KeysConfig.NEXT_SESSION:
                activity.getTermuxTerminalSessionClient().switchToSession(true);
                break;
            case KeysConfig.PREV_SESSION:
                activity.getTermuxTerminalSessionClient().switchToSession(false);
                break;
            case KeysConfig.FONT_UP:
                activity.getTermuxTerminalViewClient().changeFontSize(true);
                break;
            case KeysConfig.FONT_DOWN:
                activity.getTermuxTerminalViewClient().changeFontSize(false);
                break;
            case KeysConfig.INSERT_IMAGE:
                insertImage(activity);
                break;
            default:
                break;
        }
    }

    /** Stub: another worker replaces this with the real image insertion. */
    public static void insertImage(TermuxActivity activity) {
        Toast.makeText(activity, R.string.phone_linux_insert_image_stub, Toast.LENGTH_SHORT).show();
    }
}
