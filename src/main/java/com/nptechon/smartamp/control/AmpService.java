package com.nptechon.smartamp.control;

import com.nptechon.smartamp.control.dto.ControlResponseDto;
import com.nptechon.smartamp.control.dto.StatusResponseDto;
import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.payload.AmpPower;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmpService {

    private final CommandSender commandSender;

    public StatusResponseDto getStatus(int ampId) {
        try {
            // 여기서 0x86 payload 를 기다렸다가 1(ON)/0(OFF) 를 받음
            // payload[0] = 1 → AMP ON
            // payload[0] = 0 → AMP OFF
            String status = commandSender.getStatus(ampId);
            log.info("앰프 상태: {}", status);


            return new StatusResponseDto(ampId, status);
        } catch (IllegalStateException e) {
            // AmpTcpSender에서 "AMP not connected" 같은 예외 던지게 해둔 경우
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        } catch (Exception e) {
            log.error("amp status request failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "전원 제어 중 오류가 발생했습니다.");
        }

    }

    public ControlResponseDto setPower(int ampId, String powerRaw) {

        String power = normalize(powerRaw);
        AmpPower command = switch (power) {
            case "ON", "1" -> AmpPower.ON;
            case "OFF", "0" -> AmpPower.OFF;
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST, "power 값은 ON 또는 OFF 여야 합니다.");
        };

        // 1) 연결 여부 확인 + 2) 명령 전송 (0x02 payload: 1/0)
        try {
            commandSender.sendPower(ampId, command);
        } catch (IllegalStateException e) {
            // AmpTcpSender에서 "AMP not connected" 같은 예외 던지게 해둔 경우
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        } catch (Exception e) {
            log.error("power control failed.. ampId={} on={}", ampId, command, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "전원 제어 중 오류가 발생했습니다.");
        }

        return new ControlResponseDto(ampId, power);
    }


    private String normalize(String powerRaw) {
        if (powerRaw == null) return "";
        return powerRaw.trim().toUpperCase();
    }
}
