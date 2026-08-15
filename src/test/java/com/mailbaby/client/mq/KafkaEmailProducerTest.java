package com.mailbaby.client.mq;

import com.mailbaby.client.model.EmailMessage;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEmailProducerTest {

    @Test
    void publishSendsJsonPayloadWithEmailIdAsKey() throws Exception {
        MockProducer<String, byte[]> mock = new MockProducer<>(true, (org.apache.kafka.clients.producer.Partitioner) null, new StringSerializer(), new ByteArraySerializer());

        KafkaEmailProducer producer = new KafkaEmailProducer(mock, "mailbaby_tasks");

        EmailMessage msg = EmailMessage.builder()
                .id("e-1")
                .to(List.of("alice@example.com"))
                .subject("Hi")
                .textBody("Hello")
                .build();
        producer.publish(msg);
        producer.close();

        assertThat(mock.history()).hasSize(1);
        ProducerRecord<String, byte[]> sent = mock.history().get(0);
        assertThat(sent.topic()).isEqualTo("mailbaby_tasks");
        assertThat(sent.key()).isEqualTo("e-1");
        String json = new String(sent.value(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"id\":\"e-1\"");
        assertThat(json).contains("\"subject\":\"Hi\"");
        assertThat(json).contains("\"to\":[\"alice@example.com\"]");
    }

    @Test
    void publishUsesEmptyKeyWhenIdMissing() {
        MockProducer<String, byte[]> mock = new MockProducer<>(true, (org.apache.kafka.clients.producer.Partitioner) null, new StringSerializer(), new ByteArraySerializer());
        KafkaEmailProducer producer = new KafkaEmailProducer(mock, "t");

        producer.publish(EmailMessage.builder().to(List.of("a@b.com")).subject("s").build());
        producer.close();

        assertThat(mock.history().get(0).key()).isEqualTo("");
    }

    @Test
    void requiresTopic() {
        MockProducer<String, byte[]> mock = new MockProducer<>(true, (org.apache.kafka.clients.producer.Partitioner) null, new StringSerializer(), new ByteArraySerializer());
        try {
            new KafkaEmailProducer(mock, null);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("topic");
        }
    }
}