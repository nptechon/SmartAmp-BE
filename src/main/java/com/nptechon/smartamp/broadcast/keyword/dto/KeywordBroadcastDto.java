package com.nptechon.smartamp.broadcast.keyword.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KeywordBroadcastDto {
    private int ampId;
    private String content;
    private int repeat;
}
