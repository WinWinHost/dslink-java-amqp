package org.dsa.iot.amqp.client;

import org.dsa.iot.amqp.AmqpHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;

public class CreateClientAction implements Handler<ActionResult> {
    private AmqpHandler handler;

    public CreateClientAction(AmqpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handle(ActionResult event) {
        String name = event.getParameter("name").getString();
        String targetBroker = event.getParameter("targetBrokerId").getString();
        String url = event.getParameter("url").getString();

        String realName = Node.checkAndEncodeName(name);
        Node node = handler
                .getResponderLink()
                .getNodeManager()
                .createRootNode(realName)
                .setDisplayName(name)
                .setConfig("amqp_target", new Value(targetBroker))
                .setConfig("amqp_url", new Value(url))
                .setConfig("client", new Value(true))
                .build();

        handler.initializeClientNode(node);
    }
}
