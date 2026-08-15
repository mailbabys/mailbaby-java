package com.mailbaby.client.mq;

import com.mailbaby.client.exception.MailBabyException;
import com.mailbaby.client.model.EmailMessage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka {@link EmailProducer}. The email id (or a generated placeholder) is used as
 * the record key so that all messages for the same id land on the same partition.
 */
public class KafkaEmailProducer implements EmailProducer {

    private final Producer<String, byte[]> producer;
    private final String topic;
    private final boolean sync;

    public KafkaEmailProducer(String bootstrapServers, String topic) {
        this(buildDefault(bootstrapServers), topic, false);
    }

    public KafkaEmailProducer(Properties props, String topic, boolean sync) {
        Properties p = props == null ? new Properties() : new Properties();
        if (!p.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)) {
            p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        }
        if (!p.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)) {
            p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        }
        if (topic == null || topic.isBlank()) {
            throw new MailBabyException("invalid_config", "Kafka topic must be provided", null);
        }
        this.producer = new KafkaProducer<>(p);
        this.topic = topic;
        this.sync = sync;
    }

    /** Test seam: inject a pre-built Producer (e.g. a Kafka MockProducer). */
    KafkaEmailProducer(Producer<String, byte[]> producer, String topic) {
        this(producer, topic, false);
    }

    /** Test seam: inject a pre-built Producer with sync/async control. */
    KafkaEmailProducer(Producer<String, byte[]> producer, String topic, boolean sync) {
        if (producer == null) {
            throw new MailBabyException("invalid_config", "Kafka producer must not be null", null);
        }
        if (topic == null || topic.isBlank()) {
            throw new MailBabyException("invalid_config", "Kafka topic must be provided", null);
        }
        this.producer = producer;
        this.topic = topic;
        this.sync = sync;
    }

    private static Properties buildDefault(String bootstrapServers) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put("acks", "all");
        p.put("retries", 3);
        p.put("linger.ms", 5);
        return p;
    }

    @Override
    public void publish(EmailMessage message) {
        byte[] payload = EmailJsonMapper.toJsonBytes(message);
        String key = message.getId() == null ? "" : message.getId();
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, payload);
        try {
            if (sync) {
                RecordMetadata md = producer.send(record).get(10, TimeUnit.SECONDS);
                if (md == null) {
                    throw new MailBabyException("publish_failed", "Kafka send returned null metadata", null);
                }
            } else {
                producer.send(record, (md, ex) -> {
                    if (ex != null) {
                        // The user's caller gets a synchronous MailBabyException; the async callback
                        // cannot surface errors there, so we log to stderr to keep the failure visible.
                        System.err.println("Kafka async send failed: " + ex.getMessage());
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailBabyException("publish_failed", "Kafka send interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new MailBabyException("publish_failed", "Kafka send failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }

    /** Avoids forcing a Kafka types import on callers while keeping the API stable. */
    private static final class ProducerConfig {
        static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";
        static final String KEY_SERIALIZER_CLASS_CONFIG = "key.serializer";
        static final String VALUE_SERIALIZER_CLASS_CONFIG = "value.serializer";
    }
}