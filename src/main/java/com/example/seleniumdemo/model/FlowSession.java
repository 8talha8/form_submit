package com.example.seleniumdemo.model;

import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Instant;

/**
 * Holds a live browser session between "fill" and the human "submit".
 * Mutable last-touch timestamp lets the cleanup scheduler expire idle sessions.
 */
public class FlowSession {

    private final String id;
    private final String owner; // username who started the flow
    private final RemoteWebDriver driver;
    private final String vncUrl;
    private volatile Status status;
    private volatile Instant lastAccess;

    public enum Status { FILLING, AWAITING_SUBMIT, SUBMITTED, CLOSED, ERROR }

    public FlowSession(String id, String owner, RemoteWebDriver driver, String vncUrl) {
        this.id = id;
        this.owner = owner;
        this.driver = driver;
        this.vncUrl = vncUrl;
        this.status = Status.FILLING;
        this.lastAccess = Instant.now();
    }

    public void touch() { this.lastAccess = Instant.now(); }

    public String getId() { return id; }
    public String getOwner() { return owner; }
    public RemoteWebDriver getDriver() { return driver; }
    public String getVncUrl() { return vncUrl; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getLastAccess() { return lastAccess; }
}
