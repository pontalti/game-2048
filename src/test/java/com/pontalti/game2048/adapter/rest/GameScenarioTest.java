package com.pontalti.game2048.adapter.rest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end scenario tests for win, loss, and hint — driven <b>purely over
 * HTTP</b>, with no access to internal beans.
 * <p>
 * The normal {@code POST /games} is random, so a specific win/loss position is
 * unreachable by blind play. These tests run under the {@code test} profile,
 * which activates {@link TestSupportController} and its {@code POST /test/games}
 * endpoint — letting us plant a known starting board <b>through HTTP</b>. Every
 * step, including seeding, is an HTTP call; nothing is injected directly.
 * <p>
 * The seed endpoint exists only under the {@code test} profile, so it is absent
 * from the production API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameScenarioTest {

    private static final Integer N = null; // readable empty cell

    @Autowired
    MockMvc mvc;

    /** Plants a game with the given board via the test-only HTTP endpoint; returns its id. */
    private String seedGame(String boardJson) throws Exception {
        MvcResult result = mvc.perform(post("/test/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"board\":" + boardJson + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("Playing into 2048 ends the game as WON (over HTTP)")
    void winsByReaching2048() throws Exception {
        // Two 1024 tiles side by side: a single LEFT merges them into 2048.
        String id = seedGame("[[1024,1024,null,null],[null,null,null,null],[null,null,null,null],[null,null,null,null]]");

        mvc.perform(post("/games/{id}/moves", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"LEFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"));

        mvc.perform(get("/games/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"));
    }

    @Test
    @DisplayName("A full checkerboard board is reported as LOST (over HTTP)")
    void deadEndBoardIsLost() throws Exception {
        // Full board, no two adjacent equal tiles -> no move is possible. The game is
        // born LOST on construction. (A move+spawn loss would be non-deterministic,
        // since a spawned 2 vs 4 could decide whether the board stays locked.)
        String id = seedGame("[[2,4,2,4],[4,2,4,2],[2,4,2,4],[4,2,4,2]]");

        mvc.perform(get("/games/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));
    }

    @Test
    @DisplayName("Hint returns a legal direction for a non-terminal board (over HTTP)")
    void hintSuggestsALegalMove() throws Exception {
        String id = seedGame("[[2,null,4,null],[null,8,null,16],[32,null,null,2],[null,4,128,null]]");

        mvc.perform(get("/games/{id}/hint", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion",
                        anyOf(is("LEFT"), is("RIGHT"), is("UP"), is("DOWN"))));
    }

    @Test
    @DisplayName("Hint on a dead-end board returns null suggestion (over HTTP)")
    void hintOnDeadEndIsNull() throws Exception {
        String id = seedGame("[[2,4,2,4],[4,2,4,2],[2,4,2,4],[4,2,4,2]]");

        mvc.perform(get("/games/{id}/hint", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion").doesNotExist()); // null in JSON
    }

    @Test
    @DisplayName("A move on a finished game is a no-op that keeps WON (over HTTP)")
    void movingAfterWinKeepsStatus() throws Exception {
        String id = seedGame("[[1024,1024,null,null],[null,null,null,null],[null,null,null,null],[null,null,null,null]]");

        mvc.perform(post("/games/{id}/moves", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"LEFT\"}"))
                .andExpect(jsonPath("$.status").value("WON"));

        // A further move must not resurrect play; status stays WON.
        mvc.perform(post("/games/{id}/moves", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"RIGHT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WON"));
    }

    @Test
    @DisplayName("A move on a LOST game is a no-op that keeps LOST (over HTTP)")
    void movingAfterLossKeepsStatus() throws Exception {
        String id = seedGame("[[2,4,2,4],[4,2,4,2],[2,4,2,4],[4,2,4,2]]");

        mvc.perform(post("/games/{id}/moves", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"LEFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));
    }
}