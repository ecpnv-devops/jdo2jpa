package com.ecpnv.openrewrite.java;

import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * This class represents a recipe that conditionally removes an attribute from an annotation in source code.
 * It can be used to modify the annotations systematically, ensuring specific attributes are removed when
 * certain conditions are met.
 * <p>
 * Behavior:
 * - Matches annotations using the provided fully qualified annotation type.
 * - Filters annotations based on a regular expression to determine if they should be modified.
 * - Removes the specified attribute from the annotation while maintaining proper formatting.
 * <p>
 * Note that this basically a copy of the original <code>org.openrewrite.java.RemoveAnnotationAttribute</code>
 * with the additional feature to filter the applicable annotations using a regular expression.
 *
 * @author Open Rewrite
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveAnnotationAttributeConditionally extends Recipe {

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be changed.",
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    @NonNull
    String matchByRegularExpression;

    @Option(displayName = "Annotation Type",
            description = "The fully qualified name of the annotation.",
            example = "org.junit.Test")
    @NonNull
    String annotationType;

    @Option(displayName = "Attribute name",
            description = "The name of attribute to remove.",
            example = "timeout")
    @NonNull
    String attributeName;

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
        return "Some annotations accept arguments. This recipe removes an existing attribute.";
    }

    @JsonCreator
    public RemoveAnnotationAttributeConditionally(
            @NonNull @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("attributeName") String attributeName) {
        this.matchByRegularExpression = matchByRegularExpression;
        this.annotationType = annotationType;
        this.attributeName = attributeName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher annotationMatcher = new AnnotationMatcher(annotationType);

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!annotationMatcher.matches(a) || !a.toString().matches(matchByRegularExpression)) {
                    return a;
                }

                AtomicBoolean didPassFirstAttribute = new AtomicBoolean(false);
                AtomicBoolean shouldTrimNextPrefix = new AtomicBoolean(false);
                return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    try {
                        if (arg instanceof J.Assignment assignment) {
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            if (attributeName.equals(variable.getSimpleName())) {
                                if (!didPassFirstAttribute.get()) {
                                    shouldTrimNextPrefix.set(true);
                                }
                                return null;
                            }
                        } else if (attributeName.equals("value")) {
                            if (!didPassFirstAttribute.get()) {
                                shouldTrimNextPrefix.set(true);
                            }
                            return null;
                        }

                        if (shouldTrimNextPrefix.get()) {
                            shouldTrimNextPrefix.set(false);
                            return arg.withPrefix(arg.getPrefix().withWhitespace(""));
                        }
                    } finally {
                        didPassFirstAttribute.set(true);
                    }

                    return arg;
                }));
            }
        });
    }
}
