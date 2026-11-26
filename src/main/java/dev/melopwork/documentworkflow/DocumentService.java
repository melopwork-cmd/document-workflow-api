package dev.melopwork.documentworkflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    public record Document(UUID id, String title, DocumentState state, int version, Instant updatedAt) {}
    public record Event(int version, DocumentState fromState, DocumentState toState, Instant occurredAt) {}

    private final JdbcClient jdbc;

    public DocumentService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Document create(String title) {
        var result = new Document(UUID.randomUUID(), title.strip(), DocumentState.RECEIVED, 0, Instant.ofEpochMilli(System.currentTimeMillis()));
        jdbc.sql("INSERT INTO documents (id, title, state, version, updated_at) VALUES (?, ?, ?, ?, ?)")
                .param(result.id().toString()).param(result.title()).param(result.state().name())
                .param(result.version()).param(result.updatedAt().toEpochMilli()).update();
        append(result, null);
        return result;
    }

    public Document get(UUID id) {
        return jdbc.sql("SELECT id, title, state, version, updated_at FROM documents WHERE id = ?")
                .param(id.toString())
                .query((rs, row) -> new Document(UUID.fromString(rs.getString("id")), rs.getString("title"),
                        DocumentState.valueOf(rs.getString("state")), rs.getInt("version"),
                        Instant.ofEpochMilli(rs.getLong("updated_at"))))
                .optional()
                .orElseThrow(() -> new WorkflowException(WorkflowException.Reason.NOT_FOUND, "Document not found"));
    }

    public List<Event> history(UUID id) {
        get(id);
        return jdbc.sql("""
                SELECT version, from_state, to_state, occurred_at
                FROM document_events WHERE document_id = ? ORDER BY version
                """)
                .param(id.toString())
                .query((rs, row) -> {
                    var previous = rs.getString("from_state");
                    return new Event(rs.getInt("version"), previous == null ? null : DocumentState.valueOf(previous),
                            DocumentState.valueOf(rs.getString("to_state")),
                            Instant.ofEpochMilli(rs.getLong("occurred_at")));
                })
                .list();
    }

    @Transactional
    public Document transition(UUID id, DocumentState next, int expectedVersion) {
        var current = get(id);
        if (current.version() != expectedVersion) {
            throw conflict();
        }
        if (!current.state().allows(next)) {
            throw new WorkflowException(WorkflowException.Reason.INVALID_TRANSITION,
                    "Transition from " + current.state() + " to " + next + " is invalid");
        }

        var changed = new Document(id, current.title(), next, expectedVersion + 1, Instant.ofEpochMilli(System.currentTimeMillis()));
        int count = jdbc.sql("""
                UPDATE documents SET state = ?, version = ?, updated_at = ?
                WHERE id = ? AND version = ?
                """)
                .param(next.name()).param(changed.version()).param(changed.updatedAt().toEpochMilli())
                .param(id.toString()).param(expectedVersion).update();
        if (count != 1) {
            throw conflict();
        }
        append(changed, current.state());
        return changed;
    }

    private void append(Document current, DocumentState previous) {
        jdbc.sql("""
                INSERT INTO document_events (document_id, version, from_state, to_state, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """)
                .param(current.id().toString()).param(current.version())
                .param(previous == null ? null : previous.name()).param(current.state().name())
                .param(current.updatedAt().toEpochMilli()).update();
    }

    private static WorkflowException conflict() {
        return new WorkflowException(WorkflowException.Reason.VERSION_CONFLICT,
                "The document changed. Read the current version and try again.");
    }
}
