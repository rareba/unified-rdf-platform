package io.rdfforge.dimension.dto;

import java.util.List;
import java.util.Map;

public record ObservationPage(
    List<Map<String, Object>> items,
    List<ObservationColumn> columns,
    long totalCount,
    int page,
    int size
) {}
