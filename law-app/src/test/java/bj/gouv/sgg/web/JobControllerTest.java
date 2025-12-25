package bj.gouv.sgg.web;

import bj.gouv.sgg.job.JobOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    private MockMvc mvc;

    @Mock
    private JobOrchestrator orchestrator;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new JobController(orchestrator)).build();
    }

    @Test
    void fetchCurrentEndpointShouldReturnOk() throws Exception {
        doNothing().when(orchestrator).runJob(anyString(), org.mockito.ArgumentMatchers.anyMap());

        mvc.perform(post("/api/jobs/fetchCurrent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"loi\"}"))
                .andExpect(status().isOk());

        verify(orchestrator).runJob(org.mockito.ArgumentMatchers.eq("fetchCurrentJob"), org.mockito.ArgumentMatchers.anyMap());
    }
}
