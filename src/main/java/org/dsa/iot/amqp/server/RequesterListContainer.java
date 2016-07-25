package org.dsa.iot.amqp.server;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.methods.requests.ListRequest;
import org.dsa.iot.dslink.methods.responses.CloseResponse;
import org.dsa.iot.dslink.methods.responses.ListResponse;
import org.dsa.iot.dslink.util.handler.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequesterListContainer {
    private DSLink requesterLink;
    private Map<String, RequesterListEntry> entries;

    public RequesterListContainer(DSLink requesterLink) {
        this.requesterLink = requesterLink;
        this.entries = new HashMap<>();
    }

    public void subscribe(String path, Handler<ListResponse> handler) {
        if (!entries.containsKey(path)) {
            entries.put(path, new RequesterListEntry(path));
        }

        entries.get(path).addHandler(handler);
    }

    public void unsubscribe(String path, Handler<ListResponse> handler) {
        if (entries.containsKey(path)) {
            entries.get(path).removeHandler(handler);
        }
    }

    public ListResponse getCurrentState(String path) {
        if (entries.containsKey(path)) {
            return entries.get(path).lastEvent;
        }
        return null;
    }

    public class RequesterListEntry {
        private final String path;
        private final Handler<ListResponse> internalHandler;
        private final List<Handler<ListResponse>> handlers;

        private ListResponse lastEvent;
        private boolean isSubscribed = false;
        private int lastRid = -1;

        public RequesterListEntry(String path) {
            this.path = path;
            this.handlers = new ArrayList<>();
            this.internalHandler = new Handler<ListResponse>() {
                @Override
                public void handle(ListResponse event) {
                    lastEvent = event;
                    for (Handler<ListResponse> handler : handlers) {
                        handler.handle(event);
                    }
                }
            };
        }

        public void addHandler(final Handler<ListResponse> handler) {
            if (!handlers.contains(handler)) {
                handlers.add(handler);
            }
            check();

            if (lastEvent != null) {
                handler.handle(lastEvent);
            }
        }

        public void removeHandler(Handler<ListResponse> handler) {
            handlers.remove(handler);
            check();
        }

        private void check() {
            if (handlers.isEmpty() && isSubscribed) {
                isSubscribed = false;
                requesterLink.getRequester().closeStream(lastRid, new Handler<CloseResponse>() {
                    @Override
                    public void handle(CloseResponse event) {
                    }
                });
                lastEvent = null;
            } else if (!handlers.isEmpty() && !isSubscribed) {
                isSubscribed = true;
                requesterLink.getRequester().list(new ListRequest(path), internalHandler);
            }
        }
    }
}
