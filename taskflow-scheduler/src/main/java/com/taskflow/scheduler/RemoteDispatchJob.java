package com.taskflow.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class RemoteDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(RemoteDispatchJob.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final HttpRequest request;

    public RemoteDispatchJob(@Value("${taskflow.remote-dispatch.url}") String url,
                             @Value("${TASKFLOW_SCHEDULER_TOKEN}") String token) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("TASKFLOW_SCHEDULER_TOKEN must not be blank");
        }
        request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("X-Taskflow-Scheduler-Token", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    @Scheduled(fixedDelayString = "${taskflow.remote-dispatch.poll-delay-ms:5000}", initialDelay = 1000)
    public void poll() {
        try {
            log.info("Remote scheduler batch completed, dispatched={}", dispatchOnce());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("Remote scheduler interrupted");
        } catch (IOException ex) {
            log.warn("Remote scheduler request failed: {}", ex.getMessage());
        }
    }

    int dispatchOnce() throws IOException, InterruptedException {
        // 一轮发一次批处理请求。失败后由下一轮定时扫描接续。
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Task returned HTTP " + response.statusCode());
        }
        try {
            return Integer.parseInt(response.body().strip());
        } catch (NumberFormatException ex) {
            throw new IOException("Task returned an invalid dispatch count");
        }
    }
}
