package com.nptechon.smartamp.app.service;

import com.nptechon.smartamp.app.dto.RegisterRequestDto;
import com.nptechon.smartamp.app.dto.RegisterResultDto;
import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class RegisterService {

    private static final DateTimeFormatter HH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Value("${smartamp.register.secret-key}")
    private String secretKey;

    /**
     * dto.password 는 앱이 SHA256(rawUserInput) 해서 보낸 해시값.
     * 서버는 SecretKey + 현재(UTC) yyyyMMddHH 로 raw 를 만들고 SHA256 해서 비교.
     */
    public RegisterResultDto verifyRegisterPassword(RegisterRequestDto dto) {
        String provided = normalize(dto.getPassword());
        log.info("앱 password={}", provided);

        if (provided.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "password가 비어있습니다.");
        }

        // 앱과 동일: 현재 UTC 시각(yyyyMMddHH) 1회만 사용
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        String timestamp = nowUtc.format(HH_FMT);        // yyyyMMddHH
        String rawExpected = secretKey + timestamp;      // SecretKey2026022517
        String fullHash = sha256HexLower(rawExpected);
        String expectedShort = fullHash.substring(0, 8);
        log.info("서버 password={}", expectedShort);

        if (expectedShort.equals(provided)) {
            return new RegisterResultDto(true, null);
        }
        return new RegisterResultDto(false, "password가 올바르지 않습니다.");
    }

    private static String sha256HexLower(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return toHexLower(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}