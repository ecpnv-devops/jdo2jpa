package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * This class defines a migration recipe for replacing occurrences of an entity field not contained in a collection
 * with optionally the <code>@javax.jdo.annotations.Persistent</code> annotation with the equivalent JPA
 * <code>@ManyToOne</code> annotation in Java code.
 * <p>
 * The transformation ensures compatibility with JPA by locating entity fields optionally annotated with
 * <code>@javax.jdo.annotations.Persistent</code>, analyzing the attributes of the annotation (e.g., `dependentElement`),
 * and it replaces the annotation with the corresponding JPA compliant <code>@ManyToOne</code> annotation.
 * <p>
 * The migration adheres to the following rules:
 * <ul>
 * <li> Fields must <b>not</b> be assignable from {@link java.util.Collection}.
 * <li> If a field already has a <code>@ManyToOne</code> annotation, it will be skipped.
 * <li> If the <code>@javax.jdo.annotations.Persistent</code> annotation exists, dependentElement and defaultFetchGroup
 * are also transformed when applicable.
 * <li> Ensures that relevant imports (<code>javax.persistence.ManyToOne</code>) are updated or added when necessary.
 * </ul>
 * <p>
 * The class uses a `JavaIsoVisitor` to traverse the Abstract Syntax Tree (AST) of the Java source code and
 * apply the required transformations to the target variable declarations.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistentWithManyToOneAnnotation extends Recipe {

    public static final String SOURCE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENT_ANNOTATION_FULL;
    public static final String TARGET_TYPE_NAME = Constants.Jpa.MANY_TO_ONE_ANNOTATION_NAME;
    public static final String TARGET_TYPE = Constants.Jpa.MANY_TO_ONE_ANNOTATION_FULL;
    public static final String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;
    public static final String PERSISTENCE_CAPABLE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL;

    @Option(displayName = "Default cascade types to apply",
            description = "When the " + TARGET_ANNOTATION_TYPE +
                    " is applied, then these optional cascade type default is applied.",
            required = false,
            example = "CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH")
    @Nullable String defaultCascade;

    @JsonCreator
    public ReplacePersistentWithManyToOneAnnotation(@NonNull @JsonProperty("defaultCascade") String defaultCascade) {
        this.defaultCascade = defaultCascade;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "When there is an `" + SOURCE_ANNOTATION_TYPE + "` annotation it must be replaced by a " +
                TARGET_ANNOTATION_TYPE + " annotation";
    }

    @Override
    public @NotNull String getDescription() {
        return "When an JDO entity is annotated with `" + SOURCE_ANNOTATION_TYPE + "`, JPA must have a " +
                TARGET_ANNOTATION_TYPE + " annotation.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {
            private Map<J.ClassDeclaration, Boolean> classDeclarationForEntityMap = new HashMap<>();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext executionContext) {
                for (J.ClassDeclaration cd : compilationUnit.getClasses()) {
                    //check which class(es) are annotated with @Entity or @PersistentCapable depending on the order of the class(es)
                    classDeclarationForEntityMap.put(cd,
                            CollectionUtils.isNotEmpty(FindAnnotations.find(cd, Constants.Jpa.ENTITY_ANNOTATION_FULL)) ||
                                    CollectionUtils.isNotEmpty(FindAnnotations.find(cd, Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)));
                }

                return super.visitCompilationUnit(compilationUnit, executionContext);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // Exit if Collection
                if (multiVariable.getType() == null || multiVariable.getType().isAssignableFrom(Pattern.compile("java.util.Collection"))) {
                    return multiVariable;
                }
                // Exit if already has target annotation
                if (!FindAnnotations.find(multiVariable, TARGET_ANNOTATION_TYPE).isEmpty()) {
                    return multiVariable;
                }
                // Exit if var part of method
                if (RewriteUtils.isMethodOwnerOfVar(multiVariable)) {
                    return multiVariable;
                }
                //exit if var not attribute of an entity class
                for (Map.Entry<J.ClassDeclaration, Boolean> entry : classDeclarationForEntityMap.entrySet()) {
                    //relate attribute to any of the declared classes that were check for annotations
                    if (entry.getKey().getBody().getStatements().contains(multiVariable) && Boolean.FALSE.equals(entry.getValue())) {
                        return multiVariable;
                    }
                }

                // Verify that the field refers to an entity
                if (RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)
                        || RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jpa.ENTITY_ANNOTATION_FULL)) {
                    // Entity field found, hence ManyToOne applies
                    StringBuilder template = new StringBuilder("@").append(TARGET_TYPE_NAME).append("(");

                    // Find optional source annotation
                    List<J.Annotation> leadAnnos = new ArrayList<>(multiVariable.getLeadingAnnotations());
                    Optional<J.Annotation> sourceAnnotationIfAny = FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE).stream().findFirst();
                    sourceAnnotationIfAny.ifPresent(annotation -> {
                        // When @Persistence is found replace
                        leadAnnos.remove(annotation);
                        // Search for @Column
                        FindAnnotations.find(multiVariable, Constants.Jdo.COLUMN_ANNOTATION_FULL).stream()
                                .findFirst()
                                .ifPresent(columnAnno ->
                                        // Search for @Column.allowsNull
                                        RewriteUtils.findArgument(columnAnno, Constants.Jdo.COLUMN_ARGUMENT_ALLOWS_NULL)
                                                .ifPresent(allowsNullArg -> {
                                                    // Add optional argument
                                                    template
                                                            .append(" optional = \"")
                                                            .append(allowsNullArg.getAssignment())
                                                            .append("\",");
                                                    // Remove @Column annotation or allowsNull argument
                                                    List<Expression> args = new ArrayList<>(columnAnno.getArguments());
                                                    args.remove(allowsNullArg);
                                                    // Remove @Column when no arguments are left
                                                    leadAnnos.remove(columnAnno);
                                                    if (!args.isEmpty()) {
                                                        // Remove allowsNull argument and keep @Column
                                                        var ca = columnAnno.withArguments(args);
                                                        ca = ca.withMarkers(ca.getMarkers().withMarkers(List.of()));
                                                        leadAnnos.add(ca);
                                                    }
                                                })
                                );

                        // Search for dependentElement
                        RewriteUtils.findBooleanArgument(annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT)
                                .filter(isDependent -> isDependent)
                                .ifPresentOrElse(isDependent -> template
                                                .append(" cascade = {CascadeType.REMOVE")
                                                .append(StringUtils.isBlank(defaultCascade) ? "" : ", " + defaultCascade)
                                                .append("}"),
                                        () -> {
                                            if (!StringUtils.isBlank(defaultCascade))
                                                template
                                                        .append(" cascade = {")
                                                        .append(defaultCascade)
                                                        .append("}");
                                        });

                        // Search for defaultFetchGroup
                        template.append(", fetch = FetchType.");
                        RewriteUtils.findBooleanArgument(annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP)
                                .ifPresentOrElse(isDefault -> {
                                            if (Boolean.TRUE.equals(isDefault))
                                                template.append("EAGER");
                                            else
                                                template.append("LAZY");
                                        }, () -> template.append("LAZY")
                                );
                    });

                    template.append(")");
                    // Add @OneToMany and CascadeType
                    maybeAddImport(TARGET_TYPE);
                    maybeAddImport(Constants.Jpa.CASCADE_TYPE_FULL);
                    maybeAddImport(Constants.Jpa.FETCH_TYPE_FULL);
                    maybeRemoveImport(Constants.Jdo.PERSISTENT_ANNOTATION_FULL);
                    maybeRemoveImport(Constants.Jdo.COLUMN_ANNOTATION_FULL);

                    return maybeAutoFormat(multiVariable, multiVariable.withLeadingAnnotations(ListUtils.concat(leadAnnos,
                                    ((J.VariableDeclarations) JavaTemplate.builder(template.toString())
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.Jpa.CLASS_PATH))
                                            .imports(TARGET_TYPE, Constants.Jpa.CASCADE_TYPE_FULL, Constants.Jpa.FETCH_TYPE_FULL)
                                            .build()
                                            .apply(getCursor(), multiVariable.getCoordinates().replaceAnnotations()))
                                            .getLeadingAnnotations().get(0))),
                            ctx);
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

        };
    }
}
