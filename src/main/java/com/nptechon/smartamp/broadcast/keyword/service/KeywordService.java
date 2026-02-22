package com.nptechon.smartamp.broadcast.keyword.service;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import com.nptechon.smartamp.broadcast.keyword.dto.KeywordBroadcastDto;
import com.nptechon.smartamp.broadcast.voice.service.VoiceBroadcastService;
import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.util.RepeatValidatorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordService {

    private final TextToSpeechClient ttsClient;              // Config에서 만든 Bean 주입
    private final VoiceBroadcastService voiceBroadcastService;

    public KeywordBroadcastDto broadcastTts(int ampId, String content, int repeat) {
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "content가 비어있습니다.");
        }

        if (!RepeatValidatorUtil.isValid(repeat)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "repeat 값은 1~5 또는 255(무한) 이어야 합니다.");
        }

        Path mp3Path = null;
        try {
            mp3Path = synthesizeToMp3File(content);

            voiceBroadcastService.sendMp3AsFile512(ampId, mp3Path, repeat);

            return new KeywordBroadcastDto(ampId, content, repeat);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Keyword TTS broadcast failed", e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 방송 실패");
        } finally {
            if (mp3Path != null) {
                try { Files.deleteIfExists(mp3Path); } catch (Exception ignore) {}
            }
        }
    }

    private Path synthesizeToMp3File(String text) throws Exception {
        // 텍스트 입력
        SynthesisInput input = SynthesisInput.newBuilder()
                .setText(text)
                .build();

        // 보이스 선택 (한국어 예시)
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("ko-KR")
                // 필요하면 voiceName 지정 가능: "ko-KR-Neural2-A" 등
                // .setName("ko-KR-Neural2-A")
                .build();

        // MP3 출력
        AudioConfig audioConfig = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .build();

        SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);
        ByteString audioContents = response.getAudioContent();

        if (audioContents.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 결과가 비어있습니다.");
        }

        Path tmp = Files.createTempFile("keyword-tts-", ".mp3");
        Files.write(tmp, audioContents.toByteArray());
        return tmp;
    }
}
