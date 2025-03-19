package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddMethodToVariableDeclarationConditionally extends Recipe {

    @Option(displayName = "Regular expression",
            description = "A regular expression to match an annotation on the variable declaration.",
            example = "@.*Persistent\\(.*mappedBy.*")
    String regularExpression;

    @Option(displayName = "Method pattern",
            description = "A method (AspectJ) pattern to verify that the method definition does not exist yet. " +
                    "Accepts the same replacement variables as methodTemplateString.",
            example = "*..* addTo$varNameC$($varGType$)")
    String methodPattern;

    @Option(displayName = "Method template",
            description = "Template of method to add. May use replacement variables: " +
                    "$varName$ = the name of the variable declaration, " +
                    "$varNameC$ = the name of the variable declaration capitalized, " +
                    "$varGType$ = the first generic type, when not available the variable declaration type, " +
                    "$className$ = the name of the owner class",
            example = "@Programmatic public void addTo$varNameC$($varGType$ element) { $varName$.add(element); element.set$className$(this); }")
    String methodTemplateString;

    @Option(displayName = "Maybe import types",
            description = "When the template introduces new types, specify them in this list",
            required = false,
            example = "org.apache.isis.applib.annotation.Programmatic")
    String[] maybeImportTypes;

    @JsonCreator
    public AddMethodToVariableDeclarationConditionally(
            @NonNull @JsonProperty("regularExpression") String regularExpression,
            @NonNull @JsonProperty("methodPattern") String methodPattern,
            @NonNull @JsonProperty("methodTemplateString") String methodTemplateString,
            @Nullable @JsonProperty("maybeImportTypes") String... maybeImportTypes
    ) {
        this.regularExpression = regularExpression;
        this.methodPattern = methodPattern;
        this.methodTemplateString = methodTemplateString;
        this.maybeImportTypes = maybeImportTypes;
    }

    @Override
    public String getDisplayName() {
        return "Adds method to variable declaration";
    }

    @Override
    public String getDescription() {
        return "Adds method after variable declaration if it has an annotation that matches the regular expression.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddMethodToVariableDeclarationConditionallyVisitor();
    }

    public class AddMethodToVariableDeclarationConditionallyVisitor extends JavaIsoVisitor<ExecutionContext> {

        List<J.VariableDeclarations> mvMatches;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cs, ExecutionContext ctx) {
            mvMatches = new ArrayList<>();

            // need to make sure we handle inner classes
            J.ClassDeclaration classDecl = super.visitClassDeclaration(cs, ctx);

            for (final J.VariableDeclarations mv : mvMatches) {
                // Create the template using the variables
                String varName = mv.getVariables().getFirst().getSimpleName();
                String varNameC = WordUtils.capitalize(varName);
                String varGType = RewriteUtils
                        .getParameterType(mv, 0, 0)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .orElse(Objects.requireNonNull(mv.getTypeAsFullyQualified()).getFullyQualifiedName());
                String className = classDecl.getSimpleName();
                String template = replaceWithVariables(methodTemplateString, varName, varNameC, varGType, className);

                // Create the matcher using the variables
                final var methodMatcher = new MethodMatcher(
                        replaceWithVariables(methodPattern, varName, varNameC, varGType, className), true);

                // Skip variable for which the method already exists
                final var classDeclFinal = classDecl;
                if (classDecl.getBody().getStatements().stream()
                        .filter(statement -> statement instanceof J.MethodDeclaration)
                        .map(J.MethodDeclaration.class::cast)
                        .noneMatch(methodDeclaration -> methodMatcher.matches(methodDeclaration, classDeclFinal))) {

                    // Add imports
                    final var jTemplate = JavaTemplate.builder(template);
                    if (maybeImportTypes != null && maybeImportTypes.length > 0) {
                        jTemplate.imports(maybeImportTypes);
                        Arrays.stream(maybeImportTypes).forEach(this::maybeAddImport);
                    }
                    // Method does not yet exist, hence add
                    classDecl = classDecl.withBody(jTemplate.build()
                            .apply(new Cursor(getCursor(), classDecl.getBody()),
                                    mv.getCoordinates().after()));
                }
            }
            return classDecl;
        }

        private static String replaceWithVariables(String input, String varName, String varNameC, String varGType, String className) {
            return StringUtils.replaceEach(input,
                    new String[]{"$varName$", "$varNameC$", "$varGType$", "$className$"},
                    new String[]{varName, varNameC, varGType, className});
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
            if (mv.getLeadingAnnotations().stream().anyMatch(a -> a.toString().matches(regularExpression))) {
                mvMatches.add(mv);
            }
            return mv;
        }
    }
}