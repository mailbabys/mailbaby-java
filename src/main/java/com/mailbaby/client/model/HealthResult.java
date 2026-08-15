package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Result of a health check. For the REST transport the status string is
 * {@code "UP"} / {@code "DOWN"}; for gRPC it mirrors {@code HealthCheckResponse.ServingStatus}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HealthResult {

    public enum ServingStatus {
        UNKNOWN,
        SERVING,
        NOT_SERVING,
        SERVICE_UNKNOWN
    }

    @JsonProperty("status")
    private String status;

    @JsonProperty("components")
    private Map<String, String> components;

    @JsonProperty("details")
    private Map<String, String> details;

    public HealthResult() {
    }

    public HealthResult(String status, Map<String, String> components, Map<String, String> details) {
        this.status = status;
        this.components = components;
        this.details = details;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, String> getComponents() {
        return components;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public boolean isHealthy() {
        return "UP".equalsIgnoreCase(status) || "SERVING".equalsIgnoreCase(status);
    }

    public ServingStatus servingStatus() {
        if (status == null) {
            return ServingStatus.UNKNOWN;
        }
        try {
            return ServingStatus.valueOf(status.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return ServingStatus.UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return "HealthResult{status='" + status + "', components=" + components + '}';
    }
}