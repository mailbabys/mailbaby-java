package com.mailbaby.client.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailbaby.client.MailTransport;
import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.BatchResult;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.HealthResult;
import com.mailbaby.client.model.PingResult;
import com.mailbaby.client.model.SendResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST transport over the JDK {@link HttpClient}.
 *
 * <p>Endpoints: {@code POST /v1/email/send}, {@code POST /v1/email/batch},
 * {@code GET /livez}, {@code GET /readyz}. Authentication via {@code X-API-Key}
 * header (or a custom header name / {@code Authorization: Bearer}).
 */
public class RestMailTransport implements MailTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String headerName;
    private final Duration requestTimeout;

    /**
     * @param baseUrl    e.g. {@code http://localhost:8080}
     * @param apiKey     secret key; may be {@code null}
     * @param headerName header used for the key (defaults to {@code X-API-Key});
     *                   use {@code Authorization} to send a Bearer token
     * @param timeout    connect/request timeout; may be {@code null}
     */
    public RestMailTransport(String baseUrl, String apiKey, String headerName, Duration timeout) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.headerName = headerName == null || headerName.isBlank() ? "X-API-Key" : headerName;
        this.requestTimeout = timeout;
        HttpClient.Builder b = HttpClient.newBuilder();
        if (timeout != null) {
            b.connectTimeout(timeout);
        }
        this.httpClient = b.build();
    }

    @Override
    public SendResult send(EmailMessage message, boolean async) throws MailBabyException {
        String url = baseUrl + "/v1/email/send" + (async ? "?async=true" : "");
        return post(url, writeJson(message), SendResult.class);
    }

    @Override
    public BatchResult sendBatch(List<EmailMessage> messages, boolean async) throws MailBabyException {
        String url = baseUrl + "/v1/email/batch" + (async ? "?async=true" : "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emails", messages);
        body.put("async", async);
        return post(url, writeJson(body), BatchResult.class);
    }

    @Override
    public PingResult ping() throws MailBabyException {
        // /livez returns {status, timestamp(RFC3339 string)}; the timestamp is not represented
        // in PingResult (which mirrors the gRPC Ping shape) so we parse leniently.
        String body = getRaw(baseUrl + "/livez");
        try {
            JsonNode node = MAPPER.readTree(body);
            String status = node.path("status").asText("");
            return new PingResult(status, null, 0L);
        } catch (JsonProcessingException e) {
            throw new MailBabyException("invalid_response", "failed to parse /livez response", e);
        }
    }

    @Override
    public HealthResult healthCheck() throws MailBabyException {
        // /readyz returns {status, components, timestamp(RFC3339 string)}; timestamp is ignored.
        String body = getRaw(baseUrl + "/readyz");
        try {
            JsonNode node = MAPPER.readTree(body);
            String status = node.path("status").asText("");
            JsonNode components = node.path("components");
            java.util.Map<String, String> compMap = new java.util.LinkedHashMap<>();
            if (components.isObject()) {
                components.fields().forEachRemaining(e -> compMap.put(e.getKey(), e.getValue().asText()));
            }
            return new HealthResult(status, compMap.isEmpty() ? null : compMap, null);
        } catch (JsonProcessingException e) {
            throw new MailBabyException("invalid_response", "failed to parse /readyz response", e);
        }
    }

    private String getRaw(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json");
        if (requestTimeout != null) {
            b.timeout(requestTimeout);
        }
        applyAuth(b);
        HttpResponse<String> response;
        try {
            response = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MailBabyException("transport_error", "HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailBabyException("transport_error", "HTTP request interrupted", e);
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        throw toException(status, response.body());
    }

    private String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new MailBabyException("serialization_failed", "failed to serialize request body", e);
        }
    }

    private <T> T get(String url, Class<T> type) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json");
        if (requestTimeout != null) {
            b.timeout(requestTimeout);
        }
        applyAuth(b);
        return execute(b.build(), type);
    }

    private <T> T post(String url, String jsonBody, Class<T> type) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (requestTimeout != null) {
            b.timeout(requestTimeout);
        }
        applyAuth(b);
        return execute(b.build(), type);
    }

    private void applyAuth(HttpRequest.Builder b) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if ("Authorization".equalsIgnoreCase(headerName)) {
            b.header("Authorization", "Bearer " + apiKey);
        } else {
            b.header(headerName, apiKey);
        }
    }

    private <T> T execute(HttpRequest request, Class<T> type) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MailBabyException("transport_error", "HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailBabyException("transport_error", "HTTP request interrupted", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return MAPPER.readValue(response.body(), type);
            } catch (JsonProcessingException e) {
                throw new MailBabyException("invalid_response",
                        "failed to parse response with HTTP " + status, e);
            }
        }
        throw toException(status, response.body());
    }

    private MailBabyException toException(int status, String body) {
        ServerError error = null;
        if (body != null && !body.isBlank()) {
            try {
                error = MAPPER.readValue(body, ServerError.class);
            } catch (JsonProcessingException ignored) {
                // fall through with plain body text
            }
        }
        if (error != null && error.error != null) {
            return MailBabyException.http(status, error.error, error.error, error.details);
        }
        return MailBabyException.http(status, "http_" + status, "request failed with HTTP " + status, body);
    }

    /** Server error payload: {@code {code, error, details}} or {@code {code, error, message}}. */
    @SuppressWarnings("unused")
    private static final class ServerError {
        public int code;
        public String error;
        public String message;
        public String details;

        private ServerError() {
        }
    }

    public String baseUrlForReuse() {
        return baseUrl;
    }

    public String apiKeyForReuse() {
        return apiKey;
    }

    public String headerNameForReuse() {
        return headerName;
    }

    public Duration timeoutForReuse() {
        return requestTimeout;
    }

    @Override
    public void close() {
        // java.net.http.HttpClient holds no resources requiring explicit shutdown
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            throw new IllegalArgumentException("baseUrl must not be null");
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}