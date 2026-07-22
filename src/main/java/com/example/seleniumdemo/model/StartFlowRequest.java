package com.example.seleniumdemo.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a flow.
 * targetUrl -> the external form page Selenium should open
 * dataRow   -> which row (0-based) of data.csv to use as the form values
 */
public record StartFlowRequest(
    @NotBlank String targetUrl,
    int dataRow) {
}
