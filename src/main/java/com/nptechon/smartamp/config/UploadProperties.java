package com.nptechon.smartamp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private String dir;


    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }
}
