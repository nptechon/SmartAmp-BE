package com.nptechon.smartamp.tcp.server.session;
import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TcpSessionManager {

    private static final AttributeKey<Integer> ATTR_DEVICE_ID =
            AttributeKey.valueOf("deviceId");

    // heartbeat task 핸들 저장용
    private static final AttributeKey<ScheduledFuture<?>> ATTR_HEARTBEAT =
            AttributeKey.valueOf("heartbeatFuture");

    private final ConcurrentHashMap<Integer, Channel> channels = new ConcurrentHashMap<>();

    // NAT idle timeout 방지용 (30초 권장: LTE/공유기 환경에서 안전)
    private static final long HEARTBEAT_PERIOD_SEC = 30;

    public void bind(int deviceId, Channel ch) {
        ch.attr(ATTR_DEVICE_ID).set(deviceId);
        channels.put(deviceId, ch);

        // 기존 heartbeat가 있으면 정리 후 재등록(재연결 케이스)
        stopHeartbeat(ch);

        startHeartbeat(deviceId, ch);

        log.info("[TCP][SESSION] bind deviceId={} ch={}", deviceId, ch.id());
    }

    public void unbind(Channel ch) {
        stopHeartbeat(ch);

        Integer deviceId = ch.attr(ATTR_DEVICE_ID).get();
        if (deviceId != null) {
            channels.remove(deviceId, ch);
            log.info("[TCP][SESSION] unbind deviceId={} ch={}", deviceId, ch.id());
        }
    }

    public Channel get(int deviceId) {
        return channels.get(deviceId);
    }

    // 외부에서 강제 close 하고 싶을 때
    public void close(int deviceId) {
        Channel ch = channels.get(deviceId);
        if (ch != null) {
            stopHeartbeat(ch);
            ch.close();
            channels.remove(deviceId, ch);
            log.warn("[TCP][SESSION] close deviceId={} ch={}", deviceId, ch.id());
        }
    }

    private void startHeartbeat(int deviceId, Channel ch) {
        // eventLoop에서 주기 실행 (Netty 스레드 안정성 OK)
        ScheduledFuture<?> f = ch.eventLoop().scheduleAtFixedRate(() -> {
            if (!ch.isActive()) {
                // 채널 죽었으면 끊고 정리
                log.warn("[TCP][HB] channel inactive -> stop heartbeat deviceId={} ch={}", deviceId, ch.id());
                stopHeartbeat(ch);
                return;
            }

            // Heartbeat는 "응답 기다리지 않는" fire-and-forget
            ByteBuf ping = CommandPacketCodec.encode(
                    ch.alloc(),
                    deviceId,
                    DateTime7.now(),
                    AmpOpcode.AMP_STATUS_REQUEST, // 0x06
                    new byte[0]
            );

            ch.writeAndFlush(ping).addListener(fut -> {
                if (!fut.isSuccess()) {
                    log.warn("[TCP][HB] write failed -> close deviceId={} cause={}", deviceId, fut.cause().toString());
                    // write 실패면 채널 상태가 이미 안 좋을 확률 높음
                    ch.close();
                } else {
                    log.debug("[TCP][HB] ping sent deviceId={}", deviceId);
                }
            });

        }, HEARTBEAT_PERIOD_SEC, HEARTBEAT_PERIOD_SEC, TimeUnit.SECONDS);

        ch.attr(ATTR_HEARTBEAT).set(f);
        log.info("[TCP][HB] started deviceId={} period={}s", deviceId, HEARTBEAT_PERIOD_SEC);
    }

    private void stopHeartbeat(Channel ch) {
        ScheduledFuture<?> f = ch.attr(ATTR_HEARTBEAT).getAndSet(null);
        if (f != null) {
            f.cancel(false);
            log.info("[TCP][HB] stopped ch={}", ch.id());
        }
    }

    public Integer getBoundDeviceId(Channel ch) {
        return ch.attr(ATTR_DEVICE_ID).get();
    }
}
