package org.dsa.iot.amqp.server;

import com.rabbitmq.client.MessageProperties;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;

import java.io.IOException;

public class SubscribeDataHandler implements RequestHandler, HandlesInitialState {
    private AmqpRemoteController provider;
    private String path;
    private ValueHandler valueHandler;
    private String exchangeName;

    public SubscribeDataHandler(AmqpRemoteController provider, String path) {
        this.provider = provider;
        this.path = path;
        this.valueHandler = new ValueHandler();
        this.exchangeName = provider.getBrokerPathPrefix("subscribe." + path);
    }

    public AmqpRemoteController getProvider() {
        return provider;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void init() {
        RequesterSubscribeContainer container = provider.getHandler().getSubscribeContainer();
        container.subscribe(path, valueHandler);
    }

    @Override
    public void destroy() {
        RequesterSubscribeContainer container = provider.getHandler().getSubscribeContainer();
        container.unsubscribe(path, valueHandler);
        try {
            provider.getChannel().exchangeDelete(exchangeName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleInitialState(String receiverQueue) {
        RequesterSubscribeContainer container = provider.getHandler().getSubscribeContainer();
        SubscriptionValue event = container.getCurrentState(path);
        if (event != null) {
            JsonArray array = new JsonArray();
            array.add(ValueUtils.toObject(event.getValue()));
            array.add(event.getValue().getTimeStamp());

            try {
                provider.getChannel().basicPublish(
                        null,
                        receiverQueue,
                        MessageProperties.BASIC,
                        array.encode(EncodingFormat.MESSAGE_PACK)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ValueHandler implements Handler<SubscriptionValue> {
        @Override
        public void handle(SubscriptionValue event) {
            JsonArray array = new JsonArray();
            array.add(ValueUtils.toObject(event.getValue()));
            array.add(event.getValue().getTimeStamp());

            try {
                provider.getChannel().basicPublish(
                        exchangeName,
                        "",
                        MessageProperties.BASIC,
                        array.encode(EncodingFormat.MESSAGE_PACK)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SubscribeDataHandler) {
            SubscribeDataHandler handler = (SubscribeDataHandler) obj;

            if (exchangeName.equals(handler.exchangeName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 37 * exchangeName.hashCode();
    }
}
