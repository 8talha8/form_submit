package com.example.seleniumdemo.controller;

import com.example.seleniumdemo.model.FlowSession;
import com.example.seleniumdemo.security.JwtService;
import com.example.seleniumdemo.service.SeleniumFlowService;
import com.example.seleniumdemo.service.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlowLoginNavigationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRegistry registry;

    @MockBean
    private SeleniumFlowService flowService;

    private String bearer;

    @BeforeEach
    void setUp(@Autowired JwtService jwtService) {
        bearer = "Bearer " + jwtService.generateToken("operator");
    }

    @Test
    void startFlow_then_sampleFormLogin_autoSubmit_navigatesToSampleForm() throws Exception {
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        FlowSession session = new FlowSession("sess-login", "operator", driver, null);
        session.setStatus(FlowSession.Status.AWAITING_SUBMIT);
        registry.register(session);

        when(flowService.startAndFill(eq("operator"), anyString(), anyInt()))
            .thenReturn(session);

        // Start the flow targeting the real Campus360 login page.
        MvcResult mvcResult = mockMvc.perform(post("/api/flow/start")
                .with(user("operator").roles("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUrl\":\"https://hmtcampus360v2.net/\",\"dataRow\":0}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(mvcResult))
            .andExpect(status().isOk());

        // Simulate the browser auto-submitting the login form after being filled by the flow
        MvcResult postResult = mockMvc.perform(post("/sample-form")
                .param("username", "Ada")
                .param("password", "555-0100"))
            .andExpect(status().isOk())
            .andReturn();

        String html = postResult.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("Form submitted successfully!");
    }
}
