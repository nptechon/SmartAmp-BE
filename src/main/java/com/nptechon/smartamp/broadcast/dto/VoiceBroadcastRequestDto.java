package com.nptechon.smartamp.broadcast.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceBroadcastRequestDto {
    private int ampId;
    private int repeat;
}
