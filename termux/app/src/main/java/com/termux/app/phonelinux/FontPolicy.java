package com.termux.app.phonelinux;

/** Decides what to do with ~/.termux/font.ttf so a font the user put there is never touched. */
public final class FontPolicy {

    public enum Action { NONE, INSTALL, REMOVE }

    /** SHA-256 of the bundled UDEVGothic-Regular.ttf (v2.2.0). */
    public static final String BUNDLED_SHA256 = "25fa0eca4be611ac125f03411d280c85494153da01dfa02ba734399c05f6289d";

    private FontPolicy() {}

    /**
     * @param currentSha  SHA-256 of the existing font.ttf, null if there is none
     * @param recordedSha SHA-256 recorded when we last installed it, null if never
     */
    public static Action decide(boolean wantBundled, String currentSha, String recordedSha) {
        if (currentSha == null) return wantBundled ? Action.INSTALL : Action.NONE;
        if (!currentSha.equals(recordedSha)) return Action.NONE;
        if (!wantBundled) return Action.REMOVE;
        return currentSha.equals(BUNDLED_SHA256) ? Action.NONE : Action.INSTALL;
    }
}
