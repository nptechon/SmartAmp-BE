package com.nptechon.smartamp.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterResultDto {
    private boolean success;
    private String message;      // 실패 사유(성공이면 성공 메시지)
}