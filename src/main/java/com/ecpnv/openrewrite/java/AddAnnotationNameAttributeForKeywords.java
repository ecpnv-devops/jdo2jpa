package com.ecpnv.openrewrite.java;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that adds a name attribute to a specified annotation in Java source files
 * when the annotated attribute matches a specified list of keywords. This is useful
 * for escaping reserved keywords in JPA frameworks.
 * <p>
 * The recipe checks for the existence of an annotation with the specified name and adds
 * an attribute with the desired name and value if conditions based on keywords are met.
 * <p>
 * Fields:
 * - `annotationType`: The fully qualified name of the annotation to which the attribute is added.
 * - `attributeName`: The name of the attribute to add to the annotation.
 * - `keywords`: A comma-separated list of keywords to check against the annotated attributes.
 * - `escapeString`: A string used to escape the attribute name if it is a reserved keyword.
 * <p>
 * Functional Behavior:
 * - The recipe searches through annotations in the source files and identifies those
 * matching the specified annotation type.
 * - If an annotated variable matches any of the provided keywords, the specified attribute
 * is added to the annotation, with its name and value appropriately escaped.
 * - Existing annotations with the same attribute name but different values are handled
 * gracefully by replacing or appending the correct attribute.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddAnnotationNameAttributeForKeywords extends Recipe {

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation to add.",
            example = "javax.persistence.Column")
    @NonNull
    String annotationType;

    @Option(displayName = "Attribute name",
            description = "The name of attribute added.",
            example = "name")
    @NonNull
    String attributeName;

    @Option(displayName = "Keywords",
            description = "Keywords",
            example = "key, user, ...")
    @NonNull
    String keywords;

    @Option(displayName = "Escape String",
            description = "Escape String for escaping JPA keywords",
            example = "\\\"")
    @NonNull
    String escapeString;

    @JsonCreator
    public AddAnnotationNameAttributeForKeywords(
            @JsonProperty("annotationType") @NonNull String annotationType,
            @JsonProperty("attributeName") @NonNull String attributeName,
            @JsonProperty("keywords") @NonNull String keywords,
            @JsonProperty("escapeString") String escapeString) {
        this.annotationType = annotationType;
        this.attributeName = attributeName;
        this.keywords = keywords;
        this.escapeString = escapeString;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add annotation name attribute for keywords";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Add annotation name attribute when annotated attribute is any of they keywords.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false),
                new JavaIsoVisitor<>() {

                    private Pattern pattern = Pattern.compile(getAnnotationType());
                    private J.VariableDeclarations.NamedVariable namedVariable;

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                        if (namedVariable != null) {
                            final List<Expression> arguments = annotation.getArguments();
                            final String attributeValue = namedVariable.getSimpleName();

                            if (annotation.getType().isAssignableFrom(pattern) &&
                                    checkArgumentsForAttributeName(arguments, getAttributeName(), escaped(attributeValue, getEscapeString()))) {
                                final String template = "@%s(%s = %s)".formatted(getAnnotationType(), getAttributeName(), escaped(attributeValue, getEscapeString()));
                                final J.Annotation dummyAnnotation = JavaTemplate.builder(template)
                                        .build()
                                        .apply(getCursor(), annotation.getCoordinates().replace());
                                final List<Expression> newArguments = createNewArguments(arguments, dummyAnnotation.getArguments().getFirst());
                                return annotation.withArguments(newArguments);
                            }
                        }
                        return super.visitAnnotation(annotation, executionContext);
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        try {
                            final Set<String> keywords = new HashSet<>(Arrays.asList(getKeywords().split(",")));
                            if (multiVariable.getVariables().getFirst() instanceof J.VariableDeclarations.NamedVariable named && keywords.contains(named.getSimpleName())) {
                                namedVariable = multiVariable.getVariables().getFirst();
                            }
                            return super.visitVariableDeclarations(multiVariable, ctx);
                        } finally {
                            namedVariable = null;
                        }
                    }
                });
    }

    private static String escaped(String string, String escapeString) {
        return "\"%s%s%s\"".formatted(escapeString, string, escapeString);
    }

    private static List<Expression> createNewArguments(List<Expression> arguments, Expression expression) {
        if (CollectionUtils.isEmpty(arguments)) {
            return arguments;
        }
        final Iterator<Expression> iterator = arguments.iterator();
        while (iterator.hasNext()) {
            Expression argument = iterator.next();
            if (argument instanceof J.Empty) {
                iterator.remove();
            } else if (argument instanceof J.Assignment assignment1 &&
                    expression instanceof J.Assignment assignment2 &&
                    assignment1.getVariable() instanceof J.Identifier identifier1 &&
                    assignment2.getVariable() instanceof J.Identifier identifier2) {
                if (Objects.equals(identifier1.getSimpleName(), identifier2.getSimpleName())) {
                    iterator.remove();
                }
            }
        }
        arguments.add(expression);
        return arguments;
    }

    private static boolean checkArgumentsForAttributeName(
            List<Expression> arguments,
            @NonNull String attributeName,
            String attributeValue) {
        if (CollectionUtils.isNotEmpty(arguments)) {
            return arguments.stream().anyMatch(argument -> {
                if (argument instanceof J.Assignment assignment &&
                        assignment.getVariable() instanceof J.Identifier identifier &&
                        attributeName.equals(identifier.getSimpleName()) &&
                        assignment.getAssignment() instanceof J.Literal literal &&
                        literal.getValueSource() instanceof String value &&
                        Objects.equals(value, attributeValue)) {
                    return false;
                }
                return true;
            });
        }
        return true;
    }
}
