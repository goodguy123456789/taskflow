
package com.taskflow.task.service;

import com.taskflow.task.config.RabbitConfig;
import com.taskflow.task.entity.OutboxEvent;
import com.taskflow.task.mapper.OutboxMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "taskflow.outbox",
        name = "relay-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelay {

    private final OutboxClaimService claimService;
    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void poll() {

        List<OutboxEvent> events;

        try {
            events = claimService.claimBatch();
        } catch (Exception ex) {
            log.error("Outbox claim failed", ex);
            return;
        }

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        String routingKey = switch (event.getEventType()) {

        case "TASK_DEFINITION_CREATED" ->
            RabbitConfig.ROUTING_KEY;

        case "TASK_INSTANCE_READY" ->
            RabbitConfig.INSTANCE_READY_KEY;

        default -> throw new IllegalArgumentException(
            "Unsupported event type: " + event.getEventType()
        );
        };

        try {
            CorrelationData correlation =
                    new CorrelationData(event.getEventId());

            rabbitTemplate.convertAndSend(
                    RabbitConfig.EXCHANGE,
                    routingKey,
                    event.getPayload(),
                    message -> {
                        message.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );
                        return message;
                    },
                    correlation
            );

            CorrelationData.Confirm confirm =
                    correlation.getFuture()
                            .get(5, TimeUnit.SECONDS);

            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ publish NACK");
            }

            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ message unroutable");
            }

            int updated = outboxMapper.markPublished(
                    event.getId(),
                    event.getClaimToken()
            );

            if (updated != 1) {
                log.warn(
                    "Event claim expired: {}",
                    event.getEventId()
                );
            } else {
                log.info(
                    "Event published: {}",
                    event.getEventId()
                );
            }

        } catch (Exception ex) {
            log.warn(
                    "Outbox publish failed: {}",
                    event.getEventId(),
                    ex
            );

            long delaySeconds = Math.min(
                    3600L,
                    5L * (1L << Math.min(
                            event.getRetryCount(), 10))
            );

            try {
                int updated = outboxMapper.scheduleRetry(
                        event.getId(),
                        event.getClaimToken(),
                        delaySeconds
                );

                if (updated == 0) {
                    log.warn(
                        "Retry state not updated: {}",
                        event.getEventId()
                    );
                }
            } catch (Exception dbEx) {
                log.error(
                    "Cannot save retry state: {}",
                    event.getEventId(),
                    dbEx
                );
            }
        }
    }
}
