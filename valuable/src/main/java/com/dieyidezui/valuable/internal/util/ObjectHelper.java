package com.dieyidezui.valuable.internal.util;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class ObjectHelper {

    public static <T> T requireNonNull(T t) {
        if (t == null) {
            throw new NullPointerException();
        }
        return t;
    }

    public static <T> T requireNonNull(T t, String msg) {
        if (t == null) {
            throw new NullPointerException(msg);
        }
        return t;
    }
}
