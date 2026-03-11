package io.rdfforge.dimension.dto;

public record ObservationColumn(
    String name,
    String propertyUri,
    String role,
    String datatype
) {}
