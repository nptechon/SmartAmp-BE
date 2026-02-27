package com.nptechon.smartamp.tcp.server.session;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class TcpSessionManager {

    private static final AttributeKey<Integer> ATTR_DEVICE_ID =
            AttributeKey.valueOf("deviceId");

    private final ConcurrentHashMap<Integer, Channel> channels = new ConcurrentHashMap<>();

    public void bind(int deviceId, Channel ch) {
        ch.attr(ATTR_DEVICE_ID).set(deviceId);
        channels.put(deviceId, ch);
    }

    public void unbind(Channel ch) {
        Integer deviceId = ch.attr(ATTR_DEVICE_ID).get();
        if (deviceId != null) {
            channels.remove(deviceId, ch);
        }
    }

    public Channel get(int deviceId) {
        return channels.get(deviceId);
    }

    /**
     * 해당 deviceId의 TCP 채널을 종료한다.
     * - timeout 등으로 프레임 동기/누적 버퍼를 끊고 싶을 때 사용
     */
    public void close(int deviceId) {
        Channel ch = channels.remove(deviceId); // 먼저 제거 (새 bind와 경합 최소화)
        if (ch == null) return;

        if (ch.isActive()) {
            ch.close().addListener(f -> {
                // close 이후 unbind가 또 호출되어도 remove(key, ch)로 안전
                // (이미 remove 했지만, 혹시 다시 들어온 경우 대비)
                channels.remove(deviceId, ch);
            });
        } else {
            // 이미 비활성이면 맵 정리만
            channels.remove(deviceId, ch);
        }
    }
}
