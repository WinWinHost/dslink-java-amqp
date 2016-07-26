package org.dsa.iot.amqp.server;

import org.dsa.iot.amqp.client.AmqpClientController;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.util.handler.Handler;

public class RemoveServerAction implements Handler<ActionResult> {
    private Node node;

    public RemoveServerAction(Node node) {
        this.node = node;
    }

    @Override
    public void handle(ActionResult event) {
        AmqpRemoteController controller = node.getMetaData();
        controller.destroy();
        node.delete();
    }
}
