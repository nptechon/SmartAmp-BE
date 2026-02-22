package com.nptechon.smartamp.tcp.util;

public final class RepeatValidatorUtil {

    private RepeatValidatorUtil() {}

    public static final int MIN = 1;
    public static final int MAX = 5;
    public static final int INFINITE = 0xFF; // 255

    public static boolean isValid(int repeat) {
        return (repeat >= MIN && repeat <= MAX) || repeat == INFINITE;
    }
}
