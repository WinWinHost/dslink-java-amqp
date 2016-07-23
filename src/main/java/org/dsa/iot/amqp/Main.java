package org.dsa.iot.amqp;

import org.dsa.iot.dslink.DSLinkFactory;

public class Main {
    public static void main(String[] args) {
        DSLinkFactory.start(args, new AmqpHandler());
    }
}
