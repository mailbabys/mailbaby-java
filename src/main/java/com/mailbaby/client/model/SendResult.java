package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a single email delivery: {@code status} is {@code "sent"} or {@code "queued"},
 * {@code sentAt} is a Unix timestamp in nanoseconds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SendResult {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sent_at")
    private long sentAt;

    public SendResult() {
    }

    public SendResult(String id, String status, String message, long sentAt) {
        this.id = id;
        this.status = status;
        this.message = message;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getSentAt() {
        return sentAt;
    }

    public boolean isQueued() {
        return "queued".equals(status);
    }

    public boolean isSent() {
        return "sent".equals(status);
    }

    @Override
    public String toString() {
        return "SendResult{id='" + id + "', status='" + status + "', message='" + message + "', sentAt=" + sentAt + '}';
    }
}