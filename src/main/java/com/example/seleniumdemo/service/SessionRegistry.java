package com.example.seleniumdemo.service;

import com.example.seleniumdemo.model.FlowSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of live browser sessions.
 * Solves the "stateful session" limitation:
 * - concurrent access via ConcurrentHashMap
 * - a scheduled sweep quits idle browsers so they don't leak.
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final ConcurrentHashMap<String, FlowSession> sessions = new ConcurrentHashMap<>();
    private final Duration idleTimeout;

    public SessionRegistry(@Value("${app.session.idle-timeout-seconds:300}") long idleTimeoutSeconds) {
        this.idleTimeout = Duration.ofSeconds(idleTimeoutSeconds);
    }

    public void register(FlowSession session) {
        sessions.put(session.getId(), session);
    }

    public Optional<FlowSession> get(String id) {
        FlowSession s = sessions.get(id);
        if (s != null) {
            s.touch();
        }
        return Optional.ofNullable(s);
    }

    /** Get a session only if it belongs to the given owner (authorization guard). */
    public Optional<FlowSession> getOwned(String id, String owner) {
        return get(id).filter(s -> s.getOwner().equals(owner));
    }

    public Collection<FlowSession> all() {
        return sessions.values();
    }

    public void remove(String id) {
        FlowSession s = sessions.remove(id);
        quitQuietly(s);
    }

    /** Runs every 60s: quit and drop sessions idle longer than the timeout. */
    @Scheduled(fixedDelayString = "${app.session.cleanup-interval-ms:60000}")
    public void reapIdleSessions() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        for (FlowSession s : sessions.values()) {
            if (s.getLastAccess().isBefore(cutoff)) {
                log.info("Reaping idle session id={}, owner={}, status={}",
                    s.getId(), s.getOwner(), s.getStatus());
                sessions.remove(s.getId());
                quitQuietly(s);
            }
        }
    }

    private void quitQuietly(FlowSession s) {
        if (s == null || s.getDriver() == null) {
            return;
        }
        try {
            s.getDriver().quit();
        } catch (Exception e) {
            log.warn("Error quitting driver for session {}: {}", s.getId(), e.getMessage());
        }
    }
}
