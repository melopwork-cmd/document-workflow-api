package dev.melopwork.documentworkflow;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentWorkflowTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired DocumentService documents;

    @Test
    void capturesValidTransitionsInVersionOrder() throws Exception {
        var id = intake();
        http.perform(post("/documents/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"IN_REVIEW\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_REVIEW"))
                .andExpect(jsonPath("$.version").value(1));

        http.perform(post("/documents/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"APPROVED\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        http.perform(get("/documents/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].toState").value("RECEIVED"))
                .andExpect(jsonPath("$[2].fromState").value("IN_REVIEW"))
                .andExpect(jsonPath("$[2].toState").value("APPROVED"));
    }

    @Test
    void rejectsIllegalTransitionAndLeavesHistoryUntouched() throws Exception {
        var id = intake();
        http.perform(post("/documents/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"APPROVED\",\"expectedVersion\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(documents.get(id).state()).isEqualTo(DocumentState.RECEIVED);
        assertThat(documents.history(id)).hasSize(1);
    }

    @Test
    void rejectsInvalidInputAndUnknownDocuments() throws Exception {
        http.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
        http.perform(get("/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        var id = intake();
        http.perform(post("/documents/{id}/transitions", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"IN_REVIEW\"}"))
                .andExpect(status().isBadRequest());
        http.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void staleVersionGetsConflictFromHttpApi() throws Exception {
        var first = documents.create("Contrato de demonstração");
        documents.transition(first.id(), DocumentState.IN_REVIEW, 0);
        http.perform(post("/documents/{id}/transitions", first.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"APPROVED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        assertThat(documents.history(first.id())).hasSize(2);
    }

    @Test
    void simultaneousWritesAppendOneEventForTheExpectedVersion() throws Exception {
        var id = documents.create("Revisão concorrente").id();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> attemptTransition(id, start));
            var second = pool.submit(() -> attemptTransition(id, start));
            start.countDown();

            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("accepted", "conflict");
            assertThat(documents.history(id)).extracting(DocumentService.Event::version).containsExactly(0, 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void listsDocumentsWithStateFilterAndBoundedLimit() throws Exception {
        var id = documents.create("Document for listing").id();
        documents.transition(id, DocumentState.IN_REVIEW, 0);
        documents.transition(id, DocumentState.REJECTED, 1);
        documents.transition(id, DocumentState.ARCHIVED, 2);
        documents.create("Document still in intake");

        http.perform(get("/documents").param("state", "ARCHIVED").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].state").value("ARCHIVED"));
        http.perform(get("/documents").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        http.perform(get("/documents").param("limit", "101"))
                .andExpect(status().isBadRequest());
        http.perform(get("/documents").param("state", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conditionallyReadsDocumentByVersion() throws Exception {
        var id = intake();
        var etag = http.perform(get("/documents/{id}", id))
                .andExpect(status().isOk()).andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");
        http.perform(get("/documents/{id}", id).header("If-None-Match", etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        documents.transition(id, DocumentState.IN_REVIEW, 0);
        http.perform(get("/documents/{id}", id).header("If-None-Match", etag))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"" + id + ":1\""))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void returnsAllowedTransitionsForTheCurrentVersion() throws Exception {
        var id = intake();
        http.perform(get("/documents/{id}/transitions", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.allowedStates[0]").value("IN_REVIEW"));
        documents.transition(id, DocumentState.IN_REVIEW, 0);
        http.perform(get("/documents/{id}/transitions", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.allowedStates[0]").value("APPROVED"))
                .andExpect(jsonPath("$.allowedStates[1]").value("REJECTED"));
    }

    @Test
    void returnsTheNextDocumentAtTheRequestedOffset() throws Exception {
        documents.create("First document for paging");
        documents.create("Second document for paging");
        var body = http.perform(get("/documents").param("limit", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var secondId = json.readTree(body).get(1).get("id").asText();
        http.perform(get("/documents").param("limit", "1").param("offset", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondId));
        http.perform(get("/documents").param("offset", "-1")).andExpect(status().isBadRequest());
    }

    private UUID intake() throws Exception {
        var created = http.perform(post("/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Documento de demonstração\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.state").value("RECEIVED"))
                .andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsByteArray()).get("id").asText());
    }

    private String attemptTransition(UUID id, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            documents.transition(id, DocumentState.IN_REVIEW, 0);
            return "accepted";
        } catch (WorkflowException error) {
            assertThat(error.reason()).isEqualTo(WorkflowException.Reason.VERSION_CONFLICT);
            return "conflict";
        }
    }
}
