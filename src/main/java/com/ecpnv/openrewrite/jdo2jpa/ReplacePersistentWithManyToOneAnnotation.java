package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

import static com.ecpnv.openrewrite.util.RewriteUtils.sanitize;

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
public class ReplacePersistentWithManyToOneAnnotation extends ScanningRecipe<Set<String>> {

    public static final String SOURCE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENT_ANNOTATION_FULL;
    public static final String TARGET_TYPE_NAME = Constants.Jpa.MANY_TO_ONE_ANNOTATION_NAME;
    public static final String TARGET_TYPE = Constants.Jpa.MANY_TO_ONE_ANNOTATION_FULL;
    public static final String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;

    @Option(displayName = "Default cascade types to apply",
            description = "When the " + TARGET_ANNOTATION_TYPE +
                    " is applied, then these optional cascade type default is applied.",
            required = false,
            example = "CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH")
    @Nullable
    String defaultCascade;

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
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> entityClasses) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                RewriteUtils.findLeadingAnnotations(cd, Constants.Jpa.ENTITY_ANNOTATION_FULL).stream()
                        .findFirst()
                        .or(() -> RewriteUtils.findLeadingAnnotations(cd, Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL).stream()
                                .findFirst()).ifPresent(annotation -> {
                    if (cd.getType() != null) {
                        entityClasses.add(cd.getType().getFullyQualifiedName());
                    }
                });
                return cd;
            }
        };
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor(Set<String> entityClasses) {

        return Preconditions.check(Preconditions.or(
                        new UsesType<>(Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL, false),
                        new UsesType<>(Constants.Jpa.ENTITY_ANNOTATION_FULL, false)),
                new ReplacePersistentWithManyToOneAnnotationVisitor(entityClasses));
    }

    public class ReplacePersistentWithManyToOneAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final Set<String> entityClasses;

        public ReplacePersistentWithManyToOneAnnotationVisitor(Set<String> entityClasses) {
            this.entityClasses = entityClasses;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariableOrg, ExecutionContext ctx) {
            J.VariableDeclarations multiVariable = super.visitVariableDeclarations(multiVariableOrg, ctx);
            // Exit if owner has no @Entity or @PersistenceCapable annotation
            if (RewriteUtils.ownerOfFirstVarToFullyQualifiedName(multiVariable).stream().noneMatch(entityClasses::contains)) {
                return multiVariable;
            }
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
            // Verify that the field refers to an entity
            if (RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)
                    || RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jpa.ENTITY_ANNOTATION_FULL)) {
                // Entity field found, hence ManyToOne applies
                StringBuilder template = new StringBuilder("@").append(TARGET_TYPE_NAME).append("(");

                // Find optional source annotation (@Persistent)
                List<J.Annotation> leadAnnos = new ArrayList<>(multiVariable.getLeadingAnnotations());
                Optional<J.Annotation> sourceAnnotationIfAny = FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE).stream().findFirst();
                // Search for @Column
                StringBuilder colTemplate = new StringBuilder("@")
                        .append(Constants.Jpa.JOIN_COLUMN_ANNOTATION_NAME)
                        .append("(");
                Optional<J.Annotation> columnAnnoIfAny = FindAnnotations
                        .find(multiVariable, Constants.Jdo.COLUMN_ANNOTATION_FULL).stream().findFirst()
                        .or(() -> FindAnnotations.find(multiVariable, Constants.Jpa.COLUMN_ANNOTATION_FULL).stream().findFirst());
                AtomicBoolean added = new AtomicBoolean(false);
                columnAnnoIfAny.ifPresent(columnAnno -> {
                    List<Expression> args = new ArrayList<>(columnAnno.getArguments());
                    // Search for @Column.allowsNull
                    AtomicBoolean foundNullOrName = new AtomicBoolean(false);
                    RewriteUtils.findArgumentAssignment(columnAnno, Constants.Jdo.COLUMN_ARGUMENT_ALLOWS_NULL)
                            .ifPresent(allowsNullArg -> {
                                // Add optional argument
                                template
                                        .append(" optional = ")
                                        .append(allowsNullArg.getAssignment());
                                colTemplate
                                        .append(" nullable = ")
                                        .append(allowsNullArg.getAssignment());
                                // Remove @Column annotation or allowsNull argument
                                args.remove(allowsNullArg);
                                foundNullOrName.set(true);
                                added.set(true);
                            });
                    RewriteUtils.findArgumentAssignment(columnAnno, Constants.Jdo.ARGUMENT_NAME)
                            .ifPresentOrElse(nameArg -> {
                                if (foundNullOrName.get()) {
                                    colTemplate.append(", ");
                                }
                                colTemplate
                                        .append(" name = \"")
                                        .append(sanitize(nameArg.getAssignment().toString()))
                                        .append("\")");
                                // Remove name argument
                                args.remove(nameArg);
                                foundNullOrName.set(true);
                            }, () -> colTemplate.delete(0, colTemplate.length()));
                    if (foundNullOrName.get()) {
                        // Remove @Column when no arguments are left
                        leadAnnos.remove(columnAnno);
                        if (!args.isEmpty()) {
                            // Remove allowsNull and/or name argument and keep @Column
                            var ca = columnAnno.withArguments(args);
                            ca = ca.withMarkers(ca.getMarkers().withMarkers(List.of()));
                            leadAnnos.add(ca);
                        }
                    }
                });

                sourceAnnotationIfAny.ifPresent(annotation -> {
                    // When @Persistence is found replace
                    leadAnnos.remove(annotation);

                    // Search for dependentElement
                    RewriteUtils.findArgumentAsBoolean(annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT)
                            .filter(isDependent -> isDependent)
                            .ifPresentOrElse(isDependent -> {
                                template
                                        .append(added.get() ? ", " : "")
                                        .append("cascade = {CascadeType.REMOVE")
                                        .append(StringUtils.isBlank(defaultCascade) ? "" : ", " + defaultCascade)
                                        .append("}");
                                added.set(true);
                            }, () -> {
                                if (!StringUtils.isBlank(defaultCascade)) {
                                    template
                                            .append(added.get() ? ", " : "")
                                            .append(" cascade = {")
                                            .append(defaultCascade)
                                            .append("}");
                                    added.set(true);
                                }
                            });

                    // Search for defaultFetchGroup
                    template
                            .append(added.get() ? ", " : "")
                            .append("fetch = FetchType.");
                    RewriteUtils.findArgumentAsBoolean(annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP)
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
                maybeAddImport(Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.PERSISTENT_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.COLUMN_ANNOTATION_FULL);

                var leadAnnosResult = ListUtils.concat(leadAnnos, ((J.VariableDeclarations) JavaTemplate.builder(template.toString())
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(TARGET_TYPE, Constants.Jpa.CASCADE_TYPE_FULL, Constants.Jpa.FETCH_TYPE_FULL)
                        .build()
                        .apply(getCursor(), multiVariable.getCoordinates().replaceAnnotations()))
                        .getLeadingAnnotations().get(0));

                // Only add @JoinColumn when the relation is UNIdirectional and NOT bidirectional
                if (!colTemplate.isEmpty() && !RewriteUtils.hasCollectionMemberOfSameTypeAsOwner(multiVariable)) {
                    leadAnnosResult = ListUtils.concat(leadAnnosResult, columnAnnoIfAny.map(colAnno ->
                            ((J.VariableDeclarations) JavaTemplate.builder(colTemplate.toString())
                                    .javaParser(JavaParserFactory.create(ctx))
                                    .imports(TARGET_TYPE, Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL)
                                    .build()
                                    .apply(getCursor(), multiVariable.getCoordinates().replaceAnnotations()))
                                    .getLeadingAnnotations().get(0))
                            .orElse(null));
                }

                return maybeAutoFormat(multiVariableOrg, multiVariable.withLeadingAnnotations(leadAnnosResult), ctx);
            }
            return multiVariable;
        }
    }
}
