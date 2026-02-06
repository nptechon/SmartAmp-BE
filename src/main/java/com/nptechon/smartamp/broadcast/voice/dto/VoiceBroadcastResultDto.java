package com.nptechon.smartamp.broadcast.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

@Getter
@AllArgsConstructor
public class VoiceBroadcastResultDto {
    private final int ampId;
    private final String savedAs;
    private final long size;
    private final long tookMs;
    private final int formatCode;
    private final Path mp3Path;
}