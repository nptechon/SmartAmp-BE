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

    // TTS
    TTS_FAILED(HttpStatus.BAD_GATEWAY, "TTS_FAILED", "TTS 변환에 실패했습니다."),
    TTS_BROADCAST_FAILED(HttpStatus.BAD_GATEWAY, "TTS_BROADCAST_FAILED", "TTS 방송에 실패했습니다."),


    // Protocol / Codec
    PROTOCOL_ENCODE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PROTOCOL_ENCODE_ERROR", "프로토콜 패킷 생성(인코딩)에 실패했습니다."),
    PROTOCOL_DECODE_ERROR(HttpStatus.BAD_REQUEST, "PROTOCOL_DECODE_ERROR", "프로토콜 패킷 해석(디코딩)에 실패했습니다."),
    PROTOCOL_INVALID_FRAME(HttpStatus.BAD_REQUEST, "PROTOCOL_INVALID_FRAME", "유효하지 않은 프로토콜 프레임입니다."),
    PROTOCOL_INVALID_LENGTH(HttpStatus.BAD_REQUEST, "PROTOCOL_INVALID_LENGTH", "프로토콜 길이(LEN)가 유효하지 않습니다."),
    PROTOCOL_INVALID_OPCODE(HttpStatus.BAD_REQUEST, "PROTOCOL_INVALID_OPCODE", "유효하지 않은 Opcode 입니다."),
    PROTOCOL_INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "PROTOCOL_INVALID_PAYLOAD", "프로토콜 Payload 형식이 유효하지 않습니다."),
    PROTOCOL_CRC_MISMATCH(HttpStatus.BAD_REQUEST, "PROTOCOL_CRC_MISMATCH", "프로토콜 CRC 검증에 실패했습니다."),
    PROTOCOL_ETX_MISMATCH(HttpStatus.BAD_REQUEST, "PROTOCOL_ETX_MISMATCH", "프로토콜 ETX 값이 올바르지 않습니다."),
    PROTOCOL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PROTOCOL_TIMEOUT", "프로토콜 응답 대기 시간이 초과되었습니다."),
    PROTOCOL_WRITE_FAILED(HttpStatus.BAD_GATEWAY, "PROTOCOL_WRITE_FAILED", "프로토콜 패킷 전송에 실패했습니다."),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "유효하지 않은 요청/응답입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

}

