package io.asterconfig.client.protocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AsterClientProperties {

    private boolean enabled = true;
    private String serverAddr = "http://localhost:8088";
    private String env = "dev";
    private List<String> namespaces = new ArrayList<>();
    private boolean failFast = true;
    private boolean nettyEnabled = true;
    private String nettyHost;
    private int nettyPort = 9088;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration pollInterval = Duration.ofSeconds(60);
    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private Duration reconnectDelay = Duration.ofSeconds(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public List<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces == null ? new ArrayList<>() : namespaces;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isNettyEnabled() {
        return nettyEnabled;
    }

    public void setNettyEnabled(boolean nettyEnabled) {
        this.nettyEnabled = nettyEnabled;
    }

    public String getNettyHost() {
        return nettyHost;
    }

    public void setNettyHost(String nettyHost) {
        this.nettyHost = nettyHost;
    }

    public int getNettyPort() {
        return nettyPort;
    }

    public void setNettyPort(int nettyPort) {
        this.nettyPort = nettyPort;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(Duration reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }
}
