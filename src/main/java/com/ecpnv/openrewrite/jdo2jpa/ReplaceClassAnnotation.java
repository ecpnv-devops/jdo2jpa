package com.ecpnv.openrewrite.jdo2jpa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindMissingTypes;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

/**
 * This class represents a recipe to replace a specific annotation with another in Java source code.
 * <p>
 * This version is derived from {@link org.openrewrite.java.ReplaceAnnotation} and changed to work for a specific use-case.
 * <p>
 * Field Details:
 * - `annotationPatternToReplace`: Specifies the annotation pattern to match for replacement.
 * - `annotationTemplateToInsert`: Defines the replacement annotation template, processed using JavaTemplate.
 * - `fullClassName`: The fully qualified name of the class associated with the replacement annotation to ensure appropriate import handling.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceClassAnnotation extends Recipe {

    @Option(displayName = "Annotation to replace",
            description = "An annotation matcher, expressed as a method pattern to replace.",
            example = "@org.jetbrains.annotations.NotNull(\"Test\")")
    String annotationPatternToReplace;

    @Option(displayName = "Annotation template to insert",
            description = "An annotation template to add instead of original one, will be parsed with `JavaTemplate`.",
            example = "@org.jetbrains.annotations.NotNull(\"Null not permitted\")")
    String annotationTemplateToInsert;

    @Option(displayName = "Full extending class name",
            description = "The fully qualified name of the extends class.",
            example = "a.SomeClass")
    @NonNull
    String fullClassName;

    @JsonCreator
    public ReplaceClassAnnotation(
            @JsonProperty("annotationPatternToReplace") String annotationPatternToReplace,
            @JsonProperty("annotationTemplateToInsert") String annotationTemplateToInsert,
            @JsonProperty("fullClassName") @NonNull String fullClassName) {
        this.annotationPatternToReplace = annotationPatternToReplace;
        this.annotationTemplateToInsert = annotationTemplateToInsert;
        this.fullClassName = fullClassName;
    }

    @Override
    public String getDisplayName() {
        return "Replace annotation";
    }

    @Override
    public String getDescription() {
        return "Replace an Annotation with another one if the annotation pattern matches. " +
                "Only fixed parameters can be set in the replacement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            /**
             * This adds a printout of which types are missing in the LST.
             *
             * Don't use in production
             */
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                if (false) {
                    doAfterVisit(new FindMissingTypes().getVisitor());
                }
                return super.visitVariableDeclarations(multiVariable, executionContext);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                J.Annotation source = super.visitAnnotation(annotation, executionContext);
                if (annotation.print(getCursor()).contains(annotationPatternToReplace)) {
                    final JavaTemplate template = JavaTemplate.builder(annotationTemplateToInsert).build();
                    maybeAddImport(fullClassName, null, false);
                    source = template.apply(getCursor(), source.getCoordinates().replace());
                    final List<Expression> arguments = source.getArguments();
                    arguments.replaceAll(expression -> {
                        if (expression instanceof J.FieldAccess fieldAccess) {
                            return fieldAccess.withTarget(fieldAccess.getTarget().withType(JavaType.ShallowClass.build(fullClassName)));
                        }
                        throw new RuntimeException("Invalid expression type: " + expression.getClass().getSimpleName() + " in annotation: " + annotation.print(getCursor()));
                    });
                    return source.withType(annotation.getType()).withArguments(arguments);
                }
                return source;
            }
        };
    }
}
