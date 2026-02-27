package com.nptechon.smartamp.broadcast.service;

import com.nptechon.smartamp.broadcast.dto.IndexBroadcastDto;
import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import com.nptechon.smartamp.tcp.util.RepeatValidatorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexBroadcastService {
    private final CommandSender commandSender;

    public IndexBroadcastDto sendAudioIndex(int ampId, int index, int repeat) {
        // 입력 검증
        if (index < 1 || index > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "Index 값은 1~100 사이여야 합니다.");
        }
        if (!RepeatValidatorUtil.isValid(repeat)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "repeat 값은 1~5 또는 255(무한) 이어야 합니다.");
        }

        try {
            boolean ok = commandSender.sendIndex(ampId, index, repeat);
            log.info("인덱스 방송 요청 결과: {}", ok);

            // Busy(false)면 앱에 메시지 내려주기
            if (!ok) {
                throw new CustomException(ErrorCode.DEVICE_BUSY, "현재 방송 중입니다. 잠시 후 다시 시도해주세요.");
            }

            return new IndexBroadcastDto(ampId, index, repeat);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("sending audio index for broadcast failed.. ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "인덱스 음원 방송 중 오류가 발생했습니다.");
        }
    }
}
