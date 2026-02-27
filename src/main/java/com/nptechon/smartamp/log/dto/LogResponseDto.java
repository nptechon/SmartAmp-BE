package com.nptechon.smartamp.log.dto;

import com.nptechon.smartamp.tcp.protocol.LogInfoDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class LogResponseDto {
    private int ampId;
    private int days; // 7 고정
    private int total;
    private List<LogInfoDto> logs;
}