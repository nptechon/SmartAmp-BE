package com.nptechon.smartamp.broadcast.voice.service;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.payload.StreamType;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import com.nptechon.smartamp.tcp.server.sender.FileSender;
import com.nptechon.smartamp.tcp.util.RepeatValidatorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceBroadcastService {

    private final FileSender fileSender;
    private final CommandSender commandSender;

    /**
     * 512 프레임 파일 전송
     * - FS(payload=파일메타: totalSize LE4 + formatCode + fileName)
     * - FD(payload=508 bytes)
     * - FE
     */
    public void sendMp3AsFile512(int ampId, Path mp3Path, StreamType streamType, int repeat) {
        try {
            if (!RepeatValidatorUtil.isValid(repeat)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST, "repeat 값은 1~5 또는 255(무한) 이어야 합니다.");
            }

            // 1) opcode 0x04 먼저
            boolean ok = commandSender.sendStreamType(ampId, streamType, repeat);
            log.info("음성 파일 Type 전송 결과: {}", ok);

            // 앰프의 응답이 Busy(payload=1)면 여기서 끊고 앱에 메시지 내려주기
            if (!ok) {
                throw new CustomException(ErrorCode.DEVICE_BUSY, "현재 방송 중입니다. 잠시 후 다시 시도해주세요.");
            }

            // 2) OK일 때만 file512 전송
            fileSender.sendMp3File(
                    ampId,
                    mp3Path,
                    (byte) 0x01, // MP3
                    true
            );
        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "현재 디바이스가 오프라인 상태입니다.");
        }
    }
}
