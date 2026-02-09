package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.protocol.payload.AmpPower;
import com.nptechon.smartamp.tcp.protocol.payload.StreamType;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class CommandSender {

    private final TcpSessionManager sessionManager;
    private final Map<Integer, CompletableFuture<String>> pendingStatus = new ConcurrentHashMap<>();

    public String getStatus(int ampId) {
        try {
            return getStatusAsync(ampId).get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 앰프 상태 가져오기
     * @param ampId
     * @return
     */
    public CompletableFuture<String> getStatusAsync(int ampId) {
        // 1) 중복 요청 방지(선택)
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> prev = pendingStatus.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            // 이미 대기 중이면 정책 선택: 기존 future 반환 / 예외 / 교체
            return prev;
        }

        // 2) 세션 확인
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        }

        // 3) 0x06 패킷 전송 (payload length 0)
        byte[] dt7 = DateTime7.now();
        byte[] payload = new byte[0];   // payload 없음
        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.AMP_STATUS_REQUEST,
                payload

        );
        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<String> p = pendingStatus.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                    }
                });

        // 4) 타임아웃 처리 (완전 중요)
        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> pendingStatus.remove(ampId));

        return future;
    }

    // InboundHandler에서 호출될 완료 함수
    public void completeStatus(int ampId, String status) {
        CompletableFuture<String> f = pendingStatus.remove(ampId);
        if (f != null) f.complete(status);
        // f == null 이면 늦게 온 응답/대기 없음 -> 로그만
    }

    public void completeStatusExceptionally(int ampId, Throwable t) {
        CompletableFuture<String> f = pendingStatus.remove(ampId);
        if (f != null) f.completeExceptionally(t);
    }

    /**
     * 앰프 전원 제어
     * @param ampId
     * @param power
     */
    public void sendPower(int ampId, AmpPower power) {
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        }

        byte[] dt7 = DateTime7.now();

        // payload는 1바이트짜리 배열
        byte[] payload = new byte[] { power.getValue() };

        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.AMP_CONTROL,
                payload
        );

        channel.writeAndFlush(packet);
    }

    /**
     * 인덱스로 앰프에 저장된 음원 방송 시
     * @param ampId
     * @param index
     */
    public void sendIndex(int ampId, int index) {
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        }
        byte[] dt7 = DateTime7.now();
        byte indexByte = (byte) index;

        byte[] payload = new byte[] { indexByte };

        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.PLAY_INDEX_PREDEFINED,
                payload
        );

        channel.writeAndFlush(packet);
    }


    /**
     * 스트림 패킷 보내기 전 선언
     * @param ampId
     * @param type
     */
    public void sendStreamType(int ampId, StreamType type) {
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        }

        byte[] dt7 = DateTime7.now();

        // payload 1바이트: 1=KEYWORD, 2=MIC
        byte[] payload = new byte[] { type.code() };

        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.STREAM_TYPE,   // 0x04
                payload
        );

        channel.writeAndFlush(packet);
    }

    // enum 을 못넘기는 경우
    public void sendStreamType(int ampId, byte streamTypeCode) {
        StreamType type = switch (streamTypeCode) {
            case 1 -> StreamType.KEYWORD;
            case 2 -> StreamType.MIC;
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST, "streamTypeCode가 유효하지 않습니다.");
        };
        sendStreamType(ampId, type);
    }

    public void requestLogs(int ampId) {
        Channel ch = sessionManager.get(ampId);
        if (ch == null || !ch.isActive()) throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");

        byte[] dt7 = new byte[7];
        ByteBuf pkt = CommandPacketCodec.encode(ch.alloc(), ampId, dt7, AmpOpcode.LOG_REQUEST, new byte[0]);
        ch.writeAndFlush(pkt);
    }
}
