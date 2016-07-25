package org.dsa.iot.amqp.server;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.dsa.iot.amqp.AmqpHandler;
import org.dsa.iot.dslink.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class AmqpRemoteController {
    private static final Logger LOG = LoggerFactory.getLogger(AmqpRemoteController.class);

    private AmqpHandler handler;
    private AmqpRemoteConfig config;
    private Channel channel;
    private ArrayList<RequestHandler> requestHandlers;
    private ServerInputRequestHandler inputRequestHandler;
    private Node node;

    public AmqpRemoteController(AmqpHandler handler, AmqpRemoteConfig config, Node node) {
        this.handler = handler;
        this.config = config;
        this.requestHandlers = new ArrayList<>();
        this.node = node;

        node.setMetaData(this);
    }

    public AmqpHandler getHandler() {
        return handler;
    }

    public void init() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(config.getUrl());
        Connection conn = factory.newConnection();
        this.channel = conn.createChannel();

        inputRequestHandler = new ServerInputRequestHandler(this);

        String inputRequestQueueName = getBrokerPathPrefix("input.request");
        channel.queueDeclare(inputRequestQueueName, true, false, true, null);
        channel.basicConsume(inputRequestQueueName, inputRequestHandler);
    }

    public String getBrokerPathPrefix(String path) {
        return "broker." + config.getBrokerId() + "." + path;
    }

    public Channel getChannel() {
        return channel;
    }

    public void addRequestHandler(RequestHandler handler) {
        addRequestHandler(handler, null);
    }

    public void addRequestHandler(RequestHandler handler, String receiverQueue) {
        if (!requestHandlers.contains(handler)) {
            requestHandlers.add(handler);
            handler.init();
        } else {
            if (handler instanceof HandlesInitialState && receiverQueue != null) {
                LOG.debug("Found an equivalent request handler in the active handlers already. Sending initial state.");
                ((HandlesInitialState) handler).handleInitialState(receiverQueue);
            } else {
                LOG.debug("Found an equivalent request handler in the active handlers already. Skipping.");
            }
        }
    }

    public void destroy() {
        for (RequestHandler handler : requestHandlers) {
            handler.destroy();
        }

        requestHandlers.clear();

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
            channel = null;
        }
    }

    public ServerInputRequestHandler getInputRequestHandler() {
        return inputRequestHandler;
    }

    public Node getNode() {
        return node;
    }
}
