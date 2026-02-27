package com.nptechon.smartamp.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "smartamp.tcp")
public class TcpServerProperties {
    private int port = 9000;
    private int bossThreads = 1;
    private int workerThreads = 0;
}
