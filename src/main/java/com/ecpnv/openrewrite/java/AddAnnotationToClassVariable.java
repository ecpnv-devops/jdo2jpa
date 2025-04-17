package com.ecpnv.openrewrite.java;

import java.util.Comparator;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddAnnotationToClassVariable extends Recipe {

    @Option(displayName = "Variable type match expression",
            description = "A fully-qualified type name, that is used to find matching type references. " +
                    "Supports glob expressions. `java..*` finds every type from every subpackage of the `java` package.",
            example = "com.test.SomeInterface.Helper.*")
    @NonNull
    String variableTypeRegex;

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation.",
            example = "org.junit.Test")
    @NonNull
    String annotationType;

    @Option(displayName = "Annotation template to insert",
            description = "An annotation template to add instead of original one, will be parsed with `JavaTemplate`.",
            example = "@org.jetbrains.annotations.NotNull(\"Null not permitted\")")
    @NonNull
    String annotationTemplateToInsert;


    @JsonCreator
    public AddAnnotationToClassVariable(
            @NonNull @JsonProperty("variableTypeRegex") String variableTypeRegex,
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("annotationTemplateToInsert") String annotationTemplateToInsert) {
        this.variableTypeRegex = variableTypeRegex;
        this.annotationType = annotationType;
        this.annotationTemplateToInsert = annotationTemplateToInsert;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add annotation to variable of type";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Add annotation to variable of type.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final Pattern pattern = Pattern.compile(variableTypeRegex);
        return new JavaIsoVisitor<>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                if (multiVariable.getType() != null && multiVariable.getType().isAssignableFrom(pattern) &&
                        !multiVariable.getVariables().isEmpty() && multiVariable.getVariables().get(0).getInitializer() != null &&
                        !RewriteUtils.hasAnnotation(multiVariable.getLeadingAnnotations(), annotationType, getCursor())) {
                    // Add annotation to variable
                    maybeAddImport(annotationType, null, false);
                    return JavaTemplate.builder(annotationTemplateToInsert)
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(annotationType)
                            .build()
                            .apply(getCursor(), multiVariable.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }
        };
    }
}
