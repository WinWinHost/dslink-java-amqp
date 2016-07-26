package org.dsa.iot.amqp.server;

import com.rabbitmq.client.MessageProperties;
import org.dsa.iot.dslink.methods.responses.ListResponse;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;

import java.io.IOException;

public class ListDataHandler extends MultiListenerRequestHandler {
    private final String exchangeName;
    private final String path;
    private final EventHandler eventHandler;

    private JsonArray lastListUpdates;

    public ListDataHandler(AmqpRemoteController controller, String path) {
        super(controller);
        this.path = path;
        this.exchangeName = controller.getBrokerPathPrefix("list." + path);
        this.eventHandler = new EventHandler();
    }

    @Override
    public void init() {
        RequesterListContainer container = getController().getHandler().getListContainer();
        container.subscribe(path, eventHandler);
    }

    @Override
    public byte[] getCurrentState() {
        if (lastListUpdates != null) {
            return lastListUpdates.encode(EncodingFormat.MESSAGE_PACK);
        } else {
            return new byte[0];
        }
    }

    @Override
    public void destroy() {
        RequesterListContainer container = getController().getHandler().getListContainer();
        container.unsubscribe(path, eventHandler);
    }

    public class EventHandler implements Handler<ListResponse> {
        @Override
        public void handle(ListResponse event) {
            JsonArray array = lastListUpdates = event.getJsonResponse(null).get("updates");

            try {
                getController().getChannel().basicPublish(
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
        if (obj instanceof ListDataHandler) {
            ListDataHandler handler = (ListDataHandler) obj;

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
