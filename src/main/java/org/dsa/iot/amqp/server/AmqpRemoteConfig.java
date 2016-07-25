package org.dsa.iot.amqp.server;

public class AmqpRemoteConfig {
    private final String url;
    private final String brokerId;

    public AmqpRemoteConfig(String url, String brokerId) {
        this.url = url;
        this.brokerId = brokerId;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public String getUrl() {
        return url;
    }
}
