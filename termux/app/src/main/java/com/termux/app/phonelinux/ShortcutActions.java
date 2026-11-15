package com.termux.app.phonelinux;

import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.terminal.TerminalSession;

/** Actions the hardware keyboard shortcuts can trigger. */
public final class ShortcutActions {

    private ShortcutActions() {}

    /** Whether holding the key should repeat the action. */
    static boolean isRepeatable(String action) {
        return KeysConfig.FONT_UP.equals(action) || KeysConfig.FONT_DOWN.equals(action) || KeysConfig.NEWLINE.equals(action);
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
                ImageInbox.pickImage(activity);
                break;
            case KeysConfig.NEWLINE: {
                // ESC + CR (Meta+Enter): Claude Code inserts a line break instead of submitting.
                TerminalSession session = activity.getTerminalView().getCurrentSession();
                if (session != null) session.write("\033\r");
                break;
            }
            default:
                break;
        }
    }
}
