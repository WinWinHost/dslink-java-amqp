package org.dsa.iot.amqp.client;

import org.dsa.iot.dslink.link.Linkable;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;

public class AmqpFakeNode extends Node {
    private AmqpClientController controller;
    private String dsaPath;

    public AmqpFakeNode(String name, Node parent, Linkable link, AmqpClientController controller, String dsaPath) {
        super(name, parent, link);
        this.controller = controller;
        this.dsaPath = dsaPath;
    }

    @Override
    public Node getChild(String name) {
        Node child = super.getChild(name);
        if (child == null) {
            child = createChild(name).build();
            AmqpNodeController nodeController = new AmqpNodeController(
                    controller,
                    child,
                    ((AmqpFakeNode) child).dsaPath
            );

            nodeController.init();
            nodeController.loadNow();
        }

        if (child instanceof AmqpFakeNode) {
            ((AmqpNodeController) child.getMetaData()).init();
        }

        return child;
    }

    @Override
    public boolean hasChild(String name) {
        return true;
    }

    @Override
    public NodeBuilder createChild(String name, String profile) {
        NodeBuilder b = new NodeBuilder(this, new AmqpFakeNode(
                name,
                this,
                getLink(),
                controller,
                (dsaPath.equals("/") ? "" : dsaPath) + "/" + name
        ));

        if (profile != null) {
            b.setProfile(profile);
        }
        return b;
    }

    public String getDsaPath() {
        return dsaPath;
    }

    public AmqpClientController getController() {
        return controller;
    }
}
