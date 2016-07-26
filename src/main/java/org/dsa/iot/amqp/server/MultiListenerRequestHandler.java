package org.dsa.iot.amqp.server;

import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class MultiListenerRequestHandler implements RequestHandler, HandlesInitialState {
    private static final Logger LOG = LoggerFactory.getLogger(MultiListenerRequestHandler.class);

    private final AmqpRemoteController controller;

    public MultiListenerRequestHandler(AmqpRemoteController controller) {
        this.controller = controller;
    }

    public abstract void init();
    public abstract byte[] getCurrentState();

    public void onListenerAdded() {
        counter++;

        if (counter == 1) {
            init();
        }
    }

    public void onListenerRemoved() {
        counter--;

        if (counter <= 0) {
            counter = 0;
            destroy();
            controller.removeRequestHandler(this);
        }
    }

    private int counter = 0;

    public AmqpRemoteController getController() {
        return controller;
    }

    @Override
    public void handleInitialState(String receiverQueue) {
        try {
            byte[] state = getCurrentState();

            if (state.length != 0) {
                getController().getChannel().basicPublish(
                        "",
                        receiverQueue,
                        MessageProperties.BASIC,
                        state
                );
            }
        } catch (Exception e) {
            LOG.error("Failed to send initial state.", e);
        }
    }
}
