package com.mailbaby.client.mq;

import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.EmailMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ {@link EmailProducer}. Default exchange "" (default direct exchange) with
 * a configurable routing key is sufficient for the typical {@code mailbaby_tasks} queue.
 *
 * <p>Connection handling: a single AMQP connection + channel is held lazily and
 * re-established on failure; {@link #close()} shuts the channel and connection down.
 */
public class RabbitMqEmailProducer implements EmailProducer {

    private final ConnectionFactory factory;
    private final String exchange;
    private final String routingKey;
    private final String queue;
    private final boolean durable;
    private final boolean publisherConfirms;

    private Connection connection;
    private Channel channel;

    public RabbitMqEmailProducer(String uri, String exchange, String routingKey,
                                 String queue, boolean durable, boolean publisherConfirms) {
        this.factory = new ConnectionFactory();
        try {
            this.factory.setUri(uri);
        } catch (Exception e) {
            throw new MailBabyException("invalid_config", "invalid AMQP URI: " + e.getMessage(), e);
        }
        this.exchange = exchange == null ? "" : exchange;
        this.routingKey = routingKey == null ? "mailbaby_tasks" : routingKey;
        this.queue = queue;
        this.durable = durable;
        this.publisherConfirms = publisherConfirms;
    }

    public RabbitMqEmailProducer(String uri) {
        this(uri, "", "mailbaby_tasks", null, true, false);
    }

    /** Test seam: inject a pre-configured factory (e.g. with mocked connections). */
    RabbitMqEmailProducer(ConnectionFactory factory, String exchange, String routingKey,
                          String queue, boolean durable, boolean publisherConfirms) {
        this.factory = factory;
        this.exchange = exchange == null ? "" : exchange;
        this.routingKey = routingKey == null ? "mailbaby_tasks" : routingKey;
        this.queue = queue;
        this.durable = durable;
        this.publisherConfirms = publisherConfirms;
    }

    @Override
    public synchronized void publish(EmailMessage message) {
        byte[] payload = EmailJsonMapper.toJsonBytes(message);
        try {
            ensureChannel();
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(durable ? 2 : 1)
                    .messageId(message.getId())
                    .build();
            if (publisherConfirms) {
                channel.confirmSelect();
            }
            channel.basicPublish(exchange, routingKey, props, payload);
            if (publisherConfirms) {
                channel.waitForConfirmsOrDie(10_000);
            }
        } catch (IOException | InterruptedException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            resetChannel();
            throw new MailBabyException("publish_failed", "failed to publish to RabbitMQ: " + e.getMessage(), e);
        }
    }

    private void ensureChannel() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen() && connection != null && connection.isOpen()) {
            return;
        }
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
        if (queue != null && !queue.isBlank()) {
            channel.queueDeclare(queue, durable, false, false, null);
        }
    }

    private void resetChannel() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
            // ignore
        }
        channel = null;
        connection = null;
    }

    @Override
    public synchronized void close() {
        resetChannel();
    }
}