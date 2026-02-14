package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.protocol.LogInfoDto;
import com.nptechon.smartamp.tcp.protocol.payload.AmpPower;
import com.nptechon.smartamp.tcp.protocol.payload.StreamType;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandSender {

    private final TcpSessionManager sessionManager;

    // pending Future 맵 ... CompletableFuture(대기 중인 요청) 보관
    // Boolean 의미: true → OK, false → Busy
    private final Map<Integer, CompletableFuture<Boolean>> pendingStatus = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingControl = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingBroadcast = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingStream = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<List<LogInfoDto>>> pendingLog = new ConcurrentHashMap<>();

    /**
     * 앰프 상태 가져오기
     * @param ampId
     * @return
     */
    public boolean getStatus(int ampId) {
        try {
            boolean result = getStatusAsync(ampId).get(5, TimeUnit.SECONDS);
            log.info("[TCP][STATUS] response sync ampId={} isOn={}", ampId, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][STATUS] timeout ampId={}", ampId);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (Exception e) {
            log.error("[TCP][STATUS] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<Boolean> getStatusAsync(int ampId) {
        // 1) 중복 요청 방지(선택)
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture<Boolean> prev = pendingStatus.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            log.info("[TCP][STATUS] already pending -> reuse future ampId={}", ampId);
            return prev;
        }

        // 2) 세션 확인
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingStatus.remove(ampId, future);
            log.warn("[TCP][STATUS] offline ampId={} channel={}", ampId, channel);
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

        log.info("---> [TCP][STATUS] send packet ampId={} opcode=0x06", ampId);
        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<Boolean> p = pendingStatus.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                        log.error("[TCP][STATUS] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][STATUS] write success ampId={}", ampId);
                    }
                });

        // 4) 타임아웃 처리
        future.orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingStatus.remove(ampId);
                    if (ex != null) log.warn("[TCP][STATUS] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][STATUS] future completed ampId={} isOn={}", ampId, r);
                });

        return future;
    }

    // InboundHandler 에서 호출될 완료 함수
    public void completeStatus(int ampId, Boolean isOn) {
        CompletableFuture<Boolean> f = pendingStatus.remove(ampId);
        if (f != null) {
            f.complete(isOn);
            log.info("[TCP][STATUS] complete ampId={} isOn={}", ampId, isOn);
        } else {
            // f == null 이면 늦게 온 응답/대기 없음 -> 로그만
            log.warn("[TCP][STATUS] complete ignored (no pending) ampId={} isOn={}", ampId, isOn);
        }
    }

    public void completeStatusExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingStatus.remove(ampId);
        if (f != null) {
            f.completeExceptionally(t);
            log.warn("[TCP][STATUS] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][STATUS] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }


    /**
     * 앰프 전원 제어
     * @param ampId
     * @param power
     */
    public boolean sendPower(int ampId, AmpPower power) {
        try {
            log.info("[TCP][CONTROL] request sync ampId={} power={}", ampId, power);

            // [API 레벨] 동기 호출.. 비동기 요청 sendPowerAsync()를 호출하고 .get()으로 응답이 올 때까지 대기(블로킹)함
            // future.get() --> 현재 스레드가 멈춤(block)
            boolean result = sendPowerAsync(ampId, power).get(3, TimeUnit.SECONDS);
            log.info("[TCP][CONTROL] response sync ampId={} isOn={}", ampId, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][CONTROL] timeout ampId={} power={}", ampId, power);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (Exception e) {
            log.error("[TCP][CONTROL] failed ampId={} power={}", ampId, power, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // [내부] 비동기 이벤트 기반 처리!!
    // CompletableFuture<Boolean> 반환.. TCP 패킷을 Netty 가 전송.. 아직 응답은 안 옴
    public CompletableFuture<Boolean> sendPowerAsync(int ampId, AmpPower power) {
        // 앰프의 응답이 오면 넣어둘 기다릴 상자 만들기..
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture<Boolean> prev = pendingControl.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            log.info("[TCP][CONTROL] already pending -> reuse future ampId={}", ampId);
            return prev;
        }

        // 연결된 채널 확인
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingControl.remove(ampId, future);
            log.warn("[TCP][CONTROL] offline ampId={} channel={}", ampId, channel);
            throw new CustomException(ErrorCode.DEVICE_OFFLINE);
        }

        // payload 값 생성 - ON(1), OFF(0)
        byte[] payload = new byte[] { power.getValue() };

        // 앰프로 보낼 패킷 생성
        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                DateTime7.now(),
                AmpOpcode.AMP_CONTROL, // 0x02
                payload
        );

        log.info("---> [TCP][CONTROL] send packet ampId={} opcode=0x02 payload={}", ampId, power.getValue());

        // 앰프로 패킷 전송
        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<Boolean> p = pendingControl.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                        log.error("[TCP][CONTROL] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][CONTROL] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingControl.remove(ampId);
                    if (ex != null) log.warn("[TCP][CONTROL] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][CONTROL] future completed ampId={} isOn={}", ampId, r);
                });

        return future;
    }

    // 이후 TCP 응답은 Netty EventLoop 스레드에서 비동기로 도착
    // future.complete --> .get()으로 기다리던 스레드가 깨어남 --> sendPower() return --> HTTP 응답 반환
    public void completeControl(int ampId, boolean isOn) {
        // 1) ampId 키에 해당하는 엔트리를 맵에서 삭제
        // 2) 삭제된 값(CompletableFuture)을 반환
        CompletableFuture<Boolean> f = pendingControl.remove(ampId);
        if (f != null) {
            f.complete(isOn);
            log.info("[TCP][CONTROL] complete ampId={} isOn={}", ampId, isOn);
        } else {
            log.warn("[TCP][CONTROL] complete ignored (no pending) ampId={} isOn={}", ampId, isOn);
        }
    }

    public void completeControlExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingControl.remove(ampId);
        if (f != null) {
            f.completeExceptionally(t);
            log.warn("[TCP][CONTROL] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][CONTROL] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }


    /**
     * 인덱스로 앰프에 저장된 음원 방송 시
     * @param ampId
     * @param index
     */
    public boolean sendIndex(int ampId, int index, int repeat) {
        try {
            log.info("[TCP][BROADCAST] request sync ampId={} index={} repeat={}", ampId, index, repeat);
            boolean result = sendIndexAsync(ampId, index, repeat).get(3, TimeUnit.SECONDS);
            log.info("[TCP][BROADCAST] response sync ampId={} ok={}", ampId, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][BROADCAST] timeout ampId={} index={} repeat={}", ampId, index, repeat);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (Exception e) {
            log.error("[TCP][BROADCAST] failed ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<Boolean> sendIndexAsync(int ampId, int index, int repeat) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture<Boolean> prev = pendingBroadcast.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            log.info("[TCP][BROADCAST] already pending -> reuse future ampId={}", ampId);
            return prev;
        }

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingBroadcast.remove(ampId, future);
            log.warn("[TCP][BROADCAST] offline ampId={} channel={}", ampId, channel);
            throw new CustomException(ErrorCode.DEVICE_OFFLINE);
        }

        byte[] payload = new byte[] {
                (byte) index,
                (byte) repeat   // 1~5, 0xFF(무한 반복)
        };

        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                DateTime7.now(),
                AmpOpcode.PLAY_INDEX_PREDEFINED, // 0x03
                payload
        );

        log.info("---> [TCP][BROADCAST] send packet ampId={} opcode=0x03 index={} repeat={}", ampId, index, repeat);
        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<Boolean> p = pendingBroadcast.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                        log.error("[TCP][BROADCAST] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][BROADCAST] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingBroadcast.remove(ampId);
                    if (ex != null) log.warn("[TCP][BROADCAST] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][BROADCAST] future completed ampId={} ok={}", ampId, r);
                });

        return future;
    }

    public void completeBroadcast(int ampId, boolean ok) {
        CompletableFuture<Boolean> f = pendingBroadcast.remove(ampId);
        if (f != null) {
            f.complete(ok);
            log.info("[TCP][BROADCAST] complete ampId={} ok={}", ampId, ok);
        } else {
            log.warn("[TCP][BROADCAST] complete ignored (no pending) ampId={} ok={}", ampId, ok);
        }
    }

    public void completeBroadcastExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingBroadcast.remove(ampId);
        if (f != null) {
            f.completeExceptionally(t);
            log.warn("[TCP][BROADCAST] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][BROADCAST] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }


    /**
     * 스트림 패킷 보내기 전 선언
     * @param ampId
     * @param type
     */
    public boolean sendStreamType(int ampId, StreamType type, int repeat) {
        try {
            log.info("[TCP][STREAM] request sync ampId={} type={} repeat={}", ampId, type, repeat);
            boolean result = sendStreamTypeAsync(ampId, type, repeat).get(3, TimeUnit.SECONDS);
            log.info("[TCP][STREAM] response sync ampId={} ok={}", ampId, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][STREAM] timeout ampId={} type={} repeat={}", ampId, type, repeat);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (Exception e) {
            log.error("[TCP][STREAM] failed ampId={} type={} repeat={}", ampId, type, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<Boolean> sendStreamTypeAsync(int ampId, StreamType type, int repeat) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture<Boolean> prev = pendingStream.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            log.info("[TCP][STREAM] already pending -> reuse future ampId={}", ampId);
            return prev;
        }

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingStream.remove(ampId, future);
            log.warn("[TCP][STREAM] offline ampId={} channel={}", ampId, channel);
            throw new CustomException(ErrorCode.DEVICE_OFFLINE);
        }

        byte[] payload = new byte[] {
                type.code(),
                (byte) repeat
        };

        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                DateTime7.now(),
                AmpOpcode.STREAM_TYPE, // 0x04
                payload
        );

        log.info("---> [TCP][STREAM] send packet ampId={} opcode=0x04 type={} repeat={}", ampId, type, repeat);

        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<Boolean> p = pendingStream.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                        log.error("[TCP][STREAM] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][STREAM] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingStream.remove(ampId);
                    if (ex != null) log.warn("[TCP][STREAM] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][STREAM] future completed ampId={} ok={}", ampId, r);
                });

        return future;
    }

    public void completeStream(int ampId, boolean ok) {
        CompletableFuture<Boolean> f = pendingStream.remove(ampId);
        if (f != null) {
            f.complete(ok);
            log.info("[TCP][STREAM] complete ampId={} ok={}", ampId, ok);
        } else {
            log.warn("[TCP][STREAM] complete ignored (no pending) ampId={} ok={}", ampId, ok);
        }
    }

    public void completeStreamExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingStream.remove(ampId);
        if (f != null) {
            f.completeExceptionally(t);
            log.warn("[TCP][STREAM] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][STREAM] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

    /**
     * 로그 조회 (0x05 요청 -> 0x85 응답)
     */
    public List<LogInfoDto> getLogs(int ampId) {
        try {
            log.info("[TCP][LOG] request sync ampId={}", ampId);
            List<LogInfoDto> result = getLogsAsync(ampId).get(15, TimeUnit.SECONDS);
            log.info("[TCP][LOG] response sync ampId={} total={}", ampId, result == null ? 0 : result.size());
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][LOG] timeout ampId={}", ampId);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TCP][LOG] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<List<LogInfoDto>> getLogsAsync(int ampId) {
        CompletableFuture<List<LogInfoDto>> future = new CompletableFuture<>();
        CompletableFuture<List<LogInfoDto>> prev = pendingLog.putIfAbsent(ampId, future);
        if (prev != null && !prev.isDone()) {
            log.info("[TCP][LOG] already pending -> reuse future ampId={}", ampId);
            return prev;
        }

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingLog.remove(ampId, future);
            log.warn("[TCP][LOG] offline ampId={} channel={}", ampId, channel);
            throw new CustomException(ErrorCode.DEVICE_OFFLINE, "AMP가 TCP로 연결되어 있지 않습니다.");
        }

        // 0x05 패킷 전송 (payload 없음)
        byte[] dt7 = DateTime7.now();
        ByteBuf packet = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.LOG_REQUEST, // 0x05
                new byte[0]
        );

        log.info("---> [TCP][LOG] send packet ampId={} opcode=0x05", ampId);

        channel.writeAndFlush(packet)
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        CompletableFuture<List<LogInfoDto>> p = pendingLog.remove(ampId);
                        if (p != null) p.completeExceptionally(f.cause());
                        log.error("[TCP][LOG] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][LOG] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(15, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingLog.remove(ampId);
                    if (ex != null) log.warn("[TCP][LOG] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][LOG] future completed ampId={} total={}", ampId, r == null ? 0 : r.size());
                });

        return future;
    }

    // InboundHandler(0x85)에서 호출
    public void completeLog(int ampId, List<LogInfoDto> logs) {
        CompletableFuture<List<LogInfoDto>> f = pendingLog.remove(ampId);
        if (f != null) {
            f.complete(logs);
            log.info("[TCP][LOG] complete ampId={} total={}", ampId, logs == null ? 0 : logs.size());
        } else {
            log.warn("[TCP][LOG] complete ignored (no pending) ampId={} total={}", ampId, logs == null ? 0 : logs.size());
        }
    }

    public void completeLogExceptionally(int ampId, Throwable t) {
        CompletableFuture<List<LogInfoDto>> f = pendingLog.remove(ampId);
        if (f != null) {
            f.completeExceptionally(t);
            log.warn("[TCP][LOG] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][LOG] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

}
