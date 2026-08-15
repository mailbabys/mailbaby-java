package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of a batch email delivery.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BatchResult {

    @JsonProperty("total")
    private int total;

    @JsonProperty("succeeded")
    private int succeeded;

    @JsonProperty("failed")
    private int failed;

    @JsonProperty("results")
    private List<SendResult> results;

    public BatchResult() {
    }

    public BatchResult(int total, int succeeded, int failed, List<SendResult> results) {
        this.total = total;
        this.succeeded = succeeded;
        this.failed = failed;
        this.results = results;
    }

    public int getTotal() {
        return total;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public int getFailed() {
        return failed;
    }

    public List<SendResult> getResults() {
        return results;
    }

    @Override
    public String toString() {
        return "BatchResult{total=" + total + ", succeeded=" + succeeded + ", failed=" + failed + '}';
    }
}