package org.dsa.iot.amqp;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.methods.responses.UnsubscribeResponse;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.util.handler.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequesterSubscribeContainer {
    private DSLink requesterLink;
    private Map<String, RequesterSubscribeEntry> entries;

    public RequesterSubscribeContainer(DSLink requesterLink) {
        this.requesterLink = requesterLink;
        this.entries = new HashMap<>();
    }

    public void subscribe(String path, Handler<SubscriptionValue> handler) {
        if (!entries.containsKey(path)) {
            entries.put(path, new RequesterSubscribeEntry(path));
        }

        entries.get(path).addHandler(handler);
    }

    public void unsubscribe(String path, Handler<SubscriptionValue> handler) {
        if (entries.containsKey(path)) {
            entries.get(path).removeHandler(handler);
        }
    }

    public class RequesterSubscribeEntry {
        private final String path;
        private final Handler<SubscriptionValue> internalHandler;
        private final List<Handler<SubscriptionValue>> handlers;

        private SubscriptionValue lastValue;
        private boolean isSubscribed = false;

        public RequesterSubscribeEntry(String path) {
            this.path = path;
            this.handlers = new ArrayList<>();
            this.internalHandler = new Handler<SubscriptionValue>() {
                @Override
                public void handle(SubscriptionValue event) {
                    lastValue = event;
                    for (Handler<SubscriptionValue> handler : handlers) {
                        handler.handle(event);
                    }
                }
            };
        }

        public void addHandler(final Handler<SubscriptionValue> handler) {
            if (!handlers.contains(handler)) {
                handlers.add(handler);
            }
            check();

            if (lastValue != null) {
                handler.handle(lastValue);
            }
        }

        public void removeHandler(Handler<SubscriptionValue> handler) {
            handlers.remove(handler);
            check();
        }

        private void check() {
            if (handlers.isEmpty() && isSubscribed) {
                isSubscribed = false;
                requesterLink.getRequester().unsubscribe(path, new Handler<UnsubscribeResponse>() {
                    @Override
                    public void handle(UnsubscribeResponse event) {
                    }
                });
                lastValue = null;
            } else if (!handlers.isEmpty() && !isSubscribed) {
                isSubscribed = true;
                requesterLink.getRequester().subscribe(path, internalHandler);
            }
        }
    }
}
