package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.java.search.FindClassesVistor;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Represents a recipe for changing specific types to other types within classes that match a given fully-qualified type name.
 *
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeTypeForClass extends Recipe {

    @Option(displayName = "Fully-qualified type name",
            description = "A fully-qualified type name, that is used to find matching type references. " +
                    "Supports glob expressions. `java..*` finds every type from every subpackage of the `java` package.",
            example = "java.util.List")
    @NonNull
    String fullyQualifiedTypeName;

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
    public ChangeTypeForClass(@NonNull @JsonProperty("fullyQualifiedTypeName") String fullyQualifiedTypeName,
                              @NonNull @JsonProperty("oldFullyQualifiedTypeNames") String oldFullyQualifiedTypeNames,
                              @NonNull @JsonProperty("newFullyQualifiedTypeNames") String newFullyQualifiedTypeNames,
                              @Nullable @JsonProperty("ignoreDefinition") Boolean ignoreDefinition) {
        this.fullyQualifiedTypeName = fullyQualifiedTypeName;
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
        return "Change types for class";
    }

    @Override
    public @NotNull String getDescription() {
        return "Change given types to other types for classes with given fully qualified type name.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindClassesVistor(fullyQualifiedTypeName), new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration newClassDecl = super.visitClassDeclaration(classDecl, executionContext);
                for (Pair pair : typesList) {
                    maybeAddImport(pair.newType());
                    newClassDecl = (J.ClassDeclaration) new ChangeType(pair.oldType(), pair.newType(), ignoreDefinition)
                            .getVisitor()
                            .visit(classDecl, executionContext, getCursor().getParentOrThrow());
                }
                doAfterVisit(new RemoveUnusedImports().getVisitor());
                return newClassDecl;
            }
        });
    }

    private record Pair(String oldType, String newType) {
    }
}
