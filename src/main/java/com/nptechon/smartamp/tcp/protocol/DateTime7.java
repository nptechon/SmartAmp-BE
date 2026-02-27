package com.nptechon.smartamp.tcp.protocol;

import java.time.LocalDateTime;

public class DateTime7 {
    public static byte[] now() {
        LocalDateTime t = LocalDateTime.now();

        int yy = t.getYear() % 100;
        int mm = t.getMonthValue();
        int dd = t.getDayOfMonth();

        // Sun=0, Mon=1, ..., Sat=6
        int w  = t.getDayOfWeek().getValue() % 7;

        int hh = t.getHour();
        int mi = t.getMinute();
        int ss = t.getSecond();

        return new byte[] {
                (byte) yy,
                (byte) mm,
                (byte) dd,
                (byte) w,
                (byte) hh,
                (byte) mi,
                (byte) ss
        };
    }
}
