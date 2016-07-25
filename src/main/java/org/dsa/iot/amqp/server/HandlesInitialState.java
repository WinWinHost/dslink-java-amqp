package org.dsa.iot.amqp.server;

public interface HandlesInitialState {
    void handleInitialState(String receiverQueue);
}
