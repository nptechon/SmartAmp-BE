package com.nptechon.smartamp.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    OK(HttpStatus.OK, "OK", "OK"),

    // Amp Power Control
    AMP_NOT_FOUND(HttpStatus.NOT_FOUND, "AMP_NOT_FOUND", "Amp를 찾을 수 없습니다."),
    DEVICE_OFFLINE(HttpStatus.SERVICE_UNAVAILABLE, "DEVICE_OFFLINE", "Amp가 오프라인 상태입니다."),
    DEVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "DEVICE_TIMEOUT", "Amp 응답이 지연되었습니다."),
    COMMAND_FAILED(HttpStatus.BAD_GATEWAY, "COMMAND_FAILED", "Amp 제어 명령 처리에 실패했습니다."),

    // Voice upload
    VOICE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "VOICE_FILE_EMPTY", "음성 파일이 비어 있습니다."),
    VOICE_CONVERT_BUSY(HttpStatus.TOO_MANY_REQUESTS, "VOICE_CONVERT_BUSY", "음성 변환 중입니다. 잠시 후 다시 시도해주세요."),
    VOICE_CONVERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "VOICE_CONVERT_FAILED", "음성 변환에 실패했습니다."),
    VOICE_UPLOAD_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, "VOICE_UPLOAD_INTERRUPTED", "음성 처리 중 인터럽트가 발생했습니다."),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "유효하지 않은 요청/응답입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

}

