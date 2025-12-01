package io.rdfforge.engine.shacl;

import io.rdfforge.common.model.ValidationReport;
import org.apache.jena.rdf.model.Model;

public interface ShaclValidator {
    ValidationReport validate(Model dataModel, Model shapesModel);
    ValidationReport validate(Model dataModel, String shapesContent);
    boolean validateSyntax(String shapesContent);
}
