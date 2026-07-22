package com.example.seleniumdemo.service;

import com.example.seleniumdemo.config.CacheConfig;
import com.example.seleniumdemo.model.FieldMapping;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads (and caches) the field mapping and the form-data rows from CSV.
 * mapping.csv columns: csvColumn, locatorType, locatorValue
 * data.csv    columns: one column per csvColumn referenced in mapping.csv
 */
@Service
public class MappingService {

    private final ResourceLoader resourceLoader;

    public MappingService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Cacheable(CacheConfig.MAPPINGS_CACHE)
    public List<FieldMapping> loadMappings() {
        Resource resource = resourceLoader.getResource("classpath:mapping.csv");
        List<FieldMapping> mappings = new ArrayList<>();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                 .setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : parser) {
                mappings.add(new FieldMapping(
                    record.get("csvColumn"),
                    record.get("locatorType"),
                    record.get("locatorValue")));
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to read mapping.csv",
                e instanceof java.io.IOException io ? io : new java.io.IOException(e));
        }
        return mappings;
    }

    /** Returns each data row as a column->value map. */
    @Cacheable(CacheConfig.FORM_DATA_CACHE)
    public List<Map<String, String>> loadFormData() {
        Resource resource = resourceLoader.getResource("classpath:data.csv");
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                 .setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to read data.csv",
                e instanceof java.io.IOException io ? io : new java.io.IOException(e));
        }
        return rows;
    }

    public Map<String, String> getRow(int index) {
        List<Map<String, String>> rows = loadFormData();
        if (index < 0 || index >= rows.size()) {
            throw new IllegalArgumentException("dataRow " + index + " out of range (0.." + (rows.size() - 1) + ")");
        }
        return rows.get(index);
    }
}
