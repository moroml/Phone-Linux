package com.termux.app.phonelinux;

/** What the API can do on the phone. Implemented by {@link PhoneControlService}; faked in tests. */
public interface PhoneActions {

    /** Failure that maps straight to an HTTP status and an error message for the client. */
    class ActionException extends Exception {
        public final int status;

        public ActionException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    byte[] screenshotPng() throws ActionException;

    /** The current window's node tree as a JSON string. */
    String uiTree() throws ActionException;

    void tap(int x, int y) throws ActionException;

    void swipe(int x1, int y1, int x2, int y2, int ms) throws ActionException;

    void type(String text, boolean append) throws ActionException;

    /** name: back, home, recents or notifications. */
    void key(String name) throws ActionException;

    /** Exactly one of pkg and url is non-null. */
    void open(String pkg, String url) throws ActionException;
}
