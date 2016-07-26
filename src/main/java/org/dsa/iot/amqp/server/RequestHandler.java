package org.dsa.iot.amqp.server;

public interface RequestHandler {
    void onListenerAdded();
    void onListenerRemoved();

    void destroy();
}
