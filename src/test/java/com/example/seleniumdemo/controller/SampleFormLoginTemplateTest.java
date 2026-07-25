package com.example.seleniumdemo.controller;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleFormLoginTemplateTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sampleFormLoginContainsExpectedFieldsAndAutoSubmit() throws Exception {
        MvcResult result = mockMvc.perform(get("/sample-form-login"))
            .andExpect(status().isOk())
            .andReturn();

        String html = result.getResponse().getContentAsString();

        Assertions.assertThat(html).contains("<form id=\"loginForm\"");
        Assertions.assertThat(html).contains("id=\"first-name\"");
        Assertions.assertThat(html).contains("class=\"phone-field\"");
        Assertions.assertThat(html).contains("setTimeout(function()");
        Assertions.assertThat(html).contains("f.submit()");
    }
}
