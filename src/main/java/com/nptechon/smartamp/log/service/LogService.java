package com.nptechon.smartamp.log.service;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.LogInfoDto;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CommandSender commandSender;

    public List<LogInfoDto> getRecentLogs(int ampId, int days) {
        validateDays(days);

        try {
            List<LogInfoDto> all = commandSender.getLogs(ampId);

            LocalDateTime now = LocalDateTime.now(KST);
            LocalDateTime from = now.minusDays(days);

            List<LogInfoDto> filtered = all.stream()
                    .filter(li -> li.getDateTime() != null)
                    .filter(li -> !li.getDateTime().isBefore(from) && !li.getDateTime().isAfter(now))
                    .sorted(Comparator.comparing(LogInfoDto::getDateTime).reversed())
                    .toList();

            log.info("[LOG] ampId={} days={} total={} filtered={}", ampId, days, all.size(), filtered.size());
            return filtered;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LOG] getRecentLogs failed ampId={} days={}", ampId, days, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "로그 조회 중 오류가 발생했습니다.");
        }
    }

    private void validateDays(int days) {
        // 정책: 1~30만 허용 (원하면 90까지 확장 가능)
        if (days < 1 || days > 30) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "days는 1~30 범위만 가능합니다.");
        }
    }
}
