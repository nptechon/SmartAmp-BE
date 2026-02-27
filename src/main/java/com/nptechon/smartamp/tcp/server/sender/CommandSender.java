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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandSender {

    private final TcpSessionManager sessionManager;

    private final Map<Integer, CompletableFuture<Boolean>> pendingStatus = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingControl = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingBroadcast = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingStream = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<byte[]>> pendingLog = new ConcurrentHashMap<>();

    // =========================
    // timeout unwrap helpers
    // =========================
    private static boolean isTimeoutCause(Throwable t) {
        if (t == null) return false;
        if (t instanceof TimeoutException) return true;

        // ExecutionException / CompletionException / 기타 wrapper 들 안을 따라 내려가며 확인
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof TimeoutException) return true;
            t = c;
            c = t.getCause();
        }
        return false;
    }

    private static void throwIfTimeout(Throwable t, String logTag, int ampId) {
        if (isTimeoutCause(t)) {
            log.warn("{} timeout(unwrap) ampId={} cause={}", logTag, ampId, t.toString());
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        }
    }

    // =========================
    // pending registration helper (race-safe)
    //
    // putIfAbsent + prev.isDone() 패턴의 레이스 해결:
    // - prev=done 인데 아직 맵에서 제거되지 않은 타이밍에 새 요청이 들어오면
    //   새 future 가 맵에 등록되지 않은 채 진행되어 응답 complete 가 누락되어 timeout 될 수 있음
    //
    // 해결:
    // - prev=done 이면 (키,값) remove 로 정확히 제거 시도 후 재시도해서
    // fresh future 가 맵에 "정상 등록된 경우에만" send 로직을 진행하게 한다.
    // =========================
    private static <T> CompletableFuture<T> registerPending(
            Map<Integer, CompletableFuture<T>> pending,
            int ampId,
            CompletableFuture<T> fresh,
            String tag
    ) {
        while (true) {
            CompletableFuture<T> prev = pending.putIfAbsent(ampId, fresh);
            if (prev == null) {
                return fresh; // 정상 등록 성공
            }
            if (!prev.isDone()) {
                log.info("{} already pending -> reuse future ampId={}", tag, ampId);
                return prev; // 진행 중이면 재사용
            }
            // 완료된 prev가 맵에 남아있는 레이스 구간 → (키,값) remove로 제거 후 재시도
            boolean removed = pending.remove(ampId, prev);
            if (!removed) {
                // 누군가 먼저 치웠거나/교체했으면 다음 loop에서 다시 시도
                log.debug("{} pending cleanup race ampId={}", tag, ampId);
            }
        }
    }

    /**
     * 앰프 상태 가져오기
     */
    public boolean getStatus(int ampId) {
        try {
            boolean result = getStatusAsync(ampId).get(5, TimeUnit.SECONDS);
            log.info("[TCP][STATUS] response sync ampId={} isOn={}", ampId, result);
            return result;
        } catch (TimeoutException e) {
            log.warn("[TCP][STATUS] timeout ampId={}", ampId);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
        } catch (ExecutionException e) {
            // orTimeout 에서 완료된 TimeoutException 도 여기로 들어올 수 있음
            throwIfTimeout(e, "[TCP][STATUS]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][STATUS] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CompletionException e) {
            // (현재는 get()이라 거의 안 오지만, 혹시 join() 쓰는 곳 생기면 대비)
            throwIfTimeout(e, "[TCP][STATUS]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][STATUS] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CustomException e) {
            log.error("[TCP][STATUS] custom failed ampId={}", ampId, e);
            throw e;
        } catch (Exception e) {
            log.error("[TCP][STATUS] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }


    public CompletableFuture<Boolean> getStatusAsync(int ampId) {
        // 1) 중복 요청 방지(선택)
        CompletableFuture<Boolean> fresh = new CompletableFuture<>();
        CompletableFuture<Boolean> future = registerPending(pendingStatus, ampId, fresh, "[TCP][STATUS]");
        if (future != fresh) return future;

        // 2) 세션 확인
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingStatus.remove(ampId, future);
            log.warn("[TCP][STATUS] offline ampId={} channel={}", ampId, channel);
            future.completeExceptionally(new CustomException(ErrorCode.DEVICE_OFFLINE));
            return future;
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
                        // (키,값) remove로 "내 future"만 제거
                        boolean removed = pendingStatus.remove(ampId, future);
                        if (removed) future.completeExceptionally(f.cause());
                        log.error("[TCP][STATUS] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][STATUS] write success ampId={}", ampId);
                    }
                });

        // 4) 타임아웃 처리
        future.orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    // (키,값) remove로 "내 future"만 제거
                    pendingStatus.remove(ampId, future);
                    if (ex != null) log.warn("[TCP][STATUS] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][STATUS] future completed ampId={} isOn={}", ampId, r);
                });

        return future;
    }


    // InboundHandler 에서 호출될 완료 함수
    public void completeStatus(int ampId, Boolean isOn) {
        CompletableFuture<Boolean> f = pendingStatus.get(ampId);
        if (f != null && pendingStatus.remove(ampId, f)) {
            f.complete(isOn);
            log.info("[TCP][STATUS] complete ampId={} isOn={}", ampId, isOn);
        } else {
            // f == null 이면 늦게 온 응답/대기 없음 -> 로그만
            log.warn("[TCP][STATUS] complete ignored (no pending) ampId={} isOn={}", ampId, isOn);
        }
    }

    public void completeStatusExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingStatus.get(ampId);
        if (f != null && pendingStatus.remove(ampId, f)) {
            f.completeExceptionally(t);
            log.warn("[TCP][STATUS] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][STATUS] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

    /**
     * 앰프 전원 제어
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
        } catch (ExecutionException e) {
            // orTimeout 에서 발생한 timeout 도 DEVICE_TIMEOUT 으로
            throwIfTimeout(e, "[TCP][CONTROL]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][CONTROL] failed ampId={} power={}", ampId, power, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CompletionException e) {
            throwIfTimeout(e, "[TCP][CONTROL]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][CONTROL] failed ampId={} power={}", ampId, power, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CustomException e) {
            log.error("[TCP][CONTROL] custom failed ampId={} power={}", ampId, power, e);
            throw e;
        } catch (Exception e) {
            log.error("[TCP][CONTROL] failed ampId={} power={}", ampId, power, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // [내부] 비동기 이벤트 기반 처리!!
    // CompletableFuture<Boolean> 반환.. TCP 패킷을 Netty 가 전송.. 아직 응답은 안 옴
    public CompletableFuture<Boolean> sendPowerAsync(int ampId, AmpPower power) {
        // 앰프의 응답이 오면 넣어둘 기다릴 상자 만들기..
        CompletableFuture<Boolean> fresh = new CompletableFuture<>();
        CompletableFuture<Boolean> future = registerPending(pendingControl, ampId, fresh, "[TCP][CONTROL]");
        if (future != fresh) return future;

        // 연결된 채널 확인
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingControl.remove(ampId, future);
            log.warn("[TCP][CONTROL] offline ampId={} channel={}", ampId, channel);
            future.completeExceptionally(new CustomException(ErrorCode.DEVICE_OFFLINE));
            return future;
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
                        boolean removed = pendingControl.remove(ampId, future);
                        if (removed) future.completeExceptionally(f.cause());
                        log.error("[TCP][CONTROL] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][CONTROL] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingControl.remove(ampId, future);
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
        CompletableFuture<Boolean> f = pendingControl.get(ampId);
        if (f != null && pendingControl.remove(ampId, f)) {
            f.complete(isOn);
            log.info("[TCP][CONTROL] complete ampId={} isOn={}", ampId, isOn);
        } else {
            log.warn("[TCP][CONTROL] complete ignored (no pending) ampId={} isOn={}", ampId, isOn);
        }
    }

    public void completeControlExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingControl.get(ampId);
        if (f != null && pendingControl.remove(ampId, f)) {
            f.completeExceptionally(t);
            log.warn("[TCP][CONTROL] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][CONTROL] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

    /**
     * 인덱스로 앰프에 저장된 음원 방송 시
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
        } catch (ExecutionException e) {
            throwIfTimeout(e, "[TCP][BROADCAST]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][BROADCAST] failed ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CompletionException e) {
            throwIfTimeout(e, "[TCP][BROADCAST]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][BROADCAST] failed ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CustomException e) {
            log.error("[TCP][BROADCAST] custom failed ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw e;
        } catch (Exception e) {
            log.error("[TCP][BROADCAST] failed ampId={} index={} repeat={}", ampId, index, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<Boolean> sendIndexAsync(int ampId, int index, int repeat) {
        CompletableFuture<Boolean> fresh = new CompletableFuture<>();
        CompletableFuture<Boolean> future = registerPending(pendingBroadcast, ampId, fresh, "[TCP][BROADCAST]");
        if (future != fresh) return future;

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingBroadcast.remove(ampId, future);
            log.warn("[TCP][BROADCAST] offline ampId={} channel={}", ampId, channel);
            future.completeExceptionally(new CustomException(ErrorCode.DEVICE_OFFLINE));
            return future;
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
                        boolean removed = pendingBroadcast.remove(ampId, future);
                        if (removed) future.completeExceptionally(f.cause());
                        log.error("[TCP][BROADCAST] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][BROADCAST] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingBroadcast.remove(ampId, future);
                    if (ex != null) log.warn("[TCP][BROADCAST] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][BROADCAST] future completed ampId={} ok={}", ampId, r);
                });

        return future;
    }

    public void completeBroadcast(int ampId, boolean ok) {
        CompletableFuture<Boolean> f = pendingBroadcast.get(ampId);
        if (f != null && pendingBroadcast.remove(ampId, f)) {
            f.complete(ok);
            log.info("[TCP][BROADCAST] complete ampId={} ok={}", ampId, ok);
        } else {
            log.warn("[TCP][BROADCAST] complete ignored (no pending) ampId={} ok={}", ampId, ok);
        }
    }

    public void completeBroadcastExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingBroadcast.get(ampId);
        if (f != null && pendingBroadcast.remove(ampId, f)) {
            f.completeExceptionally(t);
            log.warn("[TCP][BROADCAST] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][BROADCAST] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

    /**
     * 스트림 패킷 보내기 전 타입 선언
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
        } catch (ExecutionException e) {
            throwIfTimeout(e, "[TCP][STREAM]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][STREAM] failed ampId={} type={} repeat={}", ampId, type, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CompletionException e) {
            throwIfTimeout(e, "[TCP][STREAM]", ampId);
            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][STREAM] failed ampId={} type={} repeat={}", ampId, type, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (CustomException e) {
            log.error("[TCP][STREAM] custom failed ampId={} type={} repeat={}", ampId, type, repeat, e);
            throw e;
        } catch (Exception e) {
            log.error("[TCP][STREAM] failed ampId={} type={} repeat={}", ampId, type, repeat, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<Boolean> sendStreamTypeAsync(int ampId, StreamType type, int repeat) {
        CompletableFuture<Boolean> fresh = new CompletableFuture<>();
        CompletableFuture<Boolean> future = registerPending(pendingStream, ampId, fresh, "[TCP][STREAM]");
        if (future != fresh) return future;

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingStream.remove(ampId, future);
            log.warn("[TCP][STREAM] offline ampId={} channel={}", ampId, channel);
            future.completeExceptionally(new CustomException(ErrorCode.DEVICE_OFFLINE));
            return future;
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
                        boolean removed = pendingStream.remove(ampId, future);
                        if (removed) future.completeExceptionally(f.cause());
                        log.error("[TCP][STREAM] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][STREAM] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingStream.remove(ampId, future);
                    if (ex != null) log.warn("[TCP][STREAM] future completed exceptionally ampId={} ex={}", ampId, ex.toString());
                    else log.debug("[TCP][STREAM] future completed ampId={} ok={}", ampId, r);
                });

        return future;
    }

    public void completeStream(int ampId, boolean ok) {
        CompletableFuture<Boolean> f = pendingStream.get(ampId);
        if (f != null && pendingStream.remove(ampId, f)) {
            f.complete(ok);
            log.info("[TCP][STREAM] complete ampId={} ok={}", ampId, ok);
        } else {
            log.warn("[TCP][STREAM] complete ignored (no pending) ampId={} ok={}", ampId, ok);
        }
    }

    public void completeStreamExceptionally(int ampId, Throwable t) {
        CompletableFuture<Boolean> f = pendingStream.get(ampId);
        if (f != null && pendingStream.remove(ampId, f)) {
            f.completeExceptionally(t);
            log.warn("[TCP][STREAM] complete exceptionally ampId={} cause={}", ampId, t.toString());
        } else {
            log.warn("[TCP][STREAM] complete exceptionally ignored (no pending) ampId={} cause={}", ampId, t.toString());
        }
    }

    /**
     * 로그 조회 (0x05 요청 -> 0x85 응답)
     * ⚠ 이제 List<LogInfoDto>가 아니라 raw payload(byte[])를 반환한다.
     * 실제 파싱은 Service 레벨에서 수행해야 한다.
     */
    public byte[] getLogs(int ampId) {
        try {
            log.info("[TCP][LOG] request sync ampId={}", ampId);

            byte[] payload = getLogsAsync(ampId).get(6, TimeUnit.SECONDS);

            log.info("[TCP][LOG] response sync ampId={} payloadSize={}",
                    ampId, payload == null ? 0 : payload.length);

            return payload;

        } catch (TimeoutException e) {
            log.warn("[TCP][LOG] timeout ampId={} -> close session", ampId);
            throw new CustomException(ErrorCode.DEVICE_TIMEOUT);

        } catch (ExecutionException e) {
            // orTimeout에서 발생한 timeout도 DEVICE_TIMEOUT으로
            if (isTimeoutCause(e)) {
                log.warn("[TCP][LOG] timeout(unwrap) ampId={} -> close session cause={}", ampId, e.toString());
                throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
            }

            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][LOG] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);

        } catch (CompletionException e) {
            if (isTimeoutCause(e)) {
                log.warn("[TCP][LOG] timeout(unwrap) ampId={} -> close session cause={}", ampId, e.toString());
                throw new CustomException(ErrorCode.DEVICE_TIMEOUT);
            }

            Throwable c = e.getCause();
            if (c instanceof CustomException ce) throw ce;
            log.error("[TCP][LOG] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);

        } catch (CustomException e) {
            log.error("[TCP][LOG] custom failed ampId={}", ampId, e);
            throw e;

        } catch (Exception e) {
            log.error("[TCP][LOG] failed ampId={}", ampId, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public CompletableFuture<byte[]> getLogsAsync(int ampId) {

        CompletableFuture<byte[]> fresh = new CompletableFuture<>();
        CompletableFuture<byte[]> future =
                registerPending(pendingLog, ampId, fresh, "[TCP][LOG]");
        if (future != fresh) return future;

        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            pendingLog.remove(ampId, future);
            log.warn("[TCP][LOG] offline ampId={} channel={}", ampId, channel);
            future.completeExceptionally(
                    new CustomException(ErrorCode.DEVICE_OFFLINE));
            return future;
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
                        boolean removed = pendingLog.remove(ampId, future);
                        if (removed) future.completeExceptionally(f.cause());
                        log.error("[TCP][LOG] write failed ampId={}", ampId, f.cause());
                    } else {
                        log.debug("[TCP][LOG] write success ampId={}", ampId);
                    }
                });

        future.orTimeout(6, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    pendingLog.remove(ampId, future);

                    if (ex != null) {
                        log.warn("[TCP][LOG] future completed exceptionally ampId={} ex={}",
                                ampId, ex.toString());

                        // timeout이면 세션 close로 누적 버퍼/동기 문제를 끊어버림
                        if (isTimeoutCause(ex)) {
                            log.warn("[TCP][LOG] timeout detected -> close session ampId={}", ampId);
                        }
                    } else {
                        log.debug("[TCP][LOG] future completed ampId={} payloadSize={}",
                                ampId, r == null ? 0 : r.length);
                    }
                });

        return future;
    }

    // InboundHandler(0x85)에서 호출
    // ⚠ 이제 List<LogInfoDto>가 아니라 raw payload(byte[])를 전달받는다.
    public void completeLogPayload(int ampId, byte[] payload) {

        CompletableFuture<byte[]> f = pendingLog.get(ampId);

        if (f != null && pendingLog.remove(ampId, f)) {
            // 방어적 복사 (Netty ByteBuf 재사용 이슈 방지)
            byte[] copy = (payload == null ? new byte[0] :
                    java.util.Arrays.copyOf(payload, payload.length));

            f.complete(copy);

            log.info("[TCP][LOG] complete ampId={} payloadSize={}",
                    ampId, copy.length);

        } else {
            log.warn("[TCP][LOG] complete ignored (no pending) ampId={} payloadSize={}",
                    ampId, payload == null ? 0 : payload.length);
        }
    }

    public void completeLogExceptionally(int ampId, Throwable t) {
        CompletableFuture<byte[]> f = pendingLog.get(ampId);
        if (f != null && pendingLog.remove(ampId, f)) {
            f.completeExceptionally(t);
            log.warn("[TCP][LOG] complete exceptionally ampId={} cause={}",
                    ampId, t.toString());
        } else {
            log.warn("[TCP][LOG] complete exceptionally ignored (no pending) ampId={} cause={}",
                    ampId, t.toString());
        }
    }
}
