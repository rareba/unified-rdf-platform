package io.rdfforge.shacl.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.shacl.service.ProfileValidationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exposes cube-link validation profiles as extensions for the catalog UI.
 *
 * <p>Two kinds:
 * <ul>
 *   <li>{@code /api/v1/extensions/validators} — every cube validator profile
 *       registered in {@link ProfileValidationService}. These represent the
 *       {@code VALIDATOR} extension kind.</li>
 *   <li>{@code /api/v1/extensions/cube-profiles} — the same list surfaced
 *       under {@link ExtensionKind#CUBE_PROFILE} for UI filtering purposes.</li>
 * </ul>
 * Shape templates (reusable SHACL bundles) can be added later under the same
 * VALIDATOR kind — TODO: wire {@code ShapeBuilderService} templates.
 */
@RestController
@RequestMapping("/api/v1/extensions")
@RequiredArgsConstructor
@Tag(name = "Extensions", description = "Plugin / registry catalog for shacl-service")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ExtensionsController {

    private static final String MODULE = "rdf-forge-shacl-service";

    private final ProfileValidationService profileValidationService;

    @GetMapping("/validators")
    public ResponseEntity<List<ExtensionDescriptor>> listValidators() {
        return ResponseEntity.ok(buildDescriptors(ExtensionKind.VALIDATOR));
    }

    @GetMapping("/cube-profiles")
    public ResponseEntity<List<ExtensionDescriptor>> listCubeProfiles() {
        return ResponseEntity.ok(buildDescriptors(ExtensionKind.CUBE_PROFILE));
    }

    private List<ExtensionDescriptor> buildDescriptors(ExtensionKind kind) {
        List<ExtensionDescriptor> out = new ArrayList<>();
        for (ProfileValidationService.ProfileInfo info : profileValidationService.getAvailableProfiles()) {
            out.add(new ExtensionDescriptor(
                info.id(),
                kind,
                info.name(),
                "1.0",
                info.description() == null ? "" : info.description(),
                List.of("cube", "shacl"),
                Map.of(),
                MODULE,
                null,
                true
            ));
        }
        return out;
    }
}
