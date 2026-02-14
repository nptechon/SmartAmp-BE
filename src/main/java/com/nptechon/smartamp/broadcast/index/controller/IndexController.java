package com.nptechon.smartamp.broadcast.index.controller;

import com.nptechon.smartamp.broadcast.index.dto.IndexBroadcastDto;
import com.nptechon.smartamp.broadcast.index.service.IndexBroadcastService;
import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/audio")
public class IndexController {
    private final IndexBroadcastService indexBroadcastService;

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<IndexBroadcastDto>> upload(@RequestBody IndexBroadcastDto dto, HttpServletRequest request) {
        log.info("AmpID: {}, Index: {}", dto.getAmpId(), dto.getIndex());

        IndexBroadcastDto result = indexBroadcastService.sendAudioIndex(dto.getAmpId(), dto.getIndex(), dto.getRepeat());

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "ok",
                        "앰프 인덱스 방송 성공",
                        result,
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }

}
