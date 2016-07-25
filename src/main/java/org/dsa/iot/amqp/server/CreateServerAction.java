package org.dsa.iot.amqp.server;

import org.dsa.iot.amqp.AmqpHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;

public class CreateServerAction implements Handler<ActionResult> {
    private AmqpHandler handler;

    public CreateServerAction(AmqpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handle(ActionResult event) {
        String name = event.getParameter("name").getString();
        String brokerId = event.getParameter("brokerId").getString();
        String url = event.getParameter("url").getString();

        String realName = Node.checkAndEncodeName(name);
        Node node = handler
                .getResponderLink()
                .getNodeManager()
                .createRootNode(realName)
                .setDisplayName(name)
                .setConfig("amqp_name", new Value(brokerId))
                .setConfig("amqp_url", new Value(url))
                .setConfig("server", new Value(true))
                .build();

        handler.initializeServerNode(node);
    }
}
