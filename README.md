# MailBaby Java Client

A Java client for the [MailBaby](https://github.com/mailbabys/mailbaby) email
delivery service. Targets Java 17 (builds and runs on Java 26), uses Maven,
and exposes three transports with one unified API:

- **REST** — `POST /v1/email/send`, `POST /v1/email/batch`, `GET /livez`, `GET /readyz`
- **gRPC** — `mailbaby.v1.MailService` (`Send`, `SendBatch`, `Ping`, `HealthCheck`)
- **MQ** — RabbitMQ and Kafka producers publishing the same JSON payload
  MailBaby's queue consumers expect.

## Install

Build with the Maven wrapper (Java 17+ required, tested on Java 26):

```bash
./mvnw verify        # or mvnw.cmd on Windows
```

The jar is produced at `target/mailbaby-client-0.1.0-SNAPSHOT.jar`. The wrapper
downloads Maven 3.9.16 on first run; subsequent runs are offline.

## Quick start

### REST

```java
try (MailBabyClient client = MailBabyClient.builder()
        .rest("http://localhost:8080", "your-secret-key")
        .build()) {

    EmailMessage msg = EmailMessage.builder()
            .from("noreply@example.com")
            .fromName("MailBaby")
            .to(List.of("alice@example.com"))
            .cc(List.of("manager@example.com"))
            .subject("Order Confirmation #10024")
            .textBody("Thank you for your order!")
            .htmlBody("<h2>Order Confirmed</h2><p>Tracking number: 987654</p>")
            .tag("order")
            .tag("receipt")
            .build();

    // synchronous (blocks until SMTP acknowledges)
    SendResult sent = client.send(msg);
    System.out.println(sent.getId() + " " + sent.getStatus());

    // asynchronous (enqueues and returns immediately; status is "queued")
    SendResult queued = client.sendAsync(msg);

    // batch
    BatchResult batch = client.sendBatch(List.of(msg1, msg2, msg3));

    // health probes
    client.ping();
    HealthResult ready = client.healthCheck();
}
```

### gRPC

```java
try (MailBabyClient client = MailBabyClient.builder()
        .grpc("localhost", 8081, "your-secret-key")
        .build()) {
    client.send(msg);
    client.ping();              // mailbaby.v1.MailService.Ping
    client.healthCheck();       // mailbaby.v1.MailService.HealthCheck
}
```

Authentication metadata: `X-API-Key: <key>` (or `Authorization: Bearer <key>` if
configured with `headerName("Authorization")`).

### RabbitMQ

```java
try (RabbitMqEmailProducer producer = new RabbitMqEmailProducer(
        "amqp://guest:guest@127.0.0.1:5672/",        // URI
        "",                                          // exchange (default)
        "mailbaby_tasks",                            // routing key
        "mailbaby_tasks",                            // queue to declare
        true,                                        // durable
        false)) {                                    // publisher confirms
    producer.publish(msg);
}
```

### Kafka

```java
try (KafkaEmailProducer producer = new KafkaEmailProducer(
        "broker1:9092,broker2:9092",
        "mailbaby_tasks")) {
    producer.publish(msg);   // key = email id, value = JSON payload
}
```

## Models

`EmailMessage` mirrors the Go `sender.Email` JSON schema exactly — snake_case
fields, `attachments[].data` as base64, no `async` field (the asynchronous
flag is conveyed via the REST query parameter).

`Attachment` covers filename, content type, base64 data, inline flag, and
optional content-id for `<img src="cid:...">` references.

`SendResult` carries `id`, `status` (`sent` / `queued`), human-readable
`message`, and `sentAt` (Unix nanoseconds).

`BatchResult` aggregates total / succeeded / failed counts and a list of
`SendResult`s.

`HealthResult` includes `status` ("UP" / "DOWN" for REST, `SERVING` /
`NOT_SERVING` for gRPC) and per-component health.

`MailBabyException` is thrown on every non-success path — HTTP non-2xx
responses (with the server-provided error code), gRPC `StatusRuntimeException`
(with the gRPC status code), or transport failures (with the underlying cause).
It carries `code`, `status`, `message`, and `details`.

## Configuration reference

`RestMailTransport(baseUrl, apiKey, headerName, timeout)`
- `baseUrl` — server root, e.g. `http://localhost:8080`
- `apiKey` — optional secret key (omit for unauthenticated servers)
- `headerName` — defaults to `X-API-Key`; use `Authorization` for `Bearer` tokens
- `timeout` — optional `Duration` for connect + per-request timeout

`GrpcMailTransport(host, port, apiKey, headerName)` and
`GrpcMailTransport(ManagedChannel, apiKey, headerName)` for advanced setups
(e.g. TLS, custom interceptors).

`RabbitMqEmailProducer(uri, exchange, routingKey, queue, durable, publisherConfirms)`
- `uri` — full AMQP URI with credentials
- `exchange` — empty string uses the default direct exchange
- `routingKey` — defaults to `mailbaby_tasks`
- `queue` — optional queue to declare before publishing
- `durable` — `BasicProperties.deliveryMode = 2`
- `publisherConfirms` — wait for broker ACKs before returning

`KafkaEmailProducer(bootstrapServers, topic)` /
`KafkaEmailProducer(Properties, topic, sync)` for full control over Kafka
client settings.

## License

Apache License 2.0.
