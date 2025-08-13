package com.ecpnv.openrewrite.java;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Space.EMPTY;
import static org.openrewrite.java.tree.Space.SINGLE_SPACE;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
public class ChangeAnnotationAttributeNameConditionally extends Recipe {

    @Option(displayName = "Annotation Type",
            description = "The fully qualified name of the annotation.",
            example = "org.junit.Test")
    String annotationType;

    @Option(displayName = "Old attribute name",
            description = "The name of attribute to change.",
            example = "timeout")
    String oldAttributeName;

    @Option(displayName = "New attribute name",
            description = "The new attribute name to use.",
            example = "waitFor")
    String newAttributeName;

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be changed.",
            required = false,
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @JsonCreator
    public ChangeAnnotationAttributeNameConditionally(
            @JsonProperty("annotationType") String annotationType,
            @JsonProperty("oldAttributeName") String oldAttributeName,
            @JsonProperty("newAttributeName") String newAttributeName,
            @JsonProperty("matchByRegularExpression") String matchByRegularExpression) {
        this.annotationType = annotationType;
        this.oldAttributeName = oldAttributeName;
        this.newAttributeName = newAttributeName;
        this.matchByRegularExpression = matchByRegularExpression;
    }

    @Override
    public String getDisplayName() {
        return "Change annotation attribute name conditionally";
    }

    @Override
    public String getInstanceNameSuffix() {
        String shortType = annotationType.substring(annotationType.lastIndexOf('.') + 1);
        return String.format("`@%s(%s)` to `@%s(%s)`",
                shortType, oldAttributeName,
                shortType, newAttributeName);
    }

    @Override
    public String getDescription() {
        return "Some annotations accept arguments. This recipe renames an existing attribute.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher annotationMatcher = new AnnotationMatcher('@' + annotationType);

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!annotationMatcher.matches(a)
                        || (matchByRegularExpression != null && !annotation.toString().matches(matchByRegularExpression))) {
                    return a;
                }
                return a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                    if (arg instanceof J.Assignment) {
                        if (!oldAttributeName.equals(newAttributeName)) {
                            J.Assignment assignment = (J.Assignment) arg;
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            if (oldAttributeName.equals(variable.getSimpleName())) {
                                return assignment.withVariable(variable.withSimpleName(newAttributeName));
                            }
                        }
                    } else if (oldAttributeName.equals("value")) {
                        J.Identifier name = new J.Identifier(randomId(), arg.getPrefix(), Markers.EMPTY, emptyList(), newAttributeName, arg.getType(), null);
                        return new J.Assignment(randomId(), EMPTY, arg.getMarkers(), name, new JLeftPadded<>(SINGLE_SPACE, arg.withPrefix(SINGLE_SPACE), Markers.EMPTY), arg.getType());
                    }
                    return arg;
                }));
            }
        });
    }
}

