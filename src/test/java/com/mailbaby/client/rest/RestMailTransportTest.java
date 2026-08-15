package com.mailbaby.client.rest;

import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.SendResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestMailTransportTest {

    private MockWebServer server;
    private RestMailTransport transport;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        server.shutdown();
    }

    @Test
    void sendPostsJsonWithApiKeyHeader() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"e1\",\"status\":\"sent\",\"message\":\"email sent successfully\",\"sent_at\":1771142400000000000}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), "secret-token", null, null);

        EmailMessage msg = EmailMessage.builder()
                .to(List.of("alice@example.com"))
                .subject("Hello")
                .textBody("Hi")
                .build();
        SendResult result = transport.send(msg, false);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/v1/email/send");
        assertThat(req.getHeader("X-API-Key")).isEqualTo("secret-token");
        assertThat(req.getHeader("Content-Type")).isEqualTo("application/json");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"to\":[\"alice@example.com\"]");
        assertThat(body).contains("\"subject\":\"Hello\"");
        assertThat(body).contains("\"text_body\":\"Hi\"");

        assertThat(result.getId()).isEqualTo("e1");
        assertThat(result.getStatus()).isEqualTo("sent");
        assertThat(result.getSentAt()).isEqualTo(1771142400000000000L);
    }

    @Test
    void sendAsyncAddsQueryParam() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(202)
                .setBody("{\"id\":\"q1\",\"status\":\"queued\",\"message\":\"email enqueued successfully\",\"sent_at\":42}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), null, null, null);

        SendResult result = transport.send(
                EmailMessage.builder().to(List.of("a@b.com")).subject("s").build(), true);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getPath()).isEqualTo("/v1/email/send?async=true");
        assertThat(req.getHeader("X-API-Key")).isNull();
        assertThat(result.getStatus()).isEqualTo("queued");
        assertThat(result.isQueued()).isTrue();
    }

    @Test
    void sendBatchWrapsInEmailsKey() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"total\":2,\"succeeded\":2,\"failed\":0,\"results\":[" +
                        "{\"id\":\"r1\",\"status\":\"sent\",\"message\":\"ok\",\"sent_at\":1}," +
                        "{\"id\":\"r2\",\"status\":\"sent\",\"message\":\"ok\",\"sent_at\":2}]}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), null, null, null);

        var r = transport.sendBatch(List.of(
                EmailMessage.builder().to(List.of("a@b.com")).subject("s1").build(),
                EmailMessage.builder().to(List.of("c@d.com")).subject("s2").build()
        ), false);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getPath()).isEqualTo("/v1/email/batch");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"emails\":[");
        assertThat(body).contains("\"async\":false");
        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getSucceeded()).isEqualTo(2);
        assertThat(r.getResults()).hasSize(2);
    }

    @Test
    void pingHitsLivez() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"UP\",\"timestamp\":\"2025-01-01T00:00:00Z\"}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), null, null, null);

        var ping = transport.ping();

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/livez");
        assertThat(ping.getStatus()).isEqualTo("UP");
    }

    @Test
    void healthCheckHitsReadyz() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"status\":\"UP\",\"components\":{\"smtp\":\"UP\",\"queue\":\"UP\"},\"timestamp\":\"x\"}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), null, null, null);

        var h = transport.healthCheck();

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getPath()).isEqualTo("/readyz");
        assertThat(h.getStatus()).isEqualTo("UP");
        assertThat(h.getComponents()).containsEntry("smtp", "UP");
        assertThat(h.isHealthy()).isTrue();
    }

    @Test
    void serverErrorMapsToMailBabyException() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"code\":400,\"error\":\"validation_error\",\"details\":\"to is required\"}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), null, null, null);

        assertThatThrownBy(() -> transport.send(
                EmailMessage.builder().to(List.of()).subject("s").build(), false))
                .isInstanceOf(MailBabyException.class)
                .hasMessageContaining("validation_error")
                .extracting("status").isEqualTo(400);
    }

    @Test
    void authorizationHeaderSentWhenConfigured() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"x\",\"status\":\"sent\",\"message\":\"ok\",\"sent_at\":0}")
                .addHeader("Content-Type", "application/json"));
        transport = new RestMailTransport(server.url("").toString(), "the-key", "Authorization", null);

        transport.send(EmailMessage.builder().to(List.of("a@b.com")).subject("s").build(), false);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer the-key");
    }
}