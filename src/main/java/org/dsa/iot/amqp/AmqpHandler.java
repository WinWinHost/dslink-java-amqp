package org.dsa.iot.amqp;

import org.dsa.iot.amqp.client.AmqpClientController;
import org.dsa.iot.amqp.client.CreateClientAction;
import org.dsa.iot.amqp.client.RemoveClientAction;
import org.dsa.iot.amqp.server.*;
import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpHandler extends DSLinkHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AmqpHandler.class);

    private DSLink requesterLink;
    private DSLink responderLink;
    private RequesterSubscribeContainer subscribeContainer;
    private RequesterListContainer listContainer;

    @Override
    public void onRequesterInitialized(DSLink link) {
        super.onRequesterInitialized(link);

        this.requesterLink = link;
        this.subscribeContainer = new RequesterSubscribeContainer(requesterLink);
        this.listContainer = new RequesterListContainer(requesterLink);

        LOG.info("Requester Initialized.");
        fullInitialize();
    }

    @Override
    public void onResponderInitialized(DSLink link) {
        super.onResponderInitialized(link);

        this.responderLink = link;

        LOG.info("Responder Initialized.");
        fullInitialize();
    }

    @Override
    public void onResponderConnected(DSLink link) {
        super.onResponderConnected(link);
        LOG.info("Responder Connected.");

        fullInitialize();
    }

    @Override
    public void onRequesterConnected(DSLink link) {
        super.onRequesterConnected(link);
        LOG.info("Requester Connected.");
        fullInitialize();
    }

    @Override
    public boolean isRequester() {
        return true;
    }

    @Override
    public boolean isResponder() {
        return true;
    }

    public DSLink getRequesterLink() {
        return requesterLink;
    }

    public DSLink getResponderLink() {
        return responderLink;
    }

    public RequesterSubscribeContainer getSubscribeContainer() {
        return subscribeContainer;
    }

    public RequesterListContainer getListContainer() {
        return listContainer;
    }

    private boolean hasInitialized = false;

    public void fullInitialize() {
        if (hasInitialized) {
            return;
        }

        if (requesterLink == null || responderLink == null) {
            return;
        }

        hasInitialized = true;

        NodeManager nodeManager = responderLink.getNodeManager();
        Node superRoot = nodeManager.getSuperRoot();

        {
            Action action = new Action(Permission.CONFIG, new CreateServerAction(this))
                    .addParameter(new Parameter("name", ValueType.STRING).setPlaceHolder("My Server"))
                    .addParameter(new Parameter("brokerId", ValueType.STRING).setPlaceHolder("mysrv123"))
                    .addParameter(new Parameter("url", ValueType.STRING).setPlaceHolder("amqp://my.host/vhost"));

            superRoot
                    .createChild("createAmqpServer")
                    .setDisplayName("Create AMQP Data Server")
                    .setAction(action)
                    .setSerializable(false)
                    .build();
        }

        {
            Action action = new Action(Permission.CONFIG, new CreateClientAction(this))
                    .addParameter(new Parameter("name", ValueType.STRING).setPlaceHolder("My Client"))
                    .addParameter(new Parameter("targetBrokerId", ValueType.STRING).setPlaceHolder("mysrv123"))
                    .addParameter(new Parameter("url", ValueType.STRING).setPlaceHolder("amqp://my.host/vhost"));

            superRoot
                    .createChild("createAmqpClient")
                    .setDisplayName("Create AMQP Data Client")
                    .setAction(action)
                    .setSerializable(false)
                    .build();
        }

        for (Node node : superRoot.getChildren().values()) {
            if (node.getConfig("server") != null && node.getConfig("server").getBool()) {
                initializeServerNode(node);
            } else if (node.getConfig("client") != null && node.getConfig("client").getBool()) {
                initializeClientNode(node);
            }
        }
    }

    public void initializeServerNode(Node node) {
        {
            Action action = new Action(Permission.CONFIG, new RemoveServerAction(node));

            node
                    .createChild("remove")
                    .setDisplayName("Remove")
                    .setAction(action)
                    .setSerializable(false)
                    .build();
        }

        String url = node.getConfig("amqp_url").getString();
        String brokerName = node.getConfig("amqp_name").getString();
        AmqpRemoteConfig config = new AmqpRemoteConfig(url, brokerName);
        AmqpRemoteController provider = new AmqpRemoteController(this, config, node);
        try {
            provider.init();
        } catch (Exception e) {
            e.printStackTrace();
            provider.destroy();
        }
    }

    public void initializeClientNode(Node node) {
        {
            Action action = new Action(Permission.CONFIG, new RemoveClientAction(node));

            node
                    .createChild("remove")
                    .setDisplayName("Remove")
                    .setAction(action)
                    .setSerializable(false)
                    .build();
        }

        String url = node.getConfig("amqp_url").getString();
        String brokerName = node.getConfig("amqp_target").getString();
        AmqpClientController controller = new AmqpClientController(url, brokerName, node);
        try {
            controller.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
