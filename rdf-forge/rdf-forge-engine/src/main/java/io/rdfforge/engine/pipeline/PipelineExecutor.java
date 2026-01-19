package io.rdfforge.engine.pipeline;

import io.rdfforge.common.exception.PipelineExecutionException;
import io.rdfforge.common.model.PipelineStep;
import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.OperationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineExecutor {
    private final OperationRegistry operationRegistry;

    public ExecutionResult execute(PipelineDefinition pipeline, Map<String, Object> variables, 
                                    boolean dryRun, ExecutionCallback callback) {
        ExecutionContext context = new ExecutionContext(pipeline.getId(), variables, dryRun, callback);
        
        try {
            callback.onStart(pipeline.getId());
            if (dryRun) {
                callback.onLog(null, "INFO", "Starting pipeline execution in DRY RUN mode");
            }
            
            List<PipelineStep> sortedSteps = topologicalSort(pipeline.getSteps());
            
            for (PipelineStep step : sortedSteps) {
                context.setCurrentStep(step.getId());
                callback.onStepStart(step.getId(), step.getName());
                
                StepResult result = executeStep(step, context);
                context.addStepResult(step.getId(), result);
                
                if (!result.isSuccess()) {
                    callback.onStepComplete(step.getId(), false, result.getErrorMessage());
                    throw new PipelineExecutionException("Step failed: " + step.getName() + 
                        " - " + result.getErrorMessage());
                }
                
                callback.onStepComplete(step.getId(), true, null);
            }
            
            callback.onComplete(true, null);
            return buildResult(context, true, null);
            
        } catch (Exception e) {
            log.error("Pipeline execution failed", e);
            callback.onComplete(false, e.getMessage());
            return buildResult(context, false, e.getMessage());
        }
    }

    private StepResult executeStep(PipelineStep step, ExecutionContext context) {
        Operation operation = operationRegistry.getOrThrow(step.getOperationType());
        
        if (context.isDryRun() && operation.getType() == Operation.OperationType.OUTPUT) {
            context.getCallback().onLog(step.getId(), "INFO", "Dry run: Skipping output operation " + step.getName());
            return StepResult.builder()
                .stepId(step.getId())
                .success(true)
                .metadata(Map.of("dryRun", true, "skipped", true))
                .build();
        }
        
        Map<String, Object> resolvedParams = resolveParameters(step.getParameters(), context.getVariables());
        
        Stream<?> inputStream = null;
        Model inputModel = null;

        if (step.getInputConnections() != null && !step.getInputConnections().isEmpty()) {
            // Explicit connections defined - use those
            for (String inputStepId : step.getInputConnections()) {
                StepResult inputResult = context.getStepResult(inputStepId);
                if (inputResult != null) {
                    if (inputResult.getOutputStream() != null) {
                        inputStream = inputResult.getOutputStream();
                    }
                    if (inputResult.getOutputModel() != null) {
                        if (inputModel == null) {
                            inputModel = ModelFactory.createDefaultModel();
                        }
                        inputModel.add(inputResult.getOutputModel());
                    }
                }
            }
        } else if (operation.getType() != Operation.OperationType.SOURCE) {
            // No explicit connections and not a SOURCE operation - use previous step's output (implicit chaining)
            StepResult previousResult = context.getPreviousStepResult();
            if (previousResult != null) {
                if (previousResult.getOutputStream() != null) {
                    inputStream = previousResult.getOutputStream();
                }
                if (previousResult.getOutputModel() != null) {
                    inputModel = previousResult.getOutputModel();
                }
            }
        }

        Operation.OperationCallback opCallback = new Operation.OperationCallback() {
            @Override
            public void onProgress(long processed, long total) {
                context.getCallback().onProgress(step.getId(), processed, total);
            }

            @Override
            public void onLog(String level, String message) {
                context.getCallback().onLog(step.getId(), level, message);
            }

            @Override
            public void onMetric(String name, Object value) {
                context.addMetric(step.getId() + "." + name, value);
            }
        };

        Operation.OperationContext opContext = new Operation.OperationContext(
            resolvedParams, inputStream, inputModel, context.getVariables(), opCallback
        );

        try {
            Operation.OperationResult opResult = operation.execute(opContext);
            
            return StepResult.builder()
                .stepId(step.getId())
                .success(opResult.success())
                .outputStream(opResult.outputStream())
                .outputModel(opResult.outputModel())
                .metadata(opResult.metadata())
                .errorMessage(opResult.errorMessage())
                .build();
                
        } catch (OperationException e) {
            return StepResult.builder()
                .stepId(step.getId())
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private Map<String, Object> resolveParameters(Map<String, Object> params, Map<String, Object> variables) {
        if (params == null) return Collections.emptyMap();
        
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                resolved.put(entry.getKey(), resolveVariables(strValue, variables));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    private String resolveVariables(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> var : variables.entrySet()) {
            result = result.replace("${" + var.getKey() + "}", 
                var.getValue() != null ? var.getValue().toString() : "");
        }
        if (result.startsWith("${") && result.endsWith("}")) {
            String envVar = result.substring(2, result.length() - 1);
            String envValue = System.getenv(envVar);
            if (envValue != null) {
                return envValue;
            }
        }
        return result;
    }

    private List<PipelineStep> topologicalSort(List<PipelineStep> steps) {
        Map<String, PipelineStep> stepMap = new HashMap<>();
        Map<String, Set<String>> dependencies = new HashMap<>();
        
        for (PipelineStep step : steps) {
            stepMap.put(step.getId(), step);
            dependencies.put(step.getId(), new HashSet<>(
                step.getInputConnections() != null ? step.getInputConnections() : Collections.emptyList()
            ));
        }

        List<PipelineStep> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        while (sorted.size() < steps.size()) {
            boolean progress = false;
            for (PipelineStep step : steps) {
                if (visited.contains(step.getId())) continue;
                
                Set<String> deps = dependencies.get(step.getId());
                if (deps.isEmpty() || visited.containsAll(deps)) {
                    sorted.add(step);
                    visited.add(step.getId());
                    progress = true;
                }
            }
            if (!progress) {
                throw new PipelineExecutionException("Circular dependency detected in pipeline");
            }
        }
        
        return sorted;
    }

    private ExecutionResult buildResult(ExecutionContext context, boolean success, String errorMessage) {
        return ExecutionResult.builder()
            .pipelineId(context.getPipelineId())
            .success(success)
            .errorMessage(errorMessage)
            .startTime(context.getStartTime())
            .endTime(Instant.now())
            .metrics(context.getMetrics())
            .stepResults(context.getAllStepResults())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class PipelineDefinition {
        private String id;
        private String name;
        private List<PipelineStep> steps;
        private Map<String, Object> defaultVariables;
    }

    @lombok.Data
    @lombok.Builder
    public static class StepResult {
        private String stepId;
        private boolean success;
        private Stream<?> outputStream;
        private Model outputModel;
        private Map<String, Object> metadata;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    public static class ExecutionResult {
        private String pipelineId;
        private boolean success;
        private String errorMessage;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Object> metrics;
        private Map<String, StepResult> stepResults;
    }

    @lombok.Data
    private static class ExecutionContext {
        private final String pipelineId;
        private final Map<String, Object> variables;
        private final boolean dryRun;
        private final ExecutionCallback callback;
        private final Instant startTime = Instant.now();
        private final Map<String, StepResult> stepResults = new ConcurrentHashMap<>();
        private final Map<String, Object> metrics = new ConcurrentHashMap<>();
        private String currentStep;
        private StepResult previousStepResult;

        public void addStepResult(String stepId, StepResult result) {
            stepResults.put(stepId, result);
            previousStepResult = result; // Track for implicit chaining
        }

        public StepResult getStepResult(String stepId) {
            return stepResults.get(stepId);
        }

        public StepResult getPreviousStepResult() {
            return previousStepResult;
        }

        public Map<String, StepResult> getAllStepResults() {
            return new HashMap<>(stepResults);
        }

        public void addMetric(String name, Object value) {
            metrics.put(name, value);
        }
    }

    public interface ExecutionCallback {
        void onStart(String pipelineId);
        void onStepStart(String stepId, String stepName);
        void onStepComplete(String stepId, boolean success, String errorMessage);
        void onProgress(String stepId, long processed, long total);
        void onLog(String stepId, String level, String message);
        void onComplete(boolean success, String errorMessage);
    }
}
