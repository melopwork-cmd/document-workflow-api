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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    public record Intake(@NotBlank @Size(max = 120) String title) {}
    public record Transition(@NotNull DocumentState state, @NotNull @Min(0) Integer expectedVersion) {}

    public record TransitionOptions(int version, List<DocumentState> allowedStates) {}

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DocumentService.Document> intake(@Valid @RequestBody Intake body) {
        var document = service.create(body.title());
        return ResponseEntity.created(URI.create("/documents/" + document.id())).body(document);
    }

    @GetMapping
    public List<DocumentService.Document> list(
            @RequestParam(required = false) DocumentState state, @RequestParam(defaultValue = "25") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
        return service.list(state, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentService.Document> get(@PathVariable UUID id) {
        var document = service.get(id);
        return ResponseEntity.ok().eTag("\"" + id + ":" + document.version() + "\"").body(document);
    }

    @GetMapping("/{id}/history")
    public List<DocumentService.Event> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @GetMapping("/{id}/transitions")
    public TransitionOptions transitions(@PathVariable UUID id) {
        var document = service.get(id);
        return new TransitionOptions(document.version(), document.state().availableTransitions());
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
