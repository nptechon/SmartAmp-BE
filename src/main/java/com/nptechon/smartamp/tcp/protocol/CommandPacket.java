package com.nptechon.smartamp.tcp.protocol;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommandPacket {
    private int length;
    private int deviceId;
    private byte[] dateTime7;   // Y M D W H M S
    private int opcode;
    private byte[] payload;
}
