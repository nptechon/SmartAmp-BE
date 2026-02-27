package com.nptechon.smartamp.control.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ControlRequestDto {
    private int ampId;
    private String powerCommand; // "ON" or "OFF"
}
