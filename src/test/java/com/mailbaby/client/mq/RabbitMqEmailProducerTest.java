package com.mailbaby.client.mq;

import com.mailbaby.client.model.EmailMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitMqEmailProducerTest {

    @Test
    void publishSerializesAndPushesToBroker() throws Exception {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(factory.newConnection()).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);

        RabbitMqEmailProducer producer = new RabbitMqEmailProducer(
                factory, "", "mailbaby_tasks", "mailbaby_tasks", true, false);

        EmailMessage msg = EmailMessage.builder()
                .id("e-7")
                .to(List.of("a@b.com"))
                .subject("Hi")
                .textBody("Hello")
                .build();
        producer.publish(msg);
        producer.close();

        ArgumentCaptor<AMQP.BasicProperties> propsCap = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        ArgumentCaptor<byte[]> bodyCap = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq(""), eq("mailbaby_tasks"), propsCap.capture(), bodyCap.capture());

        AMQP.BasicProperties props = propsCap.getValue();
        assertThat(props.getContentType()).isEqualTo("application/json");
        assertThat(props.getDeliveryMode()).isEqualTo(2);
        assertThat(props.getMessageId()).isEqualTo("e-7");

        String json = new String(bodyCap.getValue(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"id\":\"e-7\"");
        assertThat(json).contains("\"to\":[\"a@b.com\"]");
        assertThat(json).contains("\"subject\":\"Hi\"");
        assertThat(json).contains("\"text_body\":\"Hello\"");
    }

    @Test
    void publishSendsApplicationJsonContentType() throws Exception {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(factory.newConnection()).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);

        RabbitMqEmailProducer producer = new RabbitMqEmailProducer(
                factory, "", "mailbaby_tasks", null, true, false);
        producer.publish(EmailMessage.builder().to(List.of("a@b.com")).subject("s").build());
        producer.close();

        ArgumentCaptor<AMQP.BasicProperties> propsCap = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(eq(""), eq("mailbaby_tasks"), propsCap.capture(), org.mockito.ArgumentMatchers.any(byte[].class));
        assertThat(propsCap.getValue().getContentType()).isEqualTo("application/json");
    }
}