package com.nptechon.smartamp.log.controller;

import com.nptechon.smartamp.global.ApiResponse;
import com.nptechon.smartamp.log.dto.LogResponseDto;
import com.nptechon.smartamp.log.service.LogService;
import com.nptechon.smartamp.tcp.protocol.LogInfoDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/log")
public class LogController {

    private final LogService logService;

    @GetMapping("/recent")
    public ApiResponse<LogResponseDto> getRecentLogs(
            @RequestParam int ampId,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request
    ) {
        String requestId = (String) request.getAttribute("requestId");
        String requestURI = request.getRequestURI();

        List<LogInfoDto> logs = logService.getRecentLogs(ampId, days);
        LogResponseDto result = new LogResponseDto(ampId, days, logs.size(), logs);

        return ApiResponse.ok(
                "ok",
                "최근 " + days + "일 로그 조회 성공",
                result,
                requestId,
                requestURI
        );
    }
}
