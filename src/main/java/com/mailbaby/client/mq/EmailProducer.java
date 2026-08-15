package com.mailbaby.client.mq;

import com.mailbaby.client.model.EmailMessage;

import java.io.Closeable;

/**
 * Publishes emails to a message broker so a MailBaby server can consume them.
 */
public interface EmailProducer extends Closeable {

    /**
     * Serializes the email to JSON (the same schema MailBaby's queue consumers expect)
     * and publishes it to the configured broker.
     */
    void publish(EmailMessage message);

    @Override
    void close();
}