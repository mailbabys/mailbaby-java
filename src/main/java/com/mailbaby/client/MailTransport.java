package com.mailbaby.client;

import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.BatchResult;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.HealthResult;
import com.mailbaby.client.model.PingResult;
import com.mailbaby.client.model.SendResult;

import java.util.List;

/**
 * A MailBaby backend transport. Implementations: REST HTTP and gRPC.
 */
public interface MailTransport extends AutoCloseable {

    /**
     * Sends a single email. When {@code async} is true the message is enqueued
     * and the result status is {@code "queued"}.
     */
    SendResult send(EmailMessage message, boolean async) throws MailBabyException;

    /**
     * Sends multiple emails in a single request, returning per-message results.
     */
    BatchResult sendBatch(List<EmailMessage> messages, boolean async) throws MailBabyException;

    /**
     * Probes service liveness.
     */
    PingResult ping() throws MailBabyException;

    /**
     * Checks service readiness and dependency health.
     */
    HealthResult healthCheck() throws MailBabyException;

    @Override
    void close();
}