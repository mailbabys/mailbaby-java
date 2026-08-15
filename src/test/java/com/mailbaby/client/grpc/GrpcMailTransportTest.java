package com.mailbaby.client.grpc;

import com.mailbaby.client.model.BatchResult;
import com.mailbaby.client.model.EmailMessage;
import com.mailbaby.client.model.SendResult;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import mailbaby.v1.MailServiceGrpc;
import mailbaby.v1.Mailbaby;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcMailTransportTest {

    private static final String SERVER_NAME = "mailbaby-test";

    private Server server;
    private RecordingMailServiceImpl impl;

    @BeforeEach
    void setUp() throws IOException {
        impl = new RecordingMailServiceImpl();
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .addService(ServerInterceptors.intercept(impl, new io.grpc.ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                            io.grpc.ServerCall<ReqT, RespT> call,
                            Metadata headers,
                            io.grpc.ServerCallHandler<ReqT, RespT> next) {
                        impl.lastHeaders.set(headers);
                        return next.startCall(call, headers);
                    }
                }))
                .build()
                .start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    private GrpcMailTransport newTransport(String apiKey) {
        return new GrpcMailTransport(
                InProcessChannelBuilder.forName(SERVER_NAME).usePlaintext().build(),
                apiKey, "X-API-Key");
    }

    @Test
    void sendMapsRequestAndResponse() {
        GrpcMailTransport t = newTransport(null);
        EmailMessage msg = EmailMessage.builder()
                .to(List.of("alice@example.com"))
                .cc(List.of("mgr@example.com"))
                .subject("Hi")
                .textBody("hello")
                .build();

        SendResult r = t.send(msg, false);

        assertThat(impl.lastSend).isNotNull();
        assertThat(impl.lastSend.getSubject()).isEqualTo("Hi");
        assertThat(impl.lastSend.getTextBody()).isEqualTo("hello");
        assertThat(impl.lastSend.getToList()).containsExactly("alice@example.com");
        assertThat(impl.lastSend.getCcList()).containsExactly("mgr@example.com");
        assertThat(impl.lastSend.getAsync()).isFalse();
        assertThat(r.getStatus()).isEqualTo("sent");
        assertThat(r.getId()).isEqualTo("grpc-id-1");
        t.close();
    }

    @Test
    void sendAttachesApiKeyMetadata() {
        GrpcMailTransport t = newTransport("secret-123");
        EmailMessage msg = EmailMessage.builder().to(List.of("a@b.com")).subject("s").build();

        t.send(msg, false);

        Metadata md = impl.lastHeaders.get();
        assertThat(md).isNotNull();
        assertThat(md.get(Metadata.Key.of("X-API-Key", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("secret-123");
        t.close();
    }

    @Test
    void sendBatchAggregatesResults() {
        GrpcMailTransport t = newTransport(null);
        BatchResult br = t.sendBatch(List.of(
                EmailMessage.builder().to(List.of("a@b.com")).subject("s1").build(),
                EmailMessage.builder().to(List.of("c@d.com")).subject("s2").build()
        ), false);

        assertThat(impl.lastBatch).isNotNull();
        assertThat(impl.lastBatch.getEmailsCount()).isEqualTo(2);
        assertThat(br.getTotal()).isEqualTo(2);
        assertThat(br.getSucceeded()).isEqualTo(2);
        assertThat(br.getFailed()).isEqualTo(0);
        assertThat(br.getResults()).hasSize(2);
        t.close();
    }

    @Test
    void pingReturnsVersion() {
        GrpcMailTransport t = newTransport(null);
        var p = t.ping();
        assertThat(p.getStatus()).isEqualTo("ok");
        assertThat(p.getVersion()).isEqualTo("test-1.0");
        t.close();
    }

    @Test
    void healthCheckReturnsServingStatus() {
        GrpcMailTransport t = newTransport(null);
        var h = t.healthCheck();
        assertThat(h.getStatus()).isEqualTo("SERVING");
        assertThat(h.isHealthy()).isTrue();
        assertThat(h.servingStatus().name()).isEqualTo("SERVING");
        t.close();
    }

    private static class RecordingMailServiceImpl extends MailServiceGrpc.MailServiceImplBase {
        final AtomicReference<Metadata> lastHeaders = new AtomicReference<>();
        Mailbaby.SendMailRequest lastSend;
        Mailbaby.BatchSendMailRequest lastBatch;

        @Override
        public void send(Mailbaby.SendMailRequest request, StreamObserver<Mailbaby.SendMailResponse> observer) {
            this.lastSend = request;
            observer.onNext(Mailbaby.SendMailResponse.newBuilder()
                    .setId("grpc-id-1")
                    .setStatus("sent")
                    .setMessage("ok")
                    .setSentAt(123)
                    .build());
            observer.onCompleted();
        }

        @Override
        public void sendBatch(Mailbaby.BatchSendMailRequest request,
                              StreamObserver<Mailbaby.BatchSendMailResponse> observer) {
            this.lastBatch = request;
            Mailbaby.BatchSendMailResponse.Builder b = Mailbaby.BatchSendMailResponse.newBuilder()
                    .setTotal(request.getEmailsCount())
                    .setSucceeded(request.getEmailsCount())
                    .setFailed(0);
            for (int i = 0; i < request.getEmailsCount(); i++) {
                b.addResults(Mailbaby.SendMailResponse.newBuilder()
                        .setId("id-" + i)
                        .setStatus("sent")
                        .setMessage("ok")
                        .setSentAt(i)
                        .build());
            }
            observer.onNext(b.build());
            observer.onCompleted();
        }

        @Override
        public void ping(Mailbaby.PingRequest request, StreamObserver<Mailbaby.PingResponse> observer) {
            observer.onNext(Mailbaby.PingResponse.newBuilder()
                    .setStatus("ok")
                    .setVersion("test-1.0")
                    .setTimestamp(0)
                    .build());
            observer.onCompleted();
        }

        @Override
        public void healthCheck(Mailbaby.HealthCheckRequest request,
                                StreamObserver<Mailbaby.HealthCheckResponse> observer) {
            observer.onNext(Mailbaby.HealthCheckResponse.newBuilder()
                    .setStatus(Mailbaby.HealthCheckResponse.ServingStatus.SERVING)
                    .build());
            observer.onCompleted();
        }
    }
}