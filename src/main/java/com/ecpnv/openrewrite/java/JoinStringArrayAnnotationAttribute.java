package com.ecpnv.openrewrite.java;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class JoinStringArrayAnnotationAttribute extends Recipe {

    @Option(displayName = "Annotation Type",
            description = "The fully qualified name of the annotation.",
            example = "org.junit.Test")
    @NonNull
    String annotationType;

    @Option(displayName = "Attribute name",
            description = "The name of attribute for which a String array is expected.",
            example = "timeout")
    @NonNull
    String attributeName;

    @Option(displayName = "Delimiter",
            description = "The delimiter to use between the elements.",
            example = "timeout")
    @NonNull
    String delimiter;

    @Override
    public String getDisplayName() {
        return "Remove annotation attribute";
    }

    @Override
    public String getInstanceNameSuffix() {
        String shortType = annotationType.substring(annotationType.lastIndexOf('.') + 1);
        return String.format("`@%s(%s)`", shortType, attributeName);
    }

    @Override
    public String getDescription() {
        return "Some annotations accept attributes. This recipe joins the strings in an array of the existing attribute.";
    }

    @JsonCreator
    public JoinStringArrayAnnotationAttribute(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("attributeName") String attributeName,
            @NonNull @JsonProperty("delimiter") String delimiter) {
        this.annotationType = annotationType;
        this.attributeName = attributeName;
        this.delimiter = delimiter;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<ExecutionContext>() {

            private final AnnotationMatcher annotationMatcher = new AnnotationMatcher(annotationType);

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!annotationMatcher.matches(a) || a.getArguments() == null || a.getArguments().isEmpty()) {
                    return a;
                }

                return a.getArguments().stream()
                        .map(arg -> {
                            if (arg instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) arg;
                                J.Identifier variable = (J.Identifier) assignment.getVariable();
                                if (attributeName.equals(variable.getSimpleName())) {
                                    return join(assignment.getAssignment());
                                }
                            } else if (attributeName.equals("value") && arg instanceof J.NewArray newArray) {
                                return join(newArray);
                            }

                            return null;
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(a);
            }

            protected J.Annotation join(J javaObject) {
                if (javaObject instanceof J.NewArray newArray) {
                    List<Expression> expressions = newArray.getInitializer();
                    if (expressions != null && !expressions.isEmpty() && expressions.get(0).getType() != null
                            && TypeUtils.isString(expressions.get(0).getType())) {
                        String joinedList = expressions.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(delimiter));
                        joinedList = '"' + joinedList + '"';
                        return JavaTemplate.builder("#{}")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), newArray.getCoordinates().replace(), joinedList);
                    }
                }
                return null;
            }
        });
    }
}
