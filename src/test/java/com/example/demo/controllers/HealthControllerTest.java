package com.example.demo.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(dataSource)).build();
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void readiness_dbUp_returns200() throws Exception {
        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void readiness_dbDown_returns503() throws Exception {
        var badDataSource = mock(DataSource.class);
        when(badDataSource.getConnection()).thenThrow(new RuntimeException("DB unreachable"));
        var controller = new HealthController(badDataSource);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void readiness_invalidConnection_returns503() throws Exception {
        var badConnection = mock(Connection.class);
        when(badConnection.isValid(1)).thenReturn(false);
        var badDataSource = mock(DataSource.class);
        when(badDataSource.getConnection()).thenReturn(badConnection);
        var controller = new HealthController(badDataSource);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable());
    }
}
