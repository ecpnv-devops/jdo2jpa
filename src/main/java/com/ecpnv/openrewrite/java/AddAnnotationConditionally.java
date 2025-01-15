package com.ecpnv.openrewrite.java;

import java.util.Comparator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.jdo2jpa.Constants;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that conditionally adds an annotation to variables in the source code based on the presence
 * of another annotation that matches a specified regular expression.
 * <p>
 * The purpose of this class is to analyze annotations in the source code and, if an annotation matching
 * the specified regular expression is found, a new annotation will be added as per the provided template.
 * This can be used to enforce annotation conventions or modify source code annotations systematically.
 * <p>
 * Key behavior:
 * - Matches existing annotations using the provided regular expression.
 * - Adds the new annotation only if it does not already exist.
 * - Ensures the required import is included for the new annotation type.
 * <p>
 * TODO Currently only supports matching of annotations on variable declarations, but should also support methods
 *      and class declarations
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddAnnotationConditionally extends Recipe {

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be changed.",
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation to add.",
            example = "javax.persistence.Lob")
    String annotationType;

    @Option(displayName = "Attribute template to add",
            description = "The template of the annotation to add.",
            example = "@Lob")
    String annotationTemplate;

    @JsonCreator
    public AddAnnotationConditionally(
            @NonNull @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("annotationTemplate") String annotationTemplate) {
        this.matchByRegularExpression = matchByRegularExpression;
        this.annotationType = annotationType;
        this.annotationTemplate = annotationTemplate;
    }

    @Override
    public String getDisplayName() {
        return "Add annotation conditionally";
    }

    @Override
    public String getDescription() {
        return "Add annotation when another annotation is found that matches the given regular expression.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vars = super.visitVariableDeclarations(multiVariable, ctx);
                if (!FindAnnotations.find(vars, annotationType).isEmpty()) {
                    // Do nothing when the annotation already is present
                    return vars;
                }
                return vars.getLeadingAnnotations().stream()
                        .filter(a -> a.toString().matches(matchByRegularExpression))
                        .findFirst()
                        .map(a -> {
                            // Add annotation to variable
                            maybeAddImport(annotationType);
                            return (J.VariableDeclarations) JavaTemplate.builder(annotationTemplate)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.JPA_CLASS_PATH))
                                    .imports(annotationType)
                                    .build()
                                    .apply(getCursor(), vars.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        })
                        .orElse(vars);
            }
        };
    }

}
