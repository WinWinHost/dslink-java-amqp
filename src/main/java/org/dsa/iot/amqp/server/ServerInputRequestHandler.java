package org.dsa.iot.amqp.server;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerInputRequestHandler extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ServerInputRequestHandler.class);

    private AmqpRemoteController provider;

    public ServerInputRequestHandler(AmqpRemoteController provider) {
        super(provider.getChannel());
        this.provider = provider;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        String text = new String(body);
        LOG.debug("Got Request: " + text);
        String[] parts = text.split("::");
        if (parts.length >= 2 && "subscribe".equals(parts[0])) {
            String path = parts[1];
            String receiverQueue = parts.length > 2 ? parts[2] : null;
            SubscribeDataHandler handler = new SubscribeDataHandler(provider, path);
            provider.addRequestHandler(handler, receiverQueue);
        } else if (parts.length >= 2 && "list".equals(parts[0])) {
            String path = parts[1];
            String receiverQueue = parts.length > 2 ? parts[2] : null;
            ListDataHandler handler = new ListDataHandler(provider, path);
            provider.addRequestHandler(handler, receiverQueue);
        } else if (parts.length >= 2 && "unlist".equals(parts[0])) {
            String path = parts[1];
            ListDataHandler handler = new ListDataHandler(provider, path);
            handler = provider.findEquivalentHandler(handler);

            if (handler != null) {
                handler.onListenerRemoved();
            }
        } else if (parts.length >= 2 && "unsubscribe".equals(parts[0])) {
            String path = parts[1];
            SubscribeDataHandler handler = new SubscribeDataHandler(provider, path);
            handler = provider.findEquivalentHandler(handler);

            if (handler != null) {
                handler.onListenerRemoved();
            }
        }

        getChannel().basicAck(envelope.getDeliveryTag(), false);
    }
}
