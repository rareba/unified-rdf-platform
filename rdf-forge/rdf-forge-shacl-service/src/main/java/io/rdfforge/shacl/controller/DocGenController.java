package io.rdfforge.shacl.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.shacl.docs.ApiDocFormat;
import io.rdfforge.shacl.docs.DocGenService;
import io.rdfforge.shacl.docs.SemanticApiDoc;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Exposes {@link DocGenService} at {@code /api/v1/docs/project/{projectId}}.
 *
 * <p>Two rendering paths driven by {@code ?format=HTML|JSON}:
 * <ul>
 *   <li>{@code HTML} — returns a self-contained HTML document ({@code text/html}).</li>
 *   <li>{@code JSON} (default) — returns a pretty-printed {@link SemanticApiDoc}
 *       ({@code application/json}).</li>
 * </ul>
 *
 * Gateway routes: add {@code /api/v1/docs/**} to shacl-service predicate.
 */
@RestController
@RequestMapping("/api/v1/docs")
@RequiredArgsConstructor
@Tag(name = "Docs", description = "Semantic API documentation generator")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class DocGenController {

    private final DocGenService docGenService;

    @GetMapping(value = "/project/{projectId}")
    @Operation(summary = "Generate a Semantic API doc for a project")
    public ResponseEntity<?> generate(
            @PathVariable UUID projectId,
            @RequestParam(value = "format", defaultValue = "JSON") ApiDocFormat format,
            @CurrentUser AuthUser user) {
        SemanticApiDoc doc = docGenService.generate(projectId, user);
        String rendered = docGenService.render(doc, format);
        MediaType ct = format == ApiDocFormat.HTML
                ? MediaType.TEXT_HTML
                : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(ct).body(rendered);
    }

    /**
     * Variant that returns the {@link SemanticApiDoc} directly (auto-jackson)
     * so the Angular client can bind it as a typed object instead of parsing
     * the rendered JSON string.
     */
    @GetMapping(value = "/project/{projectId}/model")
    @Operation(summary = "Return the raw SemanticApiDoc structure (JSON)")
    public ResponseEntity<SemanticApiDoc> model(
            @PathVariable UUID projectId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(docGenService.generate(projectId, user));
    }
}
