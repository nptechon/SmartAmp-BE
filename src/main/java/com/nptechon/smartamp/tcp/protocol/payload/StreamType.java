package com.nptechon.smartamp.tcp.protocol.payload;

public enum StreamType {
    KEYWORD((byte)1),
    MIC((byte)2);

    private final byte code;
    StreamType(byte code) { this.code = code; }
    public byte code() { return code; }
}
