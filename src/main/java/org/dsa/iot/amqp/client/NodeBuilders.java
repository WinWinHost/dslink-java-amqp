package org.dsa.iot.amqp.client;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;

import java.util.ArrayList;
import java.util.List;

public class NodeBuilders {
    public static void applyMultiChildBuilders(AmqpFakeNode owner, List<NodeBuilder> builders) {
        List<Node> nodes = new ArrayList<>();
        for (NodeBuilder builder : builders) {
            Node node = builder.getChild();

            String go = owner.getDsaPath() + "/" + node.getName();
            AmqpNodeController nc = new AmqpNodeController(owner.getController(), node, go);
            nc.init();

            nodes.add(node);
        }

        owner.addChildren(nodes);
    }
}
