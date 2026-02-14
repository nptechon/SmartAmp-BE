package com.nptechon.smartamp.broadcast.voice.controller;

import com.nptechon.smartamp.broadcast.voice.dto.VoiceBroadcastRequestDto;
import com.nptechon.smartamp.broadcast.voice.dto.VoiceBroadcastResultDto;
import com.nptechon.smartamp.broadcast.voice.service.VoiceConvertService;
import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/voice")
public class VoiceController {

    private final VoiceConvertService voiceConvertService;

    @PostMapping(
            value = "/broadcast",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<VoiceBroadcastResultDto>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("dto") VoiceBroadcastRequestDto dto,
            HttpServletRequest request
    ) {
        VoiceBroadcastResultDto result = voiceConvertService.uploadAndBroadcast(file, dto.getAmpId(), dto.getRepeat());

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "ok",
                        "음성 직접 방송 성공",
                        result,
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }
}
