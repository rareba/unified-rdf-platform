package io.rdfforge.engine.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide metadata about an operation plugin.
 * 
 * Use this to add author, version, and documentation info
 * that will be displayed in the Pipeline Designer UI.
 * 
 * Example:
 * 
 * <pre>
 * {@literal @}Component
 * {@literal @}PluginInfo(
 *     author = "John Doe",
 *     version = "1.2.0",
 *     tags = {"transform", "csv"},
 *     documentation = "https://docs.example.com/my-operation"
 * )
 * public class MyOperation implements Operation { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginInfo {

    /**
     * Author or maintainer of the operation.
     */
    String author() default "";

    /**
     * Semantic version of the operation (e.g., "1.0.0").
     */
    String version() default "1.0.0";

    /**
     * Tags for categorization and search.
     */
    String[] tags() default {};

    /**
     * URL to external documentation.
     */
    String documentation() default "";

    /**
     * Minimum RDF Forge version required.
     */
    String minPlatformVersion() default "";

    /**
     * Whether this is a built-in operation or third-party.
     */
    boolean builtIn() default false;
}
