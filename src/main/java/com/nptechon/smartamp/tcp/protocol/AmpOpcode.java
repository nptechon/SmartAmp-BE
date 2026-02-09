package com.nptechon.smartamp.tcp.protocol;

import lombok.Getter;

@Getter
public enum AmpOpcode {

    // Device Register
    DEVICE_REGISTER(0x01),
    DEVICE_REGISTER_ACK(0x81),

    // Amp Control
    AMP_CONTROL(0x02),          // payload: 1(On) / 0(Off)
    AMP_STATUS_REQUEST(0x06),
    AMP_STATUS_RESPONSE(0x86),  // payload: 1(On) / 0(Off)

    // Broadcast
    PLAY_INDEX_PREDEFINED(0x03),    // payload: sentence index
    STREAM_TYPE(0x04),              // payload: 1=keyword, 2=mic

    // Log
    LOG_REQUEST(0x05),
    LOG_RESPONSE(0x85);

    private final byte code;

    AmpOpcode(int code) {
        this.code = (byte) code;
    }

    public static AmpOpcode from(byte code) {
        for (AmpOpcode op : values()) {
            if (op.code == code) return op;
        }
        throw new IllegalArgumentException(
                String.format("Unknown opcode: 0x%02X", code)
        );
    }
}
