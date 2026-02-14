package com.nptechon.smartamp.tcp.protocol.payload;

import com.nptechon.smartamp.tcp.protocol.LogInfoDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class LogPayloadParser {

    private LogPayloadParser() {}

    public static List<LogInfoDto> parseLogResponsePayload(byte[] payload) {
        if (payload == null || payload.length < 1) {
            throw new IllegalArgumentException("log payload empty");
        }

        int n = payload[0] & 0xFF;
        if (n > 200) {
            throw new IllegalArgumentException("log count too large: " + n);
        }

        int expectedMinLen = 1 + (9 * n);
        if (payload.length < expectedMinLen) {
            throw new IllegalArgumentException("log payload length mismatch. n=" + n +
                    " expected>=" + expectedMinLen + " actual=" + payload.length);
        }

        List<LogInfoDto> out = new ArrayList<>(n);

        int off = 1;
        for (int i = 0; i < n; i++) {
            int seq = payload[off] & 0xFF;  off += 1;
            int type = payload[off] & 0xFF; off += 1;

            int yy = payload[off] & 0xFF;
            int mm = payload[off + 1] & 0xFF;
            int dd = payload[off + 2] & 0xFF;
            int ww = payload[off + 3] & 0xFF;
            int hh = payload[off + 4] & 0xFF;
            int mi = payload[off + 5] & 0xFF;
            int ss = payload[off + 6] & 0xFF;
            off += 7;

            LocalDateTime dt = toLocalDateTime(yy, mm, dd, hh, mi, ss);

            out.add(new LogInfoDto(
                    seq, type,
                    yy, mm, dd, ww,
                    hh, mi, ss,
                    dt
            ));
        }

        return out;
    }

    private static LocalDateTime toLocalDateTime(int yy, int mm, int dd,
                                                 int hh, int mi, int ss) {
        int year = 2000 + yy;
        return LocalDateTime.of(
                year,
                clamp(mm, 1, 12),
                clamp(dd, 1, 31),
                clamp(hh, 0, 23),
                clamp(mi, 0, 59),
                clamp(ss, 0, 59)
        );
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
