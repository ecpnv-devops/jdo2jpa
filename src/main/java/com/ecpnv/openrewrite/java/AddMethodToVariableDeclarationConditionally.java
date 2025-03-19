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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * This class represents a recipe that conditionally adds a method to a variable declaration
 * in Java source code, based on specific criteria such as annotations matching a regular expression.
 * It automates the modification of source code by providing a customizable method template,
 * with support for replacements variables and optional imports for additional types.
 * <p>
 * The primary functionality of this class is to identify variable declarations annotated
 * with a specified pattern and attach a method next to them if certain conditions are met.
 * <p>
 * Users can define the behavior by providing:
 * - A regular expression to match annotations on variable declarations.
 * - A method template string that can be dynamically populated with contextual information
 * such as variable names, types, and their owning class name.
 * - Optional import types that the generated code may need.
 * <p>
 * Features:
 * - Dynamic generation of methods based on a customizable template.
 * - Automatic checking for the existence of a similar method to prevent duplication.
 * - Context-sensitive source code updates that integrate the method seamlessly into the class structure.
 * - Support for adding necessary imports to maintain code completeness.
 * <p>
 * Key replacement variables for the method template:
 * - `$varName$`: The name of the variable declaration.
 * - `$varNameC$`: The name of the variable declaration, capitalized.
 * - `$varGType$`: The first generic type, or the variable's type if generics are not specified.
 * - `$varGTypeFq$`: The fully qualified name of the first generic type, or the fully qualified name of the variable's type.
 * - `$className$`: The name of the owning class.
 * <p>
 * The visitor pattern is used to traverse and modify the Java syntax tree, ensuring that
 * updates are applied accurately in accordance with the specified rules and templates.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddMethodToVariableDeclarationConditionally extends Recipe {

    @Option(displayName = "Regular expression",
            description = "A regular expression to match an annotation on the variable declaration.",
            example = "@.*Persistent\\(.*mappedBy.*")
    String regularExpression;

    @Option(displayName = "Method template",
            description = "Template of method to add. May use replacement variables: " +
                    "$varName$ = the name of the variable declaration, " +
                    "$varNameC$ = the name of the variable declaration capitalized, " +
                    "$varGType$ = the first generic type, when not available the variable declaration type (simple name), " +
                    "$varGTypeFq$ = the first generic type, when not available the variable declaration type (Fully qualified), " +
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
            @NonNull @JsonProperty("methodTemplateString") String methodTemplateString,
            @Nullable @JsonProperty("maybeImportTypes") String... maybeImportTypes
    ) {
        this.regularExpression = regularExpression;
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
                String varGTypeFq = RewriteUtils
                        .getParameterType(mv, 0, 0)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .orElse(Objects.requireNonNull(mv.getTypeAsFullyQualified()).getFullyQualifiedName());
                String varGType = varGTypeFq.substring(varGTypeFq.lastIndexOf('.') + 1);
                String className = classDecl.getSimpleName();
                String template = replaceWithVariables(methodTemplateString, varName, varNameC, varGType, varGTypeFq, className);

                // Add imports
                final var jTemplate = JavaTemplate.builder(template);
                if (maybeImportTypes != null && maybeImportTypes.length > 0) {
                    jTemplate.imports(maybeImportTypes);
                    Arrays.stream(maybeImportTypes).forEach(this::maybeAddImport);
                }
                // Method does not yet exist, hence add
                J.Block newBody = jTemplate
                        .contextSensitive()
                        .javaParser(JavaParserFactory.create(ctx))
                        .build()
                        .apply(new Cursor(getCursor(), classDecl.getBody()), mv.getCoordinates().after());

                // Skip when method already exists, this can be done more simple, but for some reason the added parameter type may be unknown
                if (newBody != null && newBody.getStatements().stream()
                        .filter(s1 -> s1 instanceof J.MethodDeclaration)
                        .map(s1 -> (J.MethodDeclaration) s1)
                        .noneMatch(s1 -> newBody.getStatements().stream()
                                .filter(s2 -> s2 instanceof J.MethodDeclaration)
                                .map(s2 -> (J.MethodDeclaration) s2)
                                .filter(s2 -> !s1.equals(s2))
                                .filter(s2 -> s1.getSimpleName().equals(s2.getSimpleName()))
                                .filter(s2 -> s1.getParameters().size() == s2.getParameters().size())
                                .anyMatch(s2 -> s1.getParameters().stream()
                                        .filter(p -> p instanceof J.VariableDeclarations)
                                        .map(p -> (J.VariableDeclarations) p)
                                        .map(p -> p.getTypeExpression().toString())
                                        .toList().equals(
                                        s2.getParameters().stream()
                                                .filter(p -> p instanceof J.VariableDeclarations)
                                                .map(p -> (J.VariableDeclarations) p)
                                                .map(p -> p.getTypeExpression().toString())
                                                .toList())))
                ) {
                    classDecl = classDecl.withBody(newBody);
                }
            }
            return classDecl;
        }

        private static String replaceWithVariables(String input, String varName, String varNameC, String varGType, String varGTypeFq, String className) {
            return StringUtils.replaceEach(input,
                    new String[]{"$varName$", "$varNameC$", "$varGType$", "$varGTypeFq$", "$className$"},
                    new String[]{varName, varNameC, varGType, varGTypeFq, className});
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