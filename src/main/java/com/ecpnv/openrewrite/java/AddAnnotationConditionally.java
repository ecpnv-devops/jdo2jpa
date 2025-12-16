package com.ecpnv.openrewrite.java;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.TypedTree;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that conditionally adds an annotation to variables in the source code based on the presence
 * of another annotation that matches a specified regular expression.
 * <p>
 * The purpose of this class is to analyze annotations in the source code and, if an annotation matching
 * the specified regular expression is found, a new annotation will be added as per the provided template.
 * This can be used to enforce annotation conventions or modify source code annotations systematically.
 * <p>
 * Key behavior:
 * - Matches existing annotations using the provided regular expression.
 * - Adds the new annotation only if it does not already exist.
 * - Ensures the required import is included for the new annotation type.
 * <p>
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddAnnotationConditionally extends Recipe {

    public enum DeclarationType {
        VAR, CLASS, METHOD
    }

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be changed.",
            required = false,
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation to add.",
            example = "javax.persistence.Lob")
    String annotationType;

    @Option(displayName = "Attribute template to add",
            description = "The template of the annotation to add.",
            example = "@Lob")
    String annotationTemplate;

    @Option(displayName = "Declaration of var, class or method",
            description = "Choice of [VAR, CLASS, METHOD] to define for which type of declaration this recipe has to act.",
            example = "VAR")
    DeclarationType declarationType;

    @Option(displayName = "Which modifier types are not allowed",
            description = "When specified, these modifier types may not exist on the declaration.",
            required = false,
            example = "Abstract")
    J.Modifier.Type disallowedModifierType;

    @Option(displayName = "Add the annotation also to inherited classes",
            description = "When true (default), the annotation will also added to child classes. " +
                    "Only applicable to class declarations.",
            required = false,
            example = "false")
    Boolean allowInherited;

    @Option(displayName = "Kind of class to process",
            description = "When given should equals var kind, method return kind or class declaration kind.",
            required = false,
            example = "Enum")
    J.ClassDeclaration.Kind.Type kindOfClassToProcess;

    @JsonCreator
    public AddAnnotationConditionally(
            @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("annotationTemplate") String annotationTemplate,
            @NonNull @JsonProperty("declarationType") DeclarationType declarationType,
            @JsonProperty("disallowedModifierType") J.Modifier.Type disallowedModifierType,
            @JsonProperty("allowInherited") Boolean allowInherited,
            @JsonProperty("kindOfClassToProcess") J.ClassDeclaration.Kind.Type kindOfClassToProcess) {
        this.matchByRegularExpression = matchByRegularExpression;
        this.annotationType = annotationType;
        this.annotationTemplate = annotationTemplate;
        this.declarationType = declarationType;
        this.disallowedModifierType = disallowedModifierType;
        this.allowInherited = allowInherited == null || allowInherited;
        this.kindOfClassToProcess = kindOfClassToProcess;
    }

    @Override
    public String getDisplayName() {
        return "Add annotation conditionally";
    }

    @Override
    public String getDescription() {
        return "Add annotation when another annotation is found that matches the given regular expression.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vars = super.visitVariableDeclarations(multiVariable, ctx);
                // Match on modifiers
                if (declarationType != DeclarationType.VAR || isDisallowedModifierTypes(vars.getModifiers())) {
                    return vars;
                }
                // Match on kind
                if (!isKindAllowed(() -> vars.getTypeAsFullyQualified() != null ?
                        vars.getTypeAsFullyQualified().getKind().name() : "")) {
                    return vars;
                }
                // Only allow changes in fields and not in local variables
                if (RewriteUtils.isMethodOwnerOfVar(vars)) {
                    return vars;
                }
                // Add annotation
                return (J.VariableDeclarations) addAnnotationConditionally(vars, vars.getLeadingAnnotations(), ctx,
                        () -> vars.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classD = super.visitClassDeclaration(classDecl, ctx);
                // Match on modifiers
                if (declarationType != DeclarationType.CLASS
                        || isDisallowedModifierTypes(classD.getModifiers())
                        || !isInheritedAllowed(classD)) {
                    return classD;
                }
                // Match on kind
                if (!isKindAllowed(() -> classD.getKind().name())) {
                    return classD;
                }

                Pattern pattern = Pattern.compile(annotationType);
                if (!classD.getLeadingAnnotations().isEmpty() && classD.getLeadingAnnotations().stream()
                        .anyMatch(a -> Optional.ofNullable(a)
                                .map(J.Annotation::getAnnotationType)
                                .map(TypedTree::getType)
                                .map(type -> type.isAssignableFrom(pattern))
                                .orElse(Boolean.FALSE))) {
                    // Do nothing when the annotation already is present
                    return classD;
                }
                // Add annotation
                return (J.ClassDeclaration) addAnnotationConditionally(classD.getLeadingAnnotations(), ctx,
                        () -> classD.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)))
                        .orElse(classD);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                // Match on modifiers
                if (declarationType != DeclarationType.METHOD || isDisallowedModifierTypes(m.getModifiers())) {
                    return m;
                }
                // Match on kind
                if (!isKindAllowed(() -> m.getMethodType() != null && m.getMethodType().getReturnType()
                        instanceof JavaType.Class jtc ? jtc.getKind().name() : "")) {
                    return m;
                }
                // Add annotation
                return (J.MethodDeclaration) addAnnotationConditionally(m, m.getLeadingAnnotations(), ctx,
                        () -> m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            protected boolean isKindAllowed(Supplier<String> kindNameSupplier) {
                String kindName = kindNameSupplier.get();
                return kindOfClassToProcess == null || kindOfClassToProcess.name().equals(kindName);
            }

            protected boolean isDisallowedModifierTypes(List<J.Modifier> modifiers) {
                if (disallowedModifierType == null || modifiers == null) {
                    return false;
                }
                return modifiers.stream()
                        .anyMatch(m -> m.getType() == disallowedModifierType);
            }

            protected boolean isInheritedAllowed(J.ClassDeclaration classD) {
                var pc = classD.getExtends();
                // Is not extended and inherited allowed
                if (pc == null || allowInherited) {
                    return true;
                }
                // Parent is Object
                var pt = pc.getType();
                if (pt == null || TypeUtils.isObject(pt)) {
                    return true;
                }
                // Parent has different optional modifier
                Flag flag = null;
                try {
                    flag = disallowedModifierType == null ? null : Flag.valueOf(disallowedModifierType.name());
                } catch (IllegalArgumentException e) {
                    // do nothing
                }
                var cls = TypeUtils.asClass(pt);
                if (flag == null || (cls != null && cls.hasFlags(flag))) {
                    return true;
                }
                return false;
            }

            public Statement addAnnotationConditionally(Statement j, List<J.Annotation> annotations, ExecutionContext ctx,
                                                        Supplier<JavaCoordinates> coordinates) {
                if (!FindAnnotations.find(j, annotationType).isEmpty()) {
                    // Do nothing when the annotation already is present
                    return j;
                }
                return addAnnotationConditionally(annotations, ctx, coordinates).orElse(j);
            }

            /**
             * Conditionally adds an annotation to the given element based on the provided list of annotations
             * and a set of conditions.
             *
             * @param annotations A list of {@code J.Annotation} objects used to determine if the annotation
             *                     should be added.
             * @param ctx The {@code ExecutionContext} which provides the execution environment for processing.
             * @param coordinates A supplier of {@code JavaCoordinates} that specifies where the annotation
             *                     should be added within the Java source code.
             * @return An {@code Optional<Statement>} representing the added annotation if the conditions are met,
             *         or an empty {@code Optional} if no annotation was added.
             */
            public Optional<Statement> addAnnotationConditionally(List<J.Annotation> annotations, ExecutionContext ctx,
                                                                  Supplier<JavaCoordinates> coordinates) {
                var matchRegEx = StringUtils.isNotBlank(matchByRegularExpression);
                var annotation = annotations.stream()
                        .filter(a -> !matchRegEx
                                || a.toString().matches(matchByRegularExpression))
                        .findFirst();
                if (annotation.isPresent() || (!matchRegEx && kindOfClassToProcess != null)) {
                    // Add annotation to variable
                    maybeAddImport(annotationType, null, false);
                    return Optional.of(
                            (Statement) JavaTemplate.builder(annotationTemplate)
                                    .javaParser(JavaParserFactory.create(ctx))
                                    .imports(annotationType)
                                    .build()
                                    .apply(getCursor(), coordinates.get()));
                }
                return Optional.empty();
            }
        };
    }
}
