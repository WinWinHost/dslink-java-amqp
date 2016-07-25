package org.dsa.iot.amqp.server;

import com.rabbitmq.client.MessageProperties;
import org.dsa.iot.dslink.methods.responses.ListResponse;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;

import java.io.IOException;

public class ListDataHandler implements RequestHandler, HandlesInitialState {
    private final String exchangeName;
    private final String path;
    private final AmqpRemoteController provider;
    private final EventHandler eventHandler;

    public ListDataHandler(AmqpRemoteController provider, String path) {
        this.provider = provider;
        this.path = path;
        this.exchangeName = provider.getBrokerPathPrefix("list." + path);
        this.eventHandler = new EventHandler();
    }

    @Override
    public void init() {
        RequesterListContainer container = provider.getHandler().getListContainer();
        container.subscribe(path, eventHandler);
    }

    @Override
    public void destroy() {
        RequesterListContainer container = provider.getHandler().getListContainer();
        container.unsubscribe(path, eventHandler);
    }

    @Override
    public void handleInitialState(String receiverQueue) {
        RequesterListContainer container = provider.getHandler().getListContainer();
        ListResponse response = container.getCurrentState(path);

        if (response != null) {
            JsonArray array = response.getJsonResponse(null).get("updates");
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

    public class EventHandler implements Handler<ListResponse> {
        @Override
        public void handle(ListResponse event) {
            JsonArray array = event.getJsonResponse(null).get("updates");

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
