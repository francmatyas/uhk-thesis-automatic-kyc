package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.worker_job.service.WorkerJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResultsListener {

    private final WorkerJobService workerJobService;

    @RabbitListener(queues = "q.api.results.${api.instanceId}")
    public void onResult(Map<String, Object> event,
                         @Header("amqp_receivedRoutingKey") String routingKey) {
        workerJobService.handleResultEvent(event, routingKey);
    }
}
