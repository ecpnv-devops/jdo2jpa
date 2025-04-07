package com.ecpnv.openrewrite.java;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveAnnotationConditionally extends Recipe {

    public enum DeclarationType {
        VAR, CLASS, METHOD
    }

    @Option(displayName = "Regular expression to match",
            description = "Optional, Only declaration types for which annotations that match the regular expression will be processed.",
            required = false,
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match this regular expression will be removed. " +
                    "When matchByRegularExpression is null, this expression will be used to find the declaration to process.",
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpressionForRemoval;

    @Option(displayName = "Declaration of var, class or method",
            description = "Choice of [VAR, CLASS, METHOD] to define for which type of declaration this recipe has to act.",
            example = "VAR")
    DeclarationType declarationType;

    @Option(displayName = "Match type",
            description = "Skip when this type does not match the (inherited) type of the variable, " +
                    "return type of the method or type of the class.",
            required = false,
            example = "java.util.Collection")
    String fullyQualifiedType;

    @JsonCreator
    public RemoveAnnotationConditionally(
            @Nullable @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @NonNull @JsonProperty("matchByRegularExpressionForRemoval") String matchByRegularExpressionForRemoval,
            @NonNull @JsonProperty("declarationType") DeclarationType declarationType,
            @Nullable @JsonProperty("fullyQualifiedType") String fullyQualifiedType) {
        this.matchByRegularExpression = matchByRegularExpression != null ? matchByRegularExpression : matchByRegularExpressionForRemoval;
        this.matchByRegularExpressionForRemoval = matchByRegularExpressionForRemoval;
        this.declarationType = declarationType;
        this.fullyQualifiedType = fullyQualifiedType;
    }

    @Override
    public String getDisplayName() {
        return "Remove annotation conditionally";
    }

    @Override
    public String getDescription() {
        return "Remove annotation when another annotation is found that matches the given regular expression.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            protected final TypeMatcher typeMatcher = new TypeMatcher(fullyQualifiedType, true);

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vars = super.visitVariableDeclarations(multiVariable, ctx);

                return (J.VariableDeclarations) removeAnnotationConditionally(DeclarationType.VAR, vars,
                        vars.getType(), vars.getLeadingAnnotations(), vars::withLeadingAnnotations);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classD = super.visitClassDeclaration(classDecl, ctx);

                return (J.ClassDeclaration) removeAnnotationConditionally(DeclarationType.CLASS, classD,
                        classD.getType(), classD.getLeadingAnnotations(), classD::withLeadingAnnotations);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                return (J.MethodDeclaration) removeAnnotationConditionally(DeclarationType.METHOD, m, m.getType(),
                        m.getLeadingAnnotations(), m::withLeadingAnnotations);
            }

            public J removeAnnotationConditionally(
                    DeclarationType currentDeclarationType, J j, JavaType type, List<J.Annotation> annotations,
                    Function<List<J.Annotation>, J> applyAnnotations) {
                // Match type when applicable
                if (StringUtils.isNotBlank(fullyQualifiedType) && !typeMatcher.matches(type)) {
                    return j;
                }
                // Match declarationType or exit
                if (declarationType != currentDeclarationType) {
                    return j;
                }
                // Does the declaration has a match annotation?
                if (annotations.stream().anyMatch(a -> a.toString().matches(matchByRegularExpression))) {
                    // And does it match the removal expression?
                    final var newAnnos = annotations.stream()
                            .filter(a -> !a.toString().matches(matchByRegularExpressionForRemoval))
                            .toList();
                    if (annotations.size() != newAnnos.size()) {
                        return applyAnnotations.apply(newAnnos);
                    }
                }
                return j;
            }
        };
    }
}
