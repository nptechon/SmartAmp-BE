package com.nptechon.smartamp.control.controller;

import com.nptechon.smartamp.control.service.AmpService;
import com.nptechon.smartamp.control.dto.ControlRequestDto;
import com.nptechon.smartamp.control.dto.ControlResponseDto;
import com.nptechon.smartamp.control.dto.StatusResponseDto;
import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/amp")
public class AmpController {
    private final AmpService ampService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<StatusResponseDto>> getStatus(@RequestParam int ampId, HttpServletRequest request) {

        StatusResponseDto response = ampService.getStatus(ampId);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "ok",
                        "앰프 전원 상태 가져오기 성공",
                        response,
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }

    @PostMapping("/control")
    public ResponseEntity<ApiResponse<ControlResponseDto>> setPower(@RequestBody ControlRequestDto requestDto, HttpServletRequest request) {

        ControlResponseDto response = ampService.setPower(requestDto.getAmpId(), requestDto.getPowerCommand());

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "OK",
                        "전원 제어 요청 성공",
                        response,                 // DTO 그대로
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }

}
