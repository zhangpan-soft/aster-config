package io.asterconfig.server.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aster.netty.server")
public class AsterNettyServerProperties {

    private boolean enabled = true;
    private int port = 9088;
    private int bossThreads = 1;
    private int workerThreads = 0;
    private int idleTimeoutSeconds = 90;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }
}
