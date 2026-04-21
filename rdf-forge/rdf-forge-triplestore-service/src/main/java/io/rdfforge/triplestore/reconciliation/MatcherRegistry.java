package io.rdfforge.triplestore.reconciliation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all {@link Matcher} beans. Mirrors the OperationRegistry pattern:
 * Spring injects the full list and we index them by {@code id()}.
 */
@Slf4j
@Component
public class MatcherRegistry {

    private final List<Matcher> discovered;
    private final Map<String, Matcher> byId = new ConcurrentHashMap<>();

    public MatcherRegistry(List<Matcher> discovered) {
        this.discovered = discovered;
    }

    @PostConstruct
    public void init() {
        for (Matcher m : discovered) {
            if (byId.put(m.id(), m) != null) {
                log.warn("Duplicate matcher id: {}", m.id());
            }
        }
        log.info("Registered {} matchers: {}", byId.size(), byId.keySet());
    }

    public Optional<Matcher> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Matcher> getAll() {
        return List.copyOf(byId.values());
    }

    public List<Matcher> getEnabled() {
        return byId.values().stream().filter(Matcher::enabled).toList();
    }

    /** Public descriptor for the /matchers API. */
    public record MatcherInfo(String id, String displayName, boolean enabled) {
        public static MatcherInfo from(Matcher m) {
            return new MatcherInfo(m.id(), m.displayName(), m.enabled());
        }
    }

    public List<MatcherInfo> describe() {
        return byId.values().stream()
            .sorted(Comparator.comparing(Matcher::id))
            .map(MatcherInfo::from)
            .toList();
    }
}
