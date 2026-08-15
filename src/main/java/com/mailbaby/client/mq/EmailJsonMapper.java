package com.mailbaby.client.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.EmailMessage;

/**
 * Serializes {@link EmailMessage} to the exact JSON schema MailBaby's queue consumers
 * expect (the Go {@code sender.Email} struct: snake_case fields, base64 attachment data).
 */
public final class EmailJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EmailJsonMapper() {
    }

    public static byte[] toJsonBytes(EmailMessage message) {
        try {
            return MAPPER.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new MailBabyException("serialization_failed", "failed to serialize email to JSON", e);
        }
    }

    public static String toJsonString(EmailMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new MailBabyException("serialization_failed", "failed to serialize email to JSON", e);
        }
    }

    public static EmailMessage fromJson(byte[] json) {
        try {
            return MAPPER.readValue(json, EmailMessage.class);
        } catch (java.io.IOException e) {
            throw new MailBabyException("deserialization_failed", "failed to parse email JSON", e);
        }
    }
}