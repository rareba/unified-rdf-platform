package io.rdfforge.engine.operation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OperationRegistry {
    private final Map<String, Operation> operations = new ConcurrentHashMap<>();
    private final Map<Operation.OperationType, List<Operation>> operationsByType = new ConcurrentHashMap<>();
    private final List<Operation> discoveredOperations;

    public OperationRegistry(List<Operation> discoveredOperations) {
        this.discoveredOperations = discoveredOperations;
    }

    @PostConstruct
    public void init() {
        for (Operation operation : discoveredOperations) {
            register(operation);
        }
        log.info("Registered {} operations", operations.size());
    }

    public void register(Operation operation) {
        operations.put(operation.getId(), operation);
        operationsByType
                .computeIfAbsent(operation.getType(), k -> new ArrayList<>())
                .add(operation);
        log.debug("Registered operation: {} ({})", operation.getId(), operation.getType());
    }

    public Optional<Operation> get(String operationId) {
        return Optional.ofNullable(operations.get(operationId));
    }

    public Operation getOrThrow(String operationId) {
        return get(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + operationId));
    }

    public List<Operation> getAll() {
        return new ArrayList<>(operations.values());
    }

    public List<Operation> getByType(Operation.OperationType type) {
        return operationsByType.getOrDefault(type, Collections.emptyList());
    }

    public Map<Operation.OperationType, List<OperationInfo>> getCatalog() {
        Map<Operation.OperationType, List<OperationInfo>> catalog = new EnumMap<>(Operation.OperationType.class);
        for (var entry : operationsByType.entrySet()) {
            List<OperationInfo> infos = entry.getValue().stream()
                    .map(this::buildOperationInfo)
                    .toList();
            catalog.put(entry.getKey(), infos);
        }
        return catalog;
    }

    /**
     * Get extended operation info including plugin metadata.
     */
    public Optional<OperationInfo> getOperationInfo(String operationId) {
        return get(operationId).map(this::buildOperationInfo);
    }

    private OperationInfo buildOperationInfo(Operation op) {
        PluginMetadata pluginMeta = null;
        PluginInfo pluginInfo = op.getClass().getAnnotation(PluginInfo.class);
        if (pluginInfo != null) {
            pluginMeta = new PluginMetadata(
                    pluginInfo.author(),
                    pluginInfo.version(),
                    Arrays.asList(pluginInfo.tags()),
                    pluginInfo.documentation(),
                    pluginInfo.builtIn());
        }
        return new OperationInfo(
                op.getId(),
                op.getName(),
                op.getDescription(),
                op.getType(),
                op.getParameters(),
                pluginMeta);
    }

    public record OperationInfo(
            String id,
            String name,
            String description,
            Operation.OperationType type,
            Map<String, Operation.ParameterSpec> parameters,
            PluginMetadata plugin) {
    }

    public record PluginMetadata(
            String author,
            String version,
            List<String> tags,
            String documentation,
            boolean builtIn) {
    }
}
