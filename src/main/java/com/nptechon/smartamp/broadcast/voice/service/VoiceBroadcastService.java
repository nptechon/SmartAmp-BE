package com.nptechon.smartamp.broadcast.voice.service;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.payload.StreamType;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import com.nptechon.smartamp.tcp.server.sender.FileSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

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
    public void sendMp3AsFile512(int ampId, Path mp3Path) {
        try {
            // 1) opcode 0x04 먼저
            commandSender.sendStreamType(ampId, StreamType.MIC);

            // 2) 그 다음 file512 전송
            fileSender.sendMp3File(
                    ampId,
                    mp3Path,
                    (byte) 0x01, // 파일 포맷 (MP3) (Opcode 0x04의 Payload 방송 타입 (Keyword / Mic)과 다름!!)
                    true         // realtime pacing
            );
        } catch (IOException e) {
            throw new CustomException(
                    ErrorCode.DEVICE_OFFLINE, // 또는 COMMAND_FAILED
                    "mp3 file send failed: ampId=" + ampId,
                    e
            );
        }
    }
}
