package com.nptechon.smartamp.tcp.protocol.payload;

import lombok.Getter;

@Getter
public enum AmpPower {
    OFF(0),
    ON(1);

    private final byte value;

    AmpPower(int value) {
        this.value = (byte) value;
    }

    public static AmpPower from(int v) {
        return switch (v) {
            case 0 -> OFF;
            case 1 -> ON;
            default -> throw new IllegalArgumentException(
                    "Invalid AmpPower value: " + v
            );
        };
    }
}