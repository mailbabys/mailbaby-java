package com.mailbaby.client;

import com.mailbaby.client.grpc.GrpcMailTransport;
import com.mailbaby.client.rest.RestMailTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailBabyClientTest {

    @Test
    void restBuilderProducesRestTransport() {
        MailBabyClient c = MailBabyClient.builder()
                .rest("http://localhost:8080")
                .apiKey("k")
                .headerName("Authorization")
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            assertThat(c.transport()).isInstanceOf(RestMailTransport.class);
            assertThat(((RestMailTransport) c.transport()).baseUrlForReuse()).isEqualTo("http://localhost:8080");
            assertThat(((RestMailTransport) c.transport()).apiKeyForReuse()).isEqualTo("k");
            assertThat(((RestMailTransport) c.transport()).headerNameForReuse()).isEqualTo("Authorization");
            assertThat(((RestMailTransport) c.transport()).timeoutForReuse()).isEqualTo(Duration.ofSeconds(5));
        } finally {
            c.close();
        }
    }

    @Test
    void grpcBuilderProducesGrpcTransport() {
        MailBabyClient c = MailBabyClient.builder()
                .grpc("mailbaby.example.com", 8081, "secret")
                .build();
        try {
            assertThat(c.transport()).isInstanceOf(GrpcMailTransport.class);
            assertThat(((GrpcMailTransport) c.transport()).apiKeyForReuse()).isEqualTo("secret");
        } finally {
            c.close();
        }
    }

    @Test
    void buildWithoutTransportFails() {
        assertThatThrownBy(() -> MailBabyClient.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transport");
    }
}