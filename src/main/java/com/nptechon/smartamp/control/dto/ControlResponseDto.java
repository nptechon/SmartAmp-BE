package com.nptechon.smartamp.control.dto;

import lombok.*;

@Getter
@AllArgsConstructor
public class ControlResponseDto {
    private int ampId;
    private String powerCommand; // "ON" / "OFF"
}
