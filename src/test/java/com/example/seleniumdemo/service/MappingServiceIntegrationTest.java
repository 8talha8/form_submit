package com.example.seleniumdemo.service;

import com.example.seleniumdemo.model.FieldMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for MappingService against the real mapping.csv and data.csv
 * on the classpath. Verifies parsing, caching behaviour, and locator translation.
 */
@SpringBootTest
class MappingServiceIntegrationTest {

    @Autowired
    private MappingService mappingService;

    @Test
    void loadsAllMappingsFromCsv() {
        List<FieldMapping> mappings = mappingService.loadMappings();
        assertThat(mappings).hasSize(6);
        assertThat(mappings)
            .extracting(FieldMapping::csvColumn)
            .containsExactly("firstName", "lastName", "email", "phone", "country", "comments");
    }

    @Test
    void mapsLocatorTypesToSeleniumBy() {
        List<FieldMapping> mappings = mappingService.loadMappings();
        FieldMapping firstName = mappings.get(0);
        assertThat(firstName.locatorType()).isEqualTo("id");
        assertThat(firstName.toBy().toString()).contains("first-name");

        FieldMapping country = mappings.stream()
            .filter(m -> m.csvColumn().equals("country"))
            .findFirst().orElseThrow();
        assertThat(country.locatorType()).isEqualTo("xpath");
        assertThat(country.toBy().toString()).contains("country");
    }

    @Test
    void unsupportedLocatorTypeThrows() {
        FieldMapping bad = new FieldMapping("col", "bogus", "value");
        assertThatThrownBy(bad::toBy)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported locator type");
    }

    @Test
    void loadsFormDataRows() {
        List<Map<String, String>> rows = mappingService.loadFormData();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("firstName", "Ada")
            .containsEntry("lastName", "Lovelace")
            .containsEntry("email", "ada@example.com");
    }

    @Test
    void getRowReturnsRequestedRow() {
        Map<String, String> row = mappingService.getRow(2);
        assertThat(row).containsEntry("firstName", "Grace");
    }

    @Test
    void getRowOutOfRangeThrows() {
        assertThatThrownBy(() -> mappingService.getRow(99))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("out of range");
    }

    @Test
    void mappingsAreCachedAcrossCalls() {
        List<FieldMapping> first = mappingService.loadMappings();
        List<FieldMapping> second = mappingService.loadMappings();
        // Cacheable returns the same cached instance
        assertThat(first).isSameAs(second);
    }
}
