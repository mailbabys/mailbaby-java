package com.mailbaby.client;

import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.grpc.GrpcMailTransport;
import com.mailbaby.client.model.BatchResult;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.HealthResult;
import com.mailbaby.client.model.PingResult;
import com.mailbaby.client.model.SendResult;
import com.mailbaby.client.rest.RestMailTransport;
import io.grpc.ManagedChannel;

import java.time.Duration;
import java.util.List;

/**
 * High-level facade for MailBaby. Choose a transport with {@link Builder#rest} or
 * {@link Builder#grpc}; the resulting client supports sending, batching, and
 * health probes through a single entry point.
 */
public final class MailBabyClient implements AutoCloseable {

    private final MailTransport transport;

    private MailBabyClient(MailTransport transport) {
        this.transport = transport;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Sends synchronously (blocks until SMTP acknowledges). */
    public SendResult send(EmailMessage message) {
        return transport.send(message, false);
    }

    /** Sends asynchronously (enqueues and returns immediately). */
    public SendResult sendAsync(EmailMessage message) {
        return transport.send(message, true);
    }

    public BatchResult sendBatch(List<EmailMessage> messages) {
        return transport.sendBatch(messages, false);
    }

    public BatchResult sendBatchAsync(List<EmailMessage> messages) {
        return transport.sendBatch(messages, true);
    }

    public PingResult ping() {
        return transport.ping();
    }

    public HealthResult healthCheck() {
        return transport.healthCheck();
    }

    public MailTransport transport() {
        return transport;
    }

    @Override
    public void close() {
        transport.close();
    }

    public static final class Builder {
        private MailTransport built;

        public Builder rest(String baseUrl) {
            this.built = new RestMailTransport(baseUrl, null, null, null);
            return this;
        }

        public Builder rest(String baseUrl, String apiKey) {
            this.built = new RestMailTransport(baseUrl, apiKey, null, null);
            return this;
        }

        public Builder rest(String baseUrl, String apiKey, Duration timeout) {
            this.built = new RestMailTransport(baseUrl, apiKey, null, timeout);
            return this;
        }

        public Builder grpc(String host, int port) {
            this.built = new GrpcMailTransport(host, port, null, null);
            return this;
        }

        public Builder grpc(String host, int port, String apiKey) {
            this.built = new GrpcMailTransport(host, port, apiKey, null);
            return this;
        }

        public Builder grpc(ManagedChannel channel) {
            this.built = new GrpcMailTransport(channel, null, null);
            return this;
        }

        public Builder grpc(ManagedChannel channel, String apiKey) {
            this.built = new GrpcMailTransport(channel, apiKey, null);
            return this;
        }

        /**
         * Configures authentication on the most recently selected transport.
         * Useful after {@link #rest} / {@link #grpc} when only the key is known later.
         */
        public Builder apiKey(String apiKey) {
            if (built instanceof RestMailTransport r) {
                this.built = new RestMailTransport(r.baseUrlForReuse(), apiKey, r.headerNameForReuse(), r.timeoutForReuse());
            } else if (built instanceof GrpcMailTransport g) {
                this.built = new GrpcMailTransport(g.channelForReuse(), apiKey, null);
            } else {
                throw new IllegalStateException("call .rest() or .grpc() before .apiKey()");
            }
            return this;
        }

        public Builder headerName(String headerName) {
            if (built instanceof RestMailTransport r) {
                this.built = new RestMailTransport(r.baseUrlForReuse(), r.apiKeyForReuse(), headerName, r.timeoutForReuse());
            } else if (built instanceof GrpcMailTransport g) {
                this.built = new GrpcMailTransport(g.channelForReuse(), g.apiKeyForReuse(), headerName);
            } else {
                throw new IllegalStateException("call .rest() or .grpc() before .headerName()");
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            if (built instanceof RestMailTransport r) {
                this.built = new RestMailTransport(r.baseUrlForReuse(), r.apiKeyForReuse(), r.headerNameForReuse(), timeout);
            } else {
                throw new IllegalStateException("timeout() only applies to REST transport");
            }
            return this;
        }

        public Builder transport(MailTransport transport) {
            this.built = transport;
            return this;
        }

        public MailBabyClient build() {
            if (built == null) {
                throw new IllegalStateException("transport not configured: call .rest() or .grpc() first");
            }
            return new MailBabyClient(built);
        }
    }
}