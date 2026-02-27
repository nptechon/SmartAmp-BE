package com.nptechon.smartamp.global;

import lombok.*;
import java.time.OffsetDateTime;

@Getter @Builder
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String requestId;
    private String path;
    private OffsetDateTime timestamp;

    public static <T> ApiResponse<T> ok(String code, String message, T data, String requestId, String path) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .requestId(requestId)
                .path(path)
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, T data, String requestId, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(data)
                .requestId(requestId)
                .path(path)
                .timestamp(OffsetDateTime.now())
                .build();
    }
}
