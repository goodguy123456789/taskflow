package com.taskflow.task.service;

import com.taskflow.task.config.RabbitConfig;
import com.taskflow.task.dto.TaskInstanceReadyEvent;
import com.taskflow.task.entity.TaskInstance;
import com.taskflow.task.listener.TaskInstanceReadyEventParser;
import com.taskflow.task.mapper.TaskInstanceMapper;
import com.taskflow.task.mapper.TaskMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadyDeadLetterReplayService {

    private final RabbitTemplate rabbitTemplate;
    private final TaskInstanceReadyEventParser parser;
    private final TaskInstanceMapper instanceMapper;
    private final TaskMapper taskMapper;

    public Result replayHead(String expectedEventId) {
        if (expectedEventId == null || expectedEventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        var factory = rabbitTemplate.getConnectionFactory();
        if (!factory.isPublisherConfirms() || !factory.isPublisherReturns()) {
            return Result.PUBLISH_FAILED;
        }

        return rabbitTemplate.execute(channel -> {
            // autoAck=false，先保留原死信，等回投确认后再处理它。
            var delivery = channel.basicGet(RabbitConfig.INSTANCE_READY_DLQ, false);
            if (delivery == null) {
                return Result.EMPTY;
            }

            long deliveryTag = delivery.getEnvelope().getDeliveryTag();
            boolean published = false;
            try {
                TaskInstanceReadyEvent event;
                try {
                    String json = StandardCharsets.UTF_8.newDecoder()
                            .decode(ByteBuffer.wrap(delivery.getBody())).toString();
                    event = parser.parse(json);
                } catch (CharacterCodingException | IllegalArgumentException ex) {
                    return Result.INVALID_MESSAGE;
                }

                if (!expectedEventId.equals(event.eventId())) {
                    return Result.HEAD_MISMATCH;
                }
                if (!canReplay(event.instanceId())) {
                    return Result.INSTANCE_NOT_ELIGIBLE;
                }

                // 保留原消息体，按普通 Ready 消息的方式重新发布。
                Message message = MessageBuilder.withBody(delivery.getBody())
                        .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();

                published = publishConfirmed(message);
                if (!published) {
                    return Result.PUBLISH_FAILED;
                }

                // deliveryTag 属于读取 DLQ 的 Channel，ACK 要在同一通道发送。
                channel.basicAck(deliveryTag, false);
                return Result.REPLAYED;
            } finally {
                if (!published) {
                    channel.basicNack(deliveryTag, false, true);
                }
            }
        });
    }

    private boolean canReplay(Long instanceId) {
        TaskInstance instance = instanceMapper.findById(instanceId);
        if (instance == null || !("PENDING".equals(instance.getStatus())
                || "QUEUED".equals(instance.getStatus()))) {
            return false;
        }

        LocalDateTime now = taskMapper.selectUtcNow();
        return instance.getScheduledAt() != null && !instance.getScheduledAt().isAfter(now)
                && (instance.getLeaseUntil() == null || !instance.getLeaseUntil().isAfter(now));
    }

    private boolean publishConfirmed(Message message) {
        if (!Boolean.TRUE.equals(rabbitTemplate.isMandatoryFor(message))) {
            return false;
        }

        CorrelationData correlation = new CorrelationData();
        try {
            rabbitTemplate.send(RabbitConfig.EXCHANGE, RabbitConfig.INSTANCE_READY_KEY, message, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (AmqpException | ExecutionException | TimeoutException ex) {
            return false;
        }
    }

    public enum Result {
        REPLAYED,
        EMPTY,
        INVALID_MESSAGE,
        HEAD_MISMATCH,
        INSTANCE_NOT_ELIGIBLE,
        PUBLISH_FAILED
    }
}