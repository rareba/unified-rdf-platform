package io.rdfforge.shacl.service;

import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.engine.shacl.ShaclValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating RDF data against standard cube-link validation profiles.
 * 
 * Supported profiles:
 * - standalone-cube-constraint: Basic RDF Data Cube structure validation
 * - profile-visualize: For visualize.admin.ch (Swiss Federal Statistics)
 * - profile-opendataswiss: For opendata.swiss (DCAT-AP CH compliance)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileValidationService {

    private final ShaclValidator shaclValidator;
    
    // Cache of loaded profile shapes
    private final Map<String, String> profileShapes = new HashMap<>();
    
    // Available profiles
    public static final String PROFILE_STANDALONE = "standalone-cube-constraint";
    public static final String PROFILE_VISUALIZE = "profile-visualize";
    public static final String PROFILE_OPENDATASWISS = "profile-opendataswiss";
    
    private static final List<String> AVAILABLE_PROFILES = List.of(
        PROFILE_STANDALONE,
        PROFILE_VISUALIZE,
        PROFILE_OPENDATASWISS
    );

    @PostConstruct
    public void loadProfiles() {
        for (String profile : AVAILABLE_PROFILES) {
            try {
                String content = loadProfileContent(profile);
                profileShapes.put(profile, content);
                log.info("Loaded validation profile: {}", profile);
            } catch (IOException e) {
                log.warn("Could not load validation profile: {} - {}", profile, e.getMessage());
            }
        }
    }

    private String loadProfileContent(String profileName) throws IOException {
        String resourcePath = "profiles/" + profileName + ".ttl";
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    /**
     * Get list of available validation profiles.
     */
    public List<ProfileInfo> getAvailableProfiles() {
        return List.of(
            new ProfileInfo(PROFILE_STANDALONE, 
                "Standalone Cube Constraint", 
                "Basic RDF Data Cube structure validation"),
            new ProfileInfo(PROFILE_VISUALIZE, 
                "Visualize.admin.ch Profile", 
                "Swiss Federal Statistics visualization platform validation"),
            new ProfileInfo(PROFILE_OPENDATASWISS, 
                "OpenData.swiss Profile", 
                "Swiss Open Government Data portal (DCAT-AP CH) validation")
        );
    }

    /**
     * Validate RDF data against a specific profile.
     */
    public ValidationReport validateAgainstProfile(String dataContent, String dataFormat, String profileName) {
        // Get the profile shape
        String shapeContent = profileShapes.get(profileName);
        if (shapeContent == null) {
            // Try to load it on demand
            try {
                shapeContent = loadProfileContent(profileName);
                profileShapes.put(profileName, shapeContent);
            } catch (IOException e) {
                return createErrorReport("Unknown profile: " + profileName);
            }
        }

        // Parse the data
        Model dataModel = ModelFactory.createDefaultModel();
        try {
            String format = dataFormat != null ? dataFormat : "TURTLE";
            dataModel.read(new StringReader(dataContent), null, format);
        } catch (Exception e) {
            return createErrorReport("Failed to parse RDF data: " + e.getMessage());
        }

        // Run validation
        return shaclValidator.validate(dataModel, shapeContent);
    }

    /**
     * Validate RDF data against all profiles and return combined results.
     */
    public Map<String, ValidationReport> validateAgainstAllProfiles(String dataContent, String dataFormat) {
        Map<String, ValidationReport> results = new HashMap<>();
        
        for (String profile : AVAILABLE_PROFILES) {
            ValidationReport report = validateAgainstProfile(dataContent, dataFormat, profile);
            results.put(profile, report);
        }
        
        return results;
    }

    /**
     * Check if a profile is available.
     */
    public boolean isProfileAvailable(String profileName) {
        return AVAILABLE_PROFILES.contains(profileName);
    }

    private ValidationReport createErrorReport(String message) {
        ValidationReport.ValidationResult result = ValidationReport.ValidationResult.builder()
            .severity(ValidationReport.ValidationResult.Severity.VIOLATION)
            .message(message)
            .build();
            
        return ValidationReport.builder()
            .conforms(false)
            .violationCount(1)
            .results(List.of(result))
            .build();
    }

    /**
     * DTO for profile information.
     */
    public record ProfileInfo(
        String id,
        String name,
        String description
    ) {}
}

