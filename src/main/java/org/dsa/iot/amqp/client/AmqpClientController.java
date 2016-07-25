package org.dsa.iot.amqp.client;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class AmqpClientController {
    private String url;
    private String targetBroker;
    private Channel channel;
    private Node node;

    public AmqpClientController(String url, String targetBroker, Node node) {
        this.url = url;
        this.targetBroker = targetBroker;
        this.node = node;

        node.setMetaData(this);
    }

    public void init() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(url);
        Connection conn = factory.newConnection();
        this.channel = conn.createChannel();

        AmqpFakeNode brokerNode = new AmqpFakeNode(
                "broker",
                node,
                node.getLink(),
                this,
                "/"
        );

        node.addChild(brokerNode);

        AmqpNodeController controller = new AmqpNodeController(this, brokerNode, "/");
        controller.init();
    }

    public Channel getChannel() {
        return channel;
    }

    public void sendBrokerRequest(String[] parts) {
        String request = StringUtils.join(parts, "::");
        String brokerQueue = "broker." + targetBroker + ".input.request";
        try {
            channel.basicPublish(
                    "",
                    brokerQueue,
                    MessageProperties.BASIC,
                    request.getBytes("UTF-8")
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        try {
            channel.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public String getBrokerPrefix(String input) {
        return "broker." + targetBroker + "." + input;
    }
}
