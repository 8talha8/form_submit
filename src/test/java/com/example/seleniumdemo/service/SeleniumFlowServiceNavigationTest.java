package com.example.seleniumdemo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeleniumFlowServiceNavigationTest {

    @Test
    void resolvesCampus360PersonalProfileUrl() {
        SeleniumFlowService service = new SeleniumFlowService(null, null, "local", "", "", false, "T8t8t8t89*");

        assertThat(service.resolvePostLoginUrl("https://hmtcampus360v2.net/"))
            .isEqualTo("https://hmtcampus360v2.net/AdminPanel/Admission_new/personal_profile.php");

        assertThat(service.resolvePostLoginUrl("https://hmtcampus360v2.net/AdminPanel/Dashboard/dashboard.php"))
            .isEqualTo("https://hmtcampus360v2.net/AdminPanel/Admission_new/personal_profile.php");

        assertThat(service.resolvePostLoginUrl("https://example.com/some-page"))
            .isEqualTo("https://example.com/some-page");
    }

    @Test
    void extractsOriginForPermissionGranting() {
        SeleniumFlowService service = new SeleniumFlowService(null, null, "local", "", "", false, "T8t8t8t89*");

        assertThat(service.extractOrigin("https://hmtcampus360v2.net/AdminPanel/Dashboard/dashboard.php"))
            .isEqualTo("https://hmtcampus360v2.net");

        assertThat(service.extractOrigin("https://example.com/path?q=1"))
            .isEqualTo("https://example.com");

        assertThat(service.extractOrigin("not-a-valid-url"))
            .isNull();
    }
}
