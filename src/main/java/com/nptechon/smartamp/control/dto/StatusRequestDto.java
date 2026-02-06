package com.nptechon.smartamp.control.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusRequestDto {
    private int ampId;
    private boolean success;
}
