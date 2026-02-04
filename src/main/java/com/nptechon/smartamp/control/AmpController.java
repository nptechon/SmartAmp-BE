package com.nptechon.smartamp.control;

import com.nptechon.smartamp.control.dto.ControlRequestDto;
import com.nptechon.smartamp.control.dto.ControlResponseDto;
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
