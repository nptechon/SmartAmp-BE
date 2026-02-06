package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.protocol.payload.AmpPower;
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
            throw new IllegalStateException("Status response timeout");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
            throw new IllegalStateException("AMP not connected: " + ampId);
        }

        // 3) 0x06 패킷 전송 (payload length 0)
        byte[] dt7 = DateTime7.now();
        byte[] payload = new byte[0];   // payload 없음
        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.AMP_STATUS_REQEUST,
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

    public void sendPower(int ampId, AmpPower power) {
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("AMP not connected: " + ampId);
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


    public void requestLogs(int ampId) {
        Channel ch = sessionManager.get(ampId);
        if (ch == null || !ch.isActive()) throw new IllegalStateException("AMP not connected: " + ampId);

        byte[] dt7 = new byte[7];
        ByteBuf pkt = CommandPacketCodec.encode(ch.alloc(), ampId, dt7, AmpOpcode.LOG_REQUEST, new byte[0]);
        ch.writeAndFlush(pkt);
    }
}
