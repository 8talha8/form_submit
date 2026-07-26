package com.example.seleniumdemo.service;

import com.example.seleniumdemo.model.FieldMapping;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
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
    private final String mappingResourcePath;
    private final String formDataResourcePath;
    private volatile List<FieldMapping> cachedMappings;

    public MappingService(ResourceLoader resourceLoader,
                          @Value("${app.mapping.map-file:classpath:real_mapping.csv}") String mappingResourcePath,
                          @Value("${app.mapping.data-file:classpath:FY Admission 2026-27 - F.Y.B.Voc.csv}") String formDataResourcePath) {
        this.resourceLoader = resourceLoader;
        this.mappingResourcePath = mappingResourcePath;
        this.formDataResourcePath = formDataResourcePath;
    }

    public List<FieldMapping> loadMappings() {
        if (cachedMappings != null) {
            return cachedMappings;
        }
        synchronized (this) {
            if (cachedMappings != null) {
                return cachedMappings;
            }
            Resource resource = resourceLoader.getResource(mappingResourcePath);
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
                throw new UncheckedIOException("Failed to read mapping file " + mappingResourcePath,
                    e instanceof java.io.IOException io ? io : new java.io.IOException(e));
            }
            cachedMappings = mappings;
            return cachedMappings;
        }
    }

    /** Returns each data row as a column->value map. */
    public List<Map<String, String>> loadFormData() {
        Resource resource = resourceLoader.getResource(formDataResourcePath);
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
