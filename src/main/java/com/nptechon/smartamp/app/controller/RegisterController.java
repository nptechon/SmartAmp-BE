package com.nptechon.smartamp.app.controller;

import com.nptechon.smartamp.app.dto.RegisterRequestDto;
import com.nptechon.smartamp.app.dto.RegisterResultDto;
import com.nptechon.smartamp.app.service.RegisterService;
import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/register")
public class RegisterController {

    private final RegisterService registerService;

    @PostMapping("/app")
    public ResponseEntity<ApiResponse<RegisterResultDto>> registerApp(@RequestBody RegisterRequestDto dto, HttpServletRequest request) {
        RegisterResultDto result = registerService.verifyRegisterPassword(dto);

        if (!result.isSuccess()) {
            // 실패도 200으로 내려도 되고, 401/403으로 내려도 됨.
            // 지금 프로젝트 ApiResponse 스타일 유지하려면 200 + code/message로 구분하는게 깔끔함.
            return ResponseEntity.ok(
                    ApiResponse.ok(
                            "fail",
                            result.getMessage(),
                            result,
                            request.getRequestId(),
                            request.getRequestURI()
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "ok",
                        "앱 최초 등록 성공",
                        result,
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }
}