package com.mailbaby.client.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailbaby.client.model.Attachment;
import com.mailbaby.client.model.EmailMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link EmailMessage} serializes to the exact JSON schema the MailBaby
 * queue consumer expects (matching the Go {@code sender.Email} struct's snake_case
 * tags, with byte fields as base64 strings).
 */
class EmailJsonMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAllFieldsInSnakeCase() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Priority", "1");
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("tenant", "acme");

        Attachment att = new Attachment("hello.txt", "text/plain", "Hello, world!".getBytes(StandardCharsets.UTF_8));

        EmailMessage message = EmailMessage.builder()
                .id("abc-123")
                .account("marketing")
                .from("noreply@example.com")
                .fromName("MailBaby")
                .replyTo("support@example.com")
                .to(List.of("alice@example.com", "bob@example.com"))
                .cc(List.of("manager@example.com"))
                .subject("Order Confirmation")
                .textBody("Thank you!")
                .htmlBody("<h2>Thanks</h2>")
                .headers(headers)
                .attachment(att)
                .tag("order")
                .tag("receipt")
                .metadata(metadata)
                .build();

        JsonNode node = mapper.readTree(EmailJsonMapper.toJsonBytes(message));

        assertThat(node.get("id").asText()).isEqualTo("abc-123");
        assertThat(node.get("account").asText()).isEqualTo("marketing");
        assertThat(node.get("from").asText()).isEqualTo("noreply@example.com");
        assertThat(node.get("from_name").asText()).isEqualTo("MailBaby");
        assertThat(node.get("reply_to").asText()).isEqualTo("support@example.com");
        assertThat(toList(node.get("to"))).containsExactly("alice@example.com", "bob@example.com");
        assertThat(toList(node.get("cc"))).containsExactly("manager@example.com");
        assertThat(node.get("subject").asText()).isEqualTo("Order Confirmation");
        assertThat(node.get("text_body").asText()).isEqualTo("Thank you!");
        assertThat(node.get("html_body").asText()).isEqualTo("<h2>Thanks</h2>");
        assertThat(node.get("headers").get("X-Priority").asText()).isEqualTo("1");
        assertThat(toList(node.get("tags"))).containsExactly("order", "receipt");
        assertThat(node.get("metadata").get("tenant").asText()).isEqualTo("acme");

        JsonNode attNode = node.get("attachments").get(0);
        assertThat(attNode.get("filename").asText()).isEqualTo("hello.txt");
        assertThat(attNode.get("content_type").asText()).isEqualTo("text/plain");
        // attachments.data must be base64-encoded bytes
        byte[] decoded = Base64.getDecoder().decode(attNode.get("data").asText());
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Hello, world!");
        // default inline is false (and omitted by Go; we keep it for parity)
        assertThat(attNode.has("inline")).isTrue();
        assertThat(attNode.get("inline").asBoolean()).isFalse();
    }

    @Test
    void omitsEmptyFields() throws Exception {
        EmailMessage message = EmailMessage.builder()
                .to(List.of("alice@example.com"))
                .subject("Hi")
                .build();
        JsonNode node = mapper.readTree(EmailJsonMapper.toJsonBytes(message));
        assertThat(node.has("cc")).isFalse();
        assertThat(node.has("bcc")).isFalse();
        assertThat(node.has("headers")).isFalse();
        assertThat(node.has("attachments")).isFalse();
        assertThat(node.has("tags")).isFalse();
        assertThat(node.has("metadata")).isFalse();
        assertThat(node.get("to").isArray()).isTrue();
        assertThat(node.get("to").get(0).asText()).isEqualTo("alice@example.com");
        assertThat(node.get("subject").asText()).isEqualTo("Hi");
    }

    @Test
    void roundTripPreservesFields() throws Exception {
        EmailMessage original = EmailMessage.builder()
                .id("xyz")
                .to(List.of("a@b.com"))
                .subject("S")
                .textBody("hello")
                .htmlBody("<p>hi</p>")
                .header("X-Tenant", "t1")
                .attachment(new Attachment("a.bin", "application/octet-stream", new byte[]{1, 2, 3}, true, "img-1"))
                .tag("a")
                .metadata("k", "v")
                .build();

        EmailMessage parsed = EmailJsonMapper.fromJson(EmailJsonMapper.toJsonBytes(original));
        assertThat(parsed.getId()).isEqualTo("xyz");
        assertThat(parsed.getTo()).containsExactly("a@b.com");
        assertThat(parsed.getSubject()).isEqualTo("S");
        assertThat(parsed.getTextBody()).isEqualTo("hello");
        assertThat(parsed.getHtmlBody()).isEqualTo("<p>hi</p>");
        assertThat(parsed.getHeaders()).containsEntry("X-Tenant", "t1");
        assertThat(parsed.getAttachments()).hasSize(1);
        assertThat(parsed.getAttachments().get(0).getFilename()).isEqualTo("a.bin");
        assertThat(parsed.getAttachments().get(0).getData()).containsExactly(1, 2, 3);
        assertThat(parsed.getAttachments().get(0).isInline()).isTrue();
        assertThat(parsed.getAttachments().get(0).getContentId()).isEqualTo("img-1");
        assertThat(parsed.getTags()).containsExactly("a");
        assertThat(parsed.getMetadata()).containsEntry("k", "v");
    }

    private static List<String> toList(JsonNode array) {
        java.util.List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}