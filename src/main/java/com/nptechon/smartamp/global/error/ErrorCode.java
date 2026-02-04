package com.nptechon.smartamp.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    OK(HttpStatus.OK, "OK", "OK"),

    AMP_NOT_FOUND(HttpStatus.NOT_FOUND, "AMP_NOT_FOUND", "Amp를 찾을 수 없습니다."),
    DEVICE_OFFLINE(HttpStatus.SERVICE_UNAVAILABLE, "DEVICE_OFFLINE", "Amp가 오프라인 상태입니다."),
    DEVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "DEVICE_TIMEOUT", "Amp 응답이 지연되었습니다."),
    COMMAND_FAILED(HttpStatus.BAD_GATEWAY, "COMMAND_FAILED", "Amp 제어 명령 처리에 실패했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "유효하지 않은 응답입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

}

