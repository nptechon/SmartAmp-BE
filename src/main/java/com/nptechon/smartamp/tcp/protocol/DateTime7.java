package com.nptechon.smartamp.tcp.protocol;

import java.time.LocalDateTime;

public class DateTime7 {
    // W: 1~7(월=1..일=7)
    // 예) Wed=03처럼 보여서 "월=1" 기준으로 매핑
    public static byte[] now() {
        LocalDateTime t = LocalDateTime.now();

        int yy = t.getYear() % 100;
        int mm = t.getMonthValue();
        int dd = t.getDayOfMonth();
        int w  = t.getDayOfWeek().getValue(); // Mon=1..Sun=7
        int hh = t.getHour();
        int mi = t.getMinute();
        int ss = t.getSecond();

        return new byte[] {
                (byte) yy, (byte) mm, (byte) dd, (byte) w,
                (byte) hh, (byte) mi, (byte) ss
        };
    }
}
