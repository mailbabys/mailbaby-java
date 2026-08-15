package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of the {@code Ping} liveness probe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PingResult {

    @JsonProperty("status")
    private String status;

    @JsonProperty("version")
    private String version;

    @JsonProperty("timestamp")
    private long timestamp;

    public PingResult() {
    }

    public PingResult(String status, String version, long timestamp) {
        this.status = status;
        this.version = version;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PingResult{status='" + status + "', version='" + version + "', timestamp=" + timestamp + '}';
    }
}