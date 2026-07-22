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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the flow API. The Selenium/browser layer is mocked so no
 * real Chrome launches, but the real JWT security filter, async dispatch, and
 * SessionRegistry are exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SessionRegistry registry;

    // Mock only the browser-driving service; keep the real registry.
    @MockBean
    private SeleniumFlowService flowService;

    private String bearer;

    @BeforeEach
    void setup() {
        bearer = "Bearer " + jwtService.generateToken("operator");
    }

    @Test
    void flowApiRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/flow/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUrl\":\"http://localhost:8080/sample-form\",\"dataRow\":0}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void startFlowReturnsSessionForAuthenticatedUser() throws Exception {
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        FlowSession session = new FlowSession("sess-1", "operator", driver, null);
        session.setStatus(FlowSession.Status.AWAITING_SUBMIT);
        registry.register(session);

        when(flowService.startAndFill(eq("operator"), anyString(), anyInt()))
            .thenReturn(session);

        MvcResult mvcResult = mockMvc.perform(post("/api/flow/start")
                .with(user("operator").roles("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUrl\":\"http://localhost:8080/sample-form\",\"dataRow\":0}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("sess-1"))
            .andExpect(jsonPath("$.status").value("AWAITING_SUBMIT"));
    }

    @Test
    void statusReturns404ForUnknownSession() throws Exception {
        mockMvc.perform(get("/api/flow/does-not-exist/status")
                .header("Authorization", bearer))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Session not found"));
    }

    @Test
    void statusReturns404WhenSessionOwnedByAnotherUser() throws Exception {
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        FlowSession othersSession = new FlowSession("sess-2", "someone-else", driver, null);
        registry.register(othersSession);

        mockMvc.perform(get("/api/flow/sess-2/status")
                .header("Authorization", bearer))
            .andExpect(status().isNotFound());
    }

    @Test
    void submitDelegatesToServiceAndReturnsStatus() throws Exception {
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        FlowSession session = new FlowSession("sess-3", "operator", driver, null);
        session.setStatus(FlowSession.Status.AWAITING_SUBMIT);
        registry.register(session);

        // when submit is invoked, flip status as the real service would
        Mockito.doAnswer(inv -> {
            session.setStatus(FlowSession.Status.SUBMITTED);
            return null;
        }).when(flowService).submit(any(), anyString(), anyString());

        mockMvc.perform(post("/api/flow/sess-3/submit")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"submitLocatorType\":\"css\",\"submitLocatorValue\":\"button[type=submit]\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void cancelReturnsClosed() throws Exception {
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        FlowSession session = new FlowSession("sess-4", "operator", driver, null);
        registry.register(session);

        mockMvc.perform(delete("/api/flow/sess-4")
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}
