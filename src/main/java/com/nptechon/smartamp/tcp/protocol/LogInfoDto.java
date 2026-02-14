package com.nptechon.smartamp.tcp.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class LogInfoDto {

    private final int seq;          // 0~99
    private final int commandType;  // 1~6
    private final int yy;
    private final int mm;
    private final int dd;
    private final int ww;
    private final int hh;
    private final int mi;
    private final int ss;
    private final LocalDateTime dateTime;

    public String getCommandTypeName() {
        return switch (commandType) {
            case 1 -> "AMP";
            case 2 -> "SENTENCE";
            case 3 -> "KEYWORD";
            case 4 -> "MIC";
            case 5 -> "REGISTER";
            case 6 -> "LOG_REQUEST";
            default -> "UNKNOWN";
        };
    }
}
