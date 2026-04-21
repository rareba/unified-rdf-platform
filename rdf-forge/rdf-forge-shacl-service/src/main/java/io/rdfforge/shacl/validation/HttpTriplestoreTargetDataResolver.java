package io.rdfforge.shacl.validation;

import io.rdfforge.common.exception.TriplestoreConnectionException;
import io.rdfforge.shacl.validation.dto.ValidationRunRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Production {@link TargetDataResolver} that fetches the data graph by
 * calling the triplestore-service's export endpoint over HTTP. Active in
 * every profile except {@code test}, which uses a fixture-backed
 * implementation so the SHACL/SPARQL execution paths can be exercised
 * without a running triplestore.
 */
@Slf4j
@Component
@Profile("!test")
public class HttpTriplestoreTargetDataResolver implements TargetDataResolver {

    private final RestTemplate restTemplate;
    private final String triplestoreBaseUrl;

    public HttpTriplestoreTargetDataResolver(
            RestTemplateBuilder builder,
            @Value("${rdf-forge.services.triplestore.base-url:http://localhost:8006}")
            String triplestoreBaseUrl) {
        this.restTemplate = builder.build();
        this.triplestoreBaseUrl = triplestoreBaseUrl.endsWith("/")
            ? triplestoreBaseUrl.substring(0, triplestoreBaseUrl.length() - 1)
            : triplestoreBaseUrl;
    }

    @Override
    public Model resolve(ValidationRunRequest request) {
        if (request.targetTriplestoreId() == null || request.targetGraph() == null
                || request.targetGraph().isBlank()) {
            throw new TriplestoreConnectionException(
                "Target triplestoreId and targetGraph are required for validation runs");
        }
        String url = String.format(
            "%s/api/v1/triplestores/%s/graphs/export?graphUri=%s&format=TURTLE",
            triplestoreBaseUrl,
            request.targetTriplestoreId(),
            java.net.URLEncoder.encode(request.targetGraph(), StandardCharsets.UTF_8));

        try {
            String body = restTemplate.getForObject(url, String.class);
            Model model = ModelFactory.createDefaultModel();
            if (body != null && !body.isBlank()) {
                RDFDataMgr.read(model,
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                    Lang.TURTLE);
            }
            return model;
        } catch (RestClientException e) {
            log.error("Failed to fetch target graph {} from triplestore {}",
                request.targetGraph(), request.targetTriplestoreId(), e);
            throw new TriplestoreConnectionException(
                "Unable to fetch target graph: " + e.getMessage());
        }
    }
}
