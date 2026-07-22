package com.example.seleniumdemo.controller;

import com.example.seleniumdemo.model.FlowSession;
import com.example.seleniumdemo.model.StartFlowRequest;
import com.example.seleniumdemo.service.SeleniumFlowService;
import com.example.seleniumdemo.service.SessionRegistry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Human-in-the-loop flow API. All endpoints require a valid JWT.
 * Flows run on the dedicated async executor so request threads never block.
 */
@RestController
@RequestMapping("/api/flow")
public class FlowController {

    private final SeleniumFlowService flowService;
    private final SessionRegistry registry;

    public FlowController(SeleniumFlowService flowService, SessionRegistry registry) {
        this.flowService = flowService;
        this.registry = registry;
    }

    /** Start + fill (but not submit). Returns the session id and the VNC url to embed. */
    @PostMapping("/start")
    @Async
    public CompletableFuture<ResponseEntity<?>> start(@Valid @RequestBody StartFlowRequest request,
                                                      Authentication auth) {
        String owner = auth.getName();
        try {
            FlowSession session = flowService.startAndFill(owner, request.targetUrl(), request.dataRow());
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", session.getId());
            body.put("vncUrl", session.getVncUrl()); // null in local mode
            body.put("status", session.getStatus().name());
            return CompletableFuture.completedFuture(ResponseEntity.ok(body));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                ResponseEntity.status(500).body(Map.of("error", e.getMessage())));
        }
    }

    /** Poll current status of a session. */
    @GetMapping("/{sessionId}/status")
    public ResponseEntity<?> status(@PathVariable String sessionId, Authentication auth) {
        return registry.getOwned(sessionId, auth.getName())
            .<ResponseEntity<?>>map(s -> {
                Map<String, Object> body = new HashMap<>();
                body.put("sessionId", s.getId());
                body.put("status", s.getStatus().name());
                body.put("vncUrl", s.getVncUrl()); // null in local mode
                return ResponseEntity.ok(body);
            })
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Session not found")));
    }

    /** Human verified the filled form and clicks submit from the dashboard. */
    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<?> submit(@PathVariable String sessionId,
                                    @RequestBody Map<String, String> body,
                                    Authentication auth) {
        return registry.getOwned(sessionId, auth.getName())
            .<ResponseEntity<?>>map(s -> {
                flowService.submit(s,
                    body.getOrDefault("submitLocatorType", "css"),
                    body.getOrDefault("submitLocatorValue", "button[type=submit]"));
                return ResponseEntity.ok(Map.of("status", s.getStatus().name()));
            })
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Session not found")));
    }

    /** Human cancels: quit the browser and discard the session. */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> cancel(@PathVariable String sessionId, Authentication auth) {
        return registry.getOwned(sessionId, auth.getName())
            .<ResponseEntity<?>>map(s -> {
                flowService.cancel(s);
                return ResponseEntity.ok(Map.of("status", "CLOSED"));
            })
            .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Session not found")));
    }
}
