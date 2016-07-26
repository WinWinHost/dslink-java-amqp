package org.dsa.iot.amqp.server;

import com.rabbitmq.client.MessageProperties;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;

import java.io.IOException;

public class SubscribeDataHandler extends MultiListenerRequestHandler {
    private String path;
    private ValueHandler valueHandler;
    private String exchangeName;
    private SubscriptionValue lastValue;

    public SubscribeDataHandler(AmqpRemoteController controller, String path) {
        super(controller);
        this.path = path;
        this.valueHandler = new ValueHandler();
        this.exchangeName = controller.getBrokerPathPrefix("subscribe." + path);
    }

    @Override
    public void init() {
        RequesterSubscribeContainer container = getController().getHandler().getSubscribeContainer();
        container.subscribe(path, valueHandler);
    }

    @Override
    public byte[] getCurrentState() {
        JsonArray array = new JsonArray();

        if (lastValue != null) {
            array.add(ValueUtils.toObject(lastValue.getValue()));
            array.add(lastValue.getValue().getTimeStamp());
            return array.encode(EncodingFormat.MESSAGE_PACK);
        } else {
            return new byte[0];
        }
    }

    @Override
    public void destroy() {
        RequesterSubscribeContainer container = getController().getHandler().getSubscribeContainer();
        container.unsubscribe(path, valueHandler);
        try {
            getController().getChannel().exchangeDelete(exchangeName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class ValueHandler implements Handler<SubscriptionValue> {
        @Override
        public void handle(SubscriptionValue event) {
            lastValue = event;

            JsonArray array = new JsonArray();
            array.add(ValueUtils.toObject(event.getValue()));
            array.add(event.getValue().getTimeStamp());

            try {
                getController().getChannel().basicPublish(
                        exchangeName,
                        "",
                        MessageProperties.PERSISTENT_BASIC,
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
