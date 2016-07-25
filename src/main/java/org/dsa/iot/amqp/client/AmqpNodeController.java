package org.dsa.iot.amqp.client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.*;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.EncodingFormat;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("Duplicates")
public class AmqpNodeController {
    private AmqpClientController controller;
    private int listHandles = 0;
    private int subscribeHandles = 0;
    private boolean hasListHandle = false;
    private boolean hasSubscribeHandle = false;
    private Node node;
    private String dsaPath;
    private AmqpListHandler listHandler;
    private AmqpSubscribeHandler subscribeHandler;

    public AmqpNodeController(AmqpClientController controller, Node node, String dsaPath) {
        this.controller = controller;
        this.node = node;

        node.setSerializable(false);
        this.dsaPath = dsaPath;
        this.listHandler = new AmqpListHandler(controller.getChannel());
        this.subscribeHandler = new AmqpSubscribeHandler(controller.getChannel());

        node.setMetaData(this);
    }

    private boolean isInitialized = false;

    public void init() {
        if (isInitialized) {
            return;
        }

        isInitialized = true;

        node.getListener().setOnListHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                listHandles++;
                checkListHandles();
            }
        });

        node.getListener().setOnListClosedHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                listHandles--;
                checkListHandles();
            }
        });

        node.getListener().setOnSubscribeHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                subscribeHandles++;
                checkSubscribeHandles();
            }
        });

        node.getListener().setOnUnsubscribeHandler(new Handler<Node>() {
            @Override
            public void handle(Node event) {
                subscribeHandles--;
                checkSubscribeHandles();
            }
        });
    }

    public void checkListHandles() {
        if (listHandles <= 0 && hasListHandle) {
            closeListHandles();
            listHandles = 0;
        } else if (!hasListHandle) {
            startListHandles();
        }
    }

    public void checkSubscribeHandles() {
        if (subscribeHandles <= 0 && hasSubscribeHandle) {
            closeSubscribeHandles();
            subscribeHandles = 0;
        } else if (!hasSubscribeHandle) {
            startSubscribeHandles();
        }
    }

    public void updateListData(JsonArray listArray) {
        List<NodeBuilder> childQueue = new ArrayList<>();
        for (Object o : listArray) {
            if (o instanceof JsonArray) {
                JsonArray m = (JsonArray) o;

                String key = m.get(0);
                Object mvalue;

                if (m.size() > 1) {
                    mvalue = m.get(1);
                } else {
                    mvalue = new JsonObject();
                }

                Value value = ValueUtils.toValue(mvalue);

                if (value == null) {
                    value = new Value((String) null);
                }

                //noinspection StatementWithEmptyBody
                if (key.equals("$is")) {
                    //node.setProfile(value.getString());
                } else if (key.equals("$type")) {
                    node.setValueType(ValueType.toValueType(value.getString()));
                } else if (key.equals("$name")) {
                    node.setDisplayName(value.getString());
                } else if (key.equals("$invokable")) {
                    Permission perm = Permission.toEnum(value.getString());
                    Action act = getOrCreateAction(node, perm, false);
                    act.setPermission(perm);
                } else if (key.equals("$columns")) {
                    if (mvalue instanceof JsonArray) {
                        JsonArray array = (JsonArray) mvalue;
                        Action act = getOrCreateAction(node, Permission.NONE, false);
                        iterateActionMetaData(act, array, true);
                    }
                } else if (key.equals("$writable")) {
                    String string = value.getString();
                    node.setWritable(Writable.toEnum(string));
                } else if (key.equals("$params")) {
                    if (mvalue instanceof JsonArray) {
                        JsonArray array = (JsonArray) mvalue;
                        Action act = getOrCreateAction(node, Permission.NONE, false);
                        iterateActionMetaData(act, array, false);
                    }
                } else if (key.equals("$hidden")) {
                    node.setHidden(value.getBool());
                } else if (key.equals("$result")) {
                    String string = value.getString();
                    Action act = getOrCreateAction(node, Permission.NONE, false);
                    act.setResultType(ResultType.toEnum(string));
                } else if (key.startsWith("$$")) {
                    node.setRoConfig(key.substring(2), value);
                } else if (key.startsWith("$")) {
                    node.setConfig(key.substring(1), value);
                } else if (key.startsWith("@")) {
                    node.setAttribute(key.substring(1), value);
                } else {
                    Node child = node.getChild(key);

                    if (child == null) {
                        NodeBuilder builder = node.createChild(key);
                        if (mvalue instanceof JsonObject) {
                            JsonObject co = (JsonObject) mvalue;
                            for (Map.Entry<String, Object> entry : co) {
                                applyCreatedAttribute(builder, entry.getKey(), entry.getValue());
                            }
                        }
                        builder.setSerializable(false);
                    } else {
                        if (mvalue instanceof JsonObject) {
                            JsonObject co = (JsonObject) mvalue;
                            for (Map.Entry<String, Object> entry : co) {
                                applyAttribute(child, entry.getKey(), entry.getValue(), true);
                            }
                        }
                    }
                }
            } else if (o instanceof JsonObject) {
                JsonObject obj = (JsonObject) o;
                if (obj.contains("change") && obj.get("change").equals("remove")) {
                    String key = obj.get("name");

                    if (key.startsWith("$$")) {
                        node.removeRoConfig(key.substring(2));
                    } else if (key.startsWith("$")) {
                        node.removeConfig(key.substring(1));
                    } else if (key.startsWith("@")) {
                        node.removeAttribute(key.substring(1));
                    } else {
                        try {
                            node.removeChild(URLEncoder.encode(key, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        NodeBuilders.applyMultiChildBuilders((AmqpFakeNode) node, childQueue);
    }

    public void applyCreatedAttribute(NodeBuilder n, String key, Object mvalue) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$is")) {
            //node.setProfile(value.getString());
        } else if (key.equals("$type")) {
            n.setValueType(ValueType.toValueType(value.getString()));
        } else if (key.equals("$name")) {
            n.setDisplayName(value.getString());
        } else if (key.equals("$invokable")) {
            Permission perm = Permission.toEnum(value.getString());
            Action act = getOrCreateAction(n.getChild(), perm, true);
            act.setPermission(perm);
        } else if (key.equals("$columns")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            iterateActionMetaData(act, array, true);
        } else if (key.equals("$writable")) {
            String string = value.getString();
            n.setWritable(Writable.toEnum(string));
        } else if (key.equals("$params")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            iterateActionMetaData(act, array, false);
        } else if (key.equals("$hidden")) {
            n.setHidden(value.getBool());
        } else if (key.equals("$result")) {
            String string = value.getString();
            Action act = getOrCreateAction(n.getChild(), Permission.NONE, true);
            act.setResultType(ResultType.toEnum(string));
        } else if (key.startsWith("$$")) {
            n.setRoConfig(key.substring(2), value);
        } else if (key.startsWith("$")) {
            n.setConfig(key.substring(1), value);
        } else if (key.startsWith("@")) {
            n.setAttribute(key.substring(1), value);
        }
    }

    public void applyAttribute(Node n, String key, Object mvalue, boolean isChild) {
        Value value = ValueUtils.toValue(mvalue);

        if (value == null) {
            value = new Value((String) null);
        }

        if (key.equals("$is")) {
            //node.setProfile(value.getString());
        } else if (key.equals("$type")) {
            n.setValueType(ValueType.toValueType(value.getString()));
        } else if (key.equals("$name")) {
            n.setDisplayName(value.getString());
        } else if (key.equals("$invokable")) {
            Permission perm = Permission.toEnum(value.getString());
            Action act = getOrCreateAction(n, perm, isChild);
            act.setPermission(perm);
        } else if (key.equals("$columns")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            iterateActionMetaData(act, array, true);
        } else if (key.equals("$writable")) {
            String string = value.getString();
            n.setWritable(Writable.toEnum(string));
        } else if (key.equals("$params")) {
            JsonArray array = (JsonArray) mvalue;
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            iterateActionMetaData(act, array, false);
        } else if (key.equals("$hidden")) {
            n.setHidden(value.getBool());
        } else if (key.equals("$result")) {
            String string = value.getString();
            Action act = getOrCreateAction(n, Permission.NONE, isChild);
            act.setResultType(ResultType.toEnum(string));
        } else if (key.startsWith("$$")) {
            n.setRoConfig(key.substring(2), value);
        } else if (key.startsWith("$")) {
            n.setConfig(key.substring(1), value);
        } else if (key.startsWith("@")) {
            n.setAttribute(key.substring(1), value);
        }
    }

    public void updateValueData(JsonArray valueArray) {
        Value val = ValueUtils.toValue(valueArray.get(0), (String) valueArray.get(1));

        if (!val.getType().getRawName().equals(node.getValueType().getRawName())) {
            node.setValueType(val.getType());
        }

        node.setValue(val);
    }

    private String listQueue;
    private String subscribeQueue;
    private String subscribeQueueConsumer;
    private String listQueueConsumer;

    public void startListHandles() {
        hasListHandle = true;

        try {
            listQueue = controller.getChannel().queueDeclare().getQueue();
            listQueueConsumer = controller.getChannel().basicConsume(listQueue, listHandler);
            String exchange = controller.getBrokerPrefix("list."  + dsaPath);

            System.out.println("SEND TO " + exchange);

            controller.getChannel().exchangeDeclare(exchange, "fanout");
            controller.getChannel().queueBind(listQueue, exchange, "");
            controller.sendBrokerRequest(new String[] {
                    "list",
                    dsaPath,
                    listQueue
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeListHandles() {
        hasListHandle = false;

        try {
            controller.getChannel().basicCancel(listQueueConsumer);
            controller.getChannel().queueDelete(listQueue);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startSubscribeHandles() {
        hasSubscribeHandle = true;

        try {
            subscribeQueue = controller.getChannel().queueDeclare().getQueue();
            subscribeQueueConsumer = controller.getChannel().basicConsume(subscribeQueue, subscribeHandler);
            String exchange = controller.getBrokerPrefix("subscribe."  + dsaPath);

            controller.getChannel().exchangeDeclare(exchange, "fanout");
            controller.getChannel().queueBind(subscribeQueue, exchange, "");
            controller.sendBrokerRequest(new String[] {
                    "subscribe",
                    dsaPath,
                    subscribeQueue
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void closeSubscribeHandles() {
        hasSubscribeHandle = true;

        try {
            controller.getChannel().basicCancel(subscribeQueueConsumer);
            controller.getChannel().queueDelete(subscribeQueue);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadNow() {
    }

    private Action getOrCreateAction(Node node, Permission perm, boolean isChild) {
        Action action = node.getAction();
        if (action != null) {
            return action;
        }

        action = getRawAction(node, perm, isChild);
        node.setAction(action);
        return action;
    }

    private Action getRawAction(final Node node, Permission perm, final boolean isChild) {
        return new Action(perm, new Handler<ActionResult>() {
            @Override
            public void handle(final ActionResult event) {
                event.getTable().close();
            }
        });
    }

    private static void iterateActionMetaData(Action act,
                                              JsonArray array,
                                              boolean isCol) {
        ArrayList<Parameter> out = new ArrayList<>();
        for (Object anArray : array) {
            JsonObject data = (JsonObject) anArray;
            String name = data.get("name");
            String type = data.get("type");
            ValueType valType = ValueType.toValueType(type);
            Parameter param = new Parameter(name, valType);
            if (isCol) {
                out.add(param);
            } else {
                String editor = data.get("editor");
                if (editor != null) {
                    param.setEditorType(EditorType.make(editor));
                }
                Object def = data.get("default");
                if (def != null) {
                    param.setDefaultValue(ValueUtils.toValue(def));
                }
                out.add(param);
            }
        }

        if (isCol) {
            act.setColumns(out);
        } else {
            act.setParams(out);
        }
    }

    public AmqpListHandler getListHandler() {
        return listHandler;
    }

    public AmqpSubscribeHandler getSubscribeHandler() {
        return subscribeHandler;
    }

    public class AmqpListHandler extends DefaultConsumer {
        public AmqpListHandler(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            super.handleDelivery(consumerTag, envelope, properties, body);

            try {
                JsonArray resp = new JsonArray(EncodingFormat.MESSAGE_PACK, body);

                updateListData(resp);
            } catch (Exception e) {
                System.err.println("Error while handling AMQP response at " + dsaPath);
                e.printStackTrace();
            }

            getChannel().basicAck(envelope.getDeliveryTag(), false);
        }
    }

    public class AmqpSubscribeHandler extends DefaultConsumer {
        public AmqpSubscribeHandler(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            super.handleDelivery(consumerTag, envelope, properties, body);

            try {
                JsonArray resp = new JsonArray(EncodingFormat.MESSAGE_PACK, body);

                updateValueData(resp);
            } catch (Exception e) {
                System.err.println("Error while handling AMQP response at " + dsaPath);
                e.printStackTrace();
            }

            getChannel().basicAck(envelope.getDeliveryTag(), false);
        }
    }
}
