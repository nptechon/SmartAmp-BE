package com.nptechon.smartamp.global.error;

import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            CustomException e,
            HttpServletRequest request
    ) {
        var ec = e.getErrorCode();
        String requestId = getRequestId();
        String path = request.getRequestURI();

        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(ApiResponse.error(
                        ec.getCode(),
                        e.getMessage(),
                        null,
                        requestId,
                        path
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(
            Exception e,
            HttpServletRequest request
    ) {
        String requestId = getRequestId();
        String path = request.getRequestURI();

        return ResponseEntity
                .status(500)
                .body(ApiResponse.error(
                        "INTERNAL_ERROR",
                        "서버 오류가 발생했습니다.",
                        null,
                        requestId,
                        path
                ));
    }

    private String getRequestId() {
        String rid = MDC.get("requestId");
        return (rid == null || rid.isBlank()) ? "N/A" : rid;
    }
}
