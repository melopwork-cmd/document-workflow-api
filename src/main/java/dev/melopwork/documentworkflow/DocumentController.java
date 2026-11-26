package dev.melopwork.documentworkflow;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    public record Intake(@NotBlank @Size(max = 120) String title) {}
    public record Transition(@NotNull DocumentState state, @NotNull @Min(0) Integer expectedVersion) {}

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DocumentService.Document> intake(@Valid @RequestBody Intake body) {
        var document = service.create(body.title());
        return ResponseEntity.created(URI.create("/documents/" + document.id())).body(document);
    }

    @GetMapping("/{id}")
    public DocumentService.Document get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/history")
    public List<DocumentService.Event> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @PostMapping("/{id}/transitions")
    public DocumentService.Document transition(@PathVariable UUID id, @Valid @RequestBody Transition body) {
        return service.transition(id, body.state(), body.expectedVersion());
    }

    @ExceptionHandler(WorkflowException.class)
    ProblemDetail workflowError(WorkflowException error) {
        var status = switch (error.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VERSION_CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_TRANSITION -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ProblemDetail.forStatusAndDetail(status, error.getMessage());
    }
}
