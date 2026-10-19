package dev.chatads.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdControllerTest {

    @Autowired
    MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions call(String json) throws Exception {
        return mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void returnsLabeledAdWithNullAnswerSlot() throws Exception {
        call("{\"prompt\":\"running shoes for a marathon\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer_slot").value((Object) null))
                .andExpect(jsonPath("$.sponsored[0].product_id").value("p-1001"))
                .andExpect(jsonPath("$.sponsored[0].label").value("Sponsored"))
                .andExpect(jsonPath("$.sponsored[0].title").isNotEmpty())
                .andExpect(jsonPath("$.sponsored[0].reason").isNotEmpty());
    }

    @Test
    void unmatchedPromptReturnsEmptyList() throws Exception {
        call("{\"prompt\":\"what is the capital of France\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored").isEmpty());
    }

    @Test
    void bannedCategoryNeverServed() throws Exception {
        call("{\"prompt\":\"where can I buy a vape\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored").isEmpty());
    }

    @Test
    void assistantAnswerComesBackUntouchedAndSeparateFromTheAd() throws Exception {
        call("{\"prompt\":\"running shoes for a marathon\",\"assistant_answer\":\"Look for a light shoe with a wide toe box.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer_slot").value("Look for a light shoe with a wide toe box."))
                .andExpect(jsonPath("$.sponsored[0].label").value("Sponsored"));
    }

    @Test
    void blankOrMissingPromptIs400() throws Exception {
        call("{\"prompt\":\"  \"}").andExpect(status().isBadRequest());
        call("{}").andExpect(status().isBadRequest());
    }
}
