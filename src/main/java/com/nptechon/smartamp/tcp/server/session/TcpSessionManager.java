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
}
