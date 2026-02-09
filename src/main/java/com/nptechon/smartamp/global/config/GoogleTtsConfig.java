package com.nptechon.smartamp.global.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.util.List;

@Slf4j
@Configuration
public class GoogleTtsConfig {

    @Value("${google.credentials:}")
    private String credentialsPath;

    @Bean
    public TextToSpeechClient textToSpeechClient() throws Exception {
        // 1) 기본은 ADC(환경변수/메타데이터)를 타게 두고 싶다면 credentialsPath 비우면 됨
        // 2) 지금처럼 application.yml에 path를 뒀으면 그걸로 강제 로드 가능

        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.info("Google TTS: Using Application Default Credentials (ADC).");
            return TextToSpeechClient.create();
        }

        log.info("Google TTS: Using credentials file path={}", credentialsPath);

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();

        return TextToSpeechClient.create(settings);
    }
}
