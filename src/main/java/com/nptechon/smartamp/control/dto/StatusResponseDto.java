package com.nptechon.smartamp.control.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusResponseDto {
    private int ampId;
    private String status;
}
