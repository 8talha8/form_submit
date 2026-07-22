package com.example.seleniumdemo.model;

import org.openqa.selenium.By;

/**
 * One row of mapping.csv.
 * csvColumn    -> the column name in the source data csv/excel
 * locatorType  -> how to find the element (id, name, css, xpath, class)
 * locatorValue -> the actual selector value
 */
public record FieldMapping(String csvColumn, String locatorType, String locatorValue) {

    /** Translate the mapping into a Selenium By locator. */
    public By toBy() {
        return switch (locatorType.trim().toLowerCase()) {
            case "id"    -> By.id(locatorValue);
            case "name"  -> By.name(locatorValue);
            case "css"   -> By.cssSelector(locatorValue);
            case "xpath" -> By.xpath(locatorValue);
            case "class" -> By.className(locatorValue);
            case "tag"   -> By.tagName(locatorValue);
            default -> throw new IllegalArgumentException(
                "Unsupported locator type: " + locatorType + " (column: " + csvColumn + ")");
        };
    }
}
