package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Preserves an explicit JDO scalar exclusion from the default fetch group as JPA lazy-basic metadata.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class PreserveScalarDefaultFetchGroup extends Recipe {

    private static final String BASIC = Constants.Jpa.BASE_PACKAGE + "Basic";
    private static final String BASIC_PATTERN = "@" + BASIC;
    private static final String PERSISTENT_PATTERN = "@" + Constants.Jdo.PERSISTENT_ANNOTATION_FULL;
    private static final Pattern COLLECTION_OR_MAP = Pattern.compile("java\\.util\\.(Collection|Map)");
    private static final List<String> RELATIONSHIP_ANNOTATIONS = List.of(
            Constants.Jpa.MANY_TO_ONE_ANNOTATION_FULL,
            Constants.Jpa.ONE_TO_ONE_ANNOTATION_FULL,
            Constants.Jpa.ONE_TO_MANY_ANNOTATION_FULL,
            Constants.Jpa.BASE_PACKAGE + "ManyToMany",
            Constants.Jpa.BASE_PACKAGE + "ElementCollection");

    @Override
    public @NotNull String getDisplayName() {
        return "Preserve scalar default-fetch-group exclusion";
    }

    @Override
    public @NotNull String getDescription() {
        return "Translate explicit JDO scalar defaultFetchGroup=false metadata to JPA @Basic(fetch = FetchType.LAZY).";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            @Override
            public J.@NotNull VariableDeclarations visitVariableDeclarations(
                    J.VariableDeclarations declarations, ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                if (RewriteUtils.isMethodOwnerOfVar(visited) || !isExcludedScalar(visited, visited.getType())) {
                    return visited;
                }
                return addOrUpdateBasic(visited, ctx);
            }

            @Override
            public J.@NotNull MethodDeclaration visitMethodDeclaration(
                    J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                JavaType returnType = visited.getMethodType() == null
                        ? Optional.ofNullable(visited.getReturnTypeExpression()).map(type -> type.getType()).orElse(null)
                        : visited.getMethodType().getReturnType();
                if (!isExcludedScalar(visited, returnType)) {
                    return visited;
                }
                return addOrUpdateBasic(visited, ctx);
            }

            private boolean isExcludedScalar(J declaration, JavaType type) {
                Optional<J.Annotation> persistent = RewriteUtils.findLeadingAnnotations(
                        declaration, PERSISTENT_PATTERN).stream()
                        .findFirst();
                if (persistent.flatMap(annotation -> RewriteUtils.findArgumentAsBoolean(
                        annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP))
                        .filter(value -> !value)
                        .isEmpty()) {
                    return false;
                }
                if (RELATIONSHIP_ANNOTATIONS.stream()
                        .anyMatch(annotation -> !RewriteUtils.findLeadingAnnotations(declaration, annotation).isEmpty())) {
                    return false;
                }
                if (type != null && type.isAssignableFrom(COLLECTION_OR_MAP)) {
                    return false;
                }
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
                return fullyQualified == null
                        || (!RewriteUtils.hasAnnotation(fullyQualified, Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)
                        && !RewriteUtils.hasAnnotation(fullyQualified, Constants.Jpa.ENTITY_ANNOTATION_FULL));
            }

            private J.VariableDeclarations addOrUpdateBasic(
                    J.VariableDeclarations declarations, ExecutionContext ctx) {
                Optional<J.Annotation> basic = RewriteUtils.findLeadingAnnotations(declarations, BASIC_PATTERN).stream()
                        .findFirst();
                if (basic.filter(this::hasSingleLazyFetch).isPresent()) {
                    return declarations;
                }
                maybeAddImport(BASIC);
                maybeAddImport(Constants.Jpa.FETCH_TYPE_FULL);
                if (basic.isPresent()) {
                    return JavaTemplate.builder(basicTemplate(basic.get()))
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(BASIC, Constants.Jpa.FETCH_TYPE_FULL)
                            .build()
                            .apply(updateCursor(declarations), basic.get().getCoordinates().replace());
                }
                return JavaTemplate.builder("@Basic(fetch = FetchType.LAZY)")
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(BASIC, Constants.Jpa.FETCH_TYPE_FULL)
                        .build()
                        .apply(updateCursor(declarations), declarations.getCoordinates()
                                .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            private J.MethodDeclaration addOrUpdateBasic(J.MethodDeclaration method, ExecutionContext ctx) {
                Optional<J.Annotation> basic = RewriteUtils.findLeadingAnnotations(method, BASIC_PATTERN).stream()
                        .findFirst();
                if (basic.filter(this::hasSingleLazyFetch).isPresent()) {
                    return method;
                }
                maybeAddImport(BASIC);
                maybeAddImport(Constants.Jpa.FETCH_TYPE_FULL);
                if (basic.isPresent()) {
                    return JavaTemplate.builder(basicTemplate(basic.get()))
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(BASIC, Constants.Jpa.FETCH_TYPE_FULL)
                            .build()
                            .apply(updateCursor(method), basic.get().getCoordinates().replace());
                }
                return JavaTemplate.builder("@Basic(fetch = FetchType.LAZY)")
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(BASIC, Constants.Jpa.FETCH_TYPE_FULL)
                        .build()
                        .apply(updateCursor(method), method.getCoordinates()
                                .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            private boolean hasSingleLazyFetch(J.Annotation basic) {
                if (basic.getArguments() == null) {
                    return false;
                }
                List<J.Assignment> fetchArguments = basic.getArguments().stream()
                        .filter(J.Assignment.class::isInstance)
                        .map(J.Assignment.class::cast)
                        .filter(assignment -> assignment.getVariable() instanceof J.Identifier identifier
                                && "fetch".equals(identifier.getSimpleName()))
                        .toList();
                return fetchArguments.size() == 1
                        && fetchArguments.getFirst().getAssignment().toString().endsWith("FetchType.LAZY");
            }

            private String basicTemplate(J.Annotation basic) {
                List<String> arguments = new ArrayList<>();
                boolean fetchAdded = false;
                if (basic.getArguments() != null) {
                    for (Expression argument : basic.getArguments()) {
                        if (argument instanceof J.Empty) {
                            continue;
                        }
                        if (argument instanceof J.Assignment assignment
                                && assignment.getVariable() instanceof J.Identifier identifier
                                && "fetch".equals(identifier.getSimpleName())) {
                            if (!fetchAdded) {
                                arguments.add("fetch = FetchType.LAZY");
                                fetchAdded = true;
                            }
                        } else {
                            arguments.add(argument.toString());
                        }
                    }
                }
                if (!fetchAdded) {
                    arguments.add("fetch = FetchType.LAZY");
                }
                return "@Basic(" + String.join(", ", arguments) + ")";
            }
        };
    }
}
