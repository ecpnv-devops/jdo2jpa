package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeTypesForAnnotatedVariables extends Recipe {

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation to filter on.",
            example = "javax.persistence.OneToMany")
    @NonNull
    String annotationType;

    @Option(displayName = "Old fully-qualified type names",
            description = "Fully-qualified class names of the original type delimited by ','.",
            example = "java.util.SortedSet")
    @NonNull
    String oldFullyQualifiedTypeNames;

    @Option(displayName = "New fully-qualified type names",
            description = "Fully-qualified class names of the replacement type delimited by ',', " +
                    "or the name of a primitive such as \"int\". " +
                    "The `OuterClassName$NestedClassName` naming convention should be used for nested classes.",
            example = "java.util.Set")
    @NonNull
    String newFullyQualifiedTypeNames;

    @Option(displayName = "Ignore type definition",
            description = "When set to `true` the definition of the old type will be left untouched. " +
                    "This is useful when you're replacing usage of a class but don't want to rename it.",
            required = false)
    @Nullable
    Boolean ignoreDefinition;

    List<Pair> typesList = new ArrayList<>();
    List<Pattern> patternList = new ArrayList<>();

    @JsonCreator
    public ChangeTypesForAnnotatedVariables(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("oldFullyQualifiedTypeNames") String oldFullyQualifiedTypeNames,
            @NonNull @JsonProperty("newFullyQualifiedTypeNames") String newFullyQualifiedTypeNames,
            @Nullable @JsonProperty("ignoreDefinition") Boolean ignoreDefinition) {
        this.annotationType = annotationType;
        this.oldFullyQualifiedTypeNames = oldFullyQualifiedTypeNames;
        this.newFullyQualifiedTypeNames = newFullyQualifiedTypeNames;
        this.ignoreDefinition = ignoreDefinition;

        final List<String> oldTypes = Arrays.asList(oldFullyQualifiedTypeNames.split(","));
        final List<String> newTypes = Arrays.asList(newFullyQualifiedTypeNames.split(","));
        if (oldTypes.isEmpty() || (oldTypes.size() != newTypes.size())) {
            throw new IllegalArgumentException("The number of old and new fully qualified type names must be filled and the same.");
        }
        for (int i = 0; i < oldTypes.size(); i++) {
            typesList.add(new Pair(oldTypes.get(i), newTypes.get(i)));
            patternList.add(Pattern.compile(oldTypes.get(i)));
        }
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Change types of variable with annotation";
    }

    @Override
    public @NotNull String getDescription() {
        return "Change given types to other for variables with an annotation.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<>() {

            // used to track the variables that are visited for the annotated class
            private final List<J.VariableDeclarations.NamedVariable> visitedVariableDeclarations = new ArrayList<>();
            private boolean visitingMethodDeclaration = false;

            @Override
            public J.@NotNull VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, ctx);
                if (RewriteUtils.hasAnnotation(multiVariable.getLeadingAnnotations(), annotationType, getCursor())) {
                    visitedVariableDeclarations.add(multiVariable.getVariables().getFirst());
                    for (Pair pair : typesList) {
                        maybeAddImport(pair.newType());
                        variableDeclarations = (J.VariableDeclarations) new ChangeType(pair.oldType(), pair.newType(), ignoreDefinition)
                                .getVisitor()
                                .visit(variableDeclarations, ctx, getCursor().getParentOrThrow());
                    }
                }
                return Objects.requireNonNull(variableDeclarations);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration newMethod = super.visitMethodDeclaration(method, ctx);
                if (method.getMethodType() != null &&
                        method.getMethodType().getReturnType() instanceof JavaType oldReturn &&
                        patternList.stream().anyMatch(oldReturn::isAssignableFrom) &&
                        containsVariable(method.getBody(), visitedVariableDeclarations)) {
                    // cheat to kick of return visiting by calling the super method again
                    visitingMethodDeclaration = true;
                    newMethod = super.visitMethodDeclaration(method, ctx);
                    visitingMethodDeclaration = false;

                    for (Pair pair : typesList) {
                        maybeAddImport(pair.newType());
                        newMethod = (J.MethodDeclaration) new ChangeType(pair.oldType(), pair.newType(), ignoreDefinition)
                                .getVisitor()
                                .visit(newMethod, ctx, getCursor().getParentOrThrow());
                    }
                }
                return Objects.requireNonNull(newMethod);
            }

            private boolean containsVariable(J.Block body, List<J.VariableDeclarations.NamedVariable> visitedVariableDeclarations) {
                if (body != null && !body.getStatements().isEmpty() &&
                        body.getStatements().getFirst() instanceof J.Return aReturn &&
                        aReturn.getExpression() instanceof J.MethodInvocation methodInvocation) {
                    for (Expression expression : methodInvocation.getArguments()) {
                        if (expression instanceof J.Identifier identifier && visitedVariableDeclarations.stream()
                                .anyMatch(variable ->
                                        variable.getSimpleName().equals(identifier.getSimpleName()))) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public J.Return visitReturn(J.Return aReturn, ExecutionContext ctx) {
                if (visitingMethodDeclaration && aReturn.getExpression() instanceof J.MethodInvocation methodInvocation &&
                        methodInvocation.getName() instanceof J.Identifier identifier &&
                        newName(identifier.getSimpleName(), typesList) instanceof String newName && !newName.isEmpty()) {
                    return aReturn.withExpression(methodInvocation.withName(identifier.withSimpleName(newName)));
                }
                return super.visitReturn(aReturn, ctx);
            }

            private String newName(String simpleName, List<Pair> typesList) {
                for (Pair pair : typesList) {
                    // has a side effect to only use the method names which is used here
                    final String oldClassName = JavaType.ShallowClass.build(pair.oldType()).getClassName();
                    final String newClassName = JavaType.ShallowClass.build(pair.newType()).getClassName();
                    if (oldClassName.equals(simpleName)) {
                        return newClassName;
                    }
                }
                return "";
            }
        });
    }

    private record Pair(String oldType, String newType) {
    }
}
