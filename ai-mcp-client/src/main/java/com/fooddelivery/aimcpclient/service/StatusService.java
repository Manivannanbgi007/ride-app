package com.fooddelivery.aimcpclient.service;

import com.fooddelivery.aimcpclient.client.AgentServerClient;
import com.fooddelivery.aimcpclient.dto.response.StatusResponse;
import com.fooddelivery.aimcpclient.exception.AgentCallException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Tells the chat window whether to offer the assistant. Never fails: "not available" is an answer too. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final AgentServerClient agentServer;

    @SuppressWarnings("checkstyle:IllegalCatch") // a status probe must never produce an error response
    public StatusResponse status() {
        try {
            StatusResponse status = agentServer.status();
            return status == null ? StatusResponse.unavailable() : status;
        } catch (AgentCallException e) {
            return StatusResponse.unavailable();
        } catch (RuntimeException e) {
            log.warn("Unexpected error while reading the assistant status: {}", e.getClass().getSimpleName());
            return StatusResponse.unavailable();
        }
    }
}
