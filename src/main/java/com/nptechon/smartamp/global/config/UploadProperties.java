package com.nptechon.smartamp.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * mp3 저장 디렉토리
     * 예: ./uploads
     */
    private String dir;

    /**
     * ffmpeg 실행 파일 경로
     * - Linux / macOS: "ffmpeg" (PATH에 등록된 경우)
     * - Windows: "C:/ffmpeg/bin/ffmpeg.exe"
     */
    private String ffmpegPath = "ffmpeg";

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }
}
