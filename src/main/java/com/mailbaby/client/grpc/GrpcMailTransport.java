package com.mailbaby.client.grpc;

import com.mailbaby.client.MailTransport;
import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.Attachment;
import com.mailbaby.client.model.BatchResult;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.HealthResult;
import com.mailbaby.client.model.PingResult;
import com.mailbaby.client.model.SendResult;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import mailbaby.v1.MailServiceGrpc;
import mailbaby.v1.Mailbaby;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC transport against the {@code mailbaby.v1.MailService} defined in
 * {@code src/main/proto/mailbaby.proto}. Authentication is injected as
 * metadata {@code authorization: Bearer &lt;key&gt;} (or a configurable
 * header name, mirroring the REST transport).
 */
public class GrpcMailTransport implements MailTransport {

    private final ManagedChannel channel;
    private final MailServiceGrpc.MailServiceBlockingStub stub;
    private final String apiKey;
    private final String headerName;

    public GrpcMailTransport(ManagedChannel channel, String apiKey, String headerName) {
        this.channel = channel;
        this.apiKey = apiKey;
        this.headerName = headerName == null || headerName.isBlank() ? "X-API-Key" : headerName;
        MailServiceGrpc.MailServiceBlockingStub base = MailServiceGrpc.newBlockingStub(channel);
        if (apiKey != null && !apiKey.isBlank()) {
            Metadata headers = new Metadata();
            Metadata.Key<String> key = Metadata.Key.of(this.headerName, Metadata.ASCII_STRING_MARSHALLER);
            if ("Authorization".equalsIgnoreCase(this.headerName)) {
                headers.put(key, "Bearer " + apiKey);
            } else {
                headers.put(key, apiKey);
            }
            this.stub = base.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        } else {
            this.stub = base;
        }
    }

    /** Convenience: plaintext channel to {@code host:port}. */
    public GrpcMailTransport(String host, int port, String apiKey, String headerName) {
        this(io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build(),
                apiKey, headerName);
    }

    @Override
    public SendResult send(EmailMessage message, boolean async) throws MailBabyException {
        Mailbaby.SendMailRequest req = populateFromMessage(Mailbaby.SendMailRequest.newBuilder()
                .setId(message.getId() == null ? "" : message.getId())
                .setAccount(message.getAccount() == null ? "" : message.getAccount())
                .setFrom(message.getFrom() == null ? "" : message.getFrom())
                .setFromName(message.getFromName() == null ? "" : message.getFromName())
                .setReplyTo(message.getReplyTo() == null ? "" : message.getReplyTo())
                .setSubject(message.getSubject() == null ? "" : message.getSubject())
                .setTextBody(message.getTextBody() == null ? "" : message.getTextBody())
                .setHtmlBody(message.getHtmlBody() == null ? "" : message.getHtmlBody())
                .setAsync(async), message).build();
        return invoke(() -> {
            Mailbaby.SendMailResponse r = stub.send(req);
            return new SendResult(r.getId(), r.getStatus(), r.getMessage(), r.getSentAt());
        });
    }

    @Override
    public BatchResult sendBatch(List<EmailMessage> messages, boolean async) throws MailBabyException {
        Mailbaby.BatchSendMailRequest.Builder req = Mailbaby.BatchSendMailRequest.newBuilder().setAsync(async);
        for (EmailMessage m : messages) {
            Mailbaby.SendMailRequest.Builder b = Mailbaby.SendMailRequest.newBuilder()
                    .setId(m.getId() == null ? "" : m.getId())
                    .setAccount(m.getAccount() == null ? "" : m.getAccount())
                    .setFrom(m.getFrom() == null ? "" : m.getFrom())
                    .setFromName(m.getFromName() == null ? "" : m.getFromName())
                    .setReplyTo(m.getReplyTo() == null ? "" : m.getReplyTo())
                    .setSubject(m.getSubject() == null ? "" : m.getSubject())
                    .setTextBody(m.getTextBody() == null ? "" : m.getTextBody())
                    .setHtmlBody(m.getHtmlBody() == null ? "" : m.getHtmlBody());
            req.addEmails(populateFromMessage(b, m).build());
        }
        return invoke(() -> {
            Mailbaby.BatchSendMailResponse r = stub.sendBatch(req.build());
            List<SendResult> results = new ArrayList<>(r.getResultsCount());
            for (Mailbaby.SendMailResponse item : r.getResultsList()) {
                results.add(new SendResult(item.getId(), item.getStatus(), item.getMessage(), item.getSentAt()));
            }
            return new BatchResult(r.getTotal(), r.getSucceeded(), r.getFailed(), results);
        });
    }

    @Override
    public PingResult ping() throws MailBabyException {
        return invoke(() -> {
            Mailbaby.PingResponse r = stub.ping(Mailbaby.PingRequest.getDefaultInstance());
            return new PingResult(r.getStatus(), r.getVersion(), r.getTimestamp());
        });
    }

    @Override
    public HealthResult healthCheck() throws MailBabyException {
        return invoke(() -> {
            Mailbaby.HealthCheckResponse r = stub.healthCheck(Mailbaby.HealthCheckRequest.getDefaultInstance());
            String status = r.getStatus().name();
            return new HealthResult(status, null, r.getDetailsMap());
        });
    }

    private static Mailbaby.SendMailRequest.Builder populateFromMessage(
            Mailbaby.SendMailRequest.Builder b, EmailMessage m) {
        if (m.getTo() != null) {
            b.addAllTo(m.getTo());
        }
        if (m.getCc() != null) {
            b.addAllCc(m.getCc());
        }
        if (m.getBcc() != null) {
            b.addAllBcc(m.getBcc());
        }
        if (m.getHeaders() != null) {
            b.putAllHeaders(m.getHeaders());
        }
        if (m.getAttachments() != null) {
            for (Attachment a : m.getAttachments()) {
                Mailbaby.Attachment.Builder ab = Mailbaby.Attachment.newBuilder()
                        .setFilename(a.getFilename() == null ? "" : a.getFilename())
                        .setContentType(a.getContentType() == null ? "" : a.getContentType())
                        .setInline(a.isInline());
                if (a.getContentId() != null) {
                    ab.setContentId(a.getContentId());
                }
                if (a.getData() != null) {
                    ab.setData(com.google.protobuf.ByteString.copyFrom(a.getData()));
                }
                b.addAttachments(ab.build());
            }
        }
        if (m.getTags() != null) {
            b.addAllTags(m.getTags());
        }
        if (m.getMetadata() != null) {
            b.putAllMetadata(m.getMetadata());
        }
        return b;
    }

    @FunctionalInterface
    private interface SupplierThrowing<T> {
        T get() throws StatusRuntimeException;
    }

    private <T> T invoke(SupplierThrowing<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw MailBabyException.grpc(e.getStatus().getCode().name(),
                    e.getStatus().getDescription() == null ? e.getMessage() : e.getStatus().getDescription());
        }
    }

    public ManagedChannel channelForReuse() {
        return channel;
    }

    public String apiKeyForReuse() {
        return apiKey;
    }

    public String headerNameForReuse() {
        return headerName;
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}