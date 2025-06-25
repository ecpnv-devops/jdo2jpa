package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import static com.ecpnv.openrewrite.util.RewriteUtils.sanitizeTableName;

import lombok.Data;
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
public class ReplacePersistentWithManyToOneAnnotation extends ScanningRecipe<ReplacePersistentWithManyToOneAnnotation.Accumulator> {

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
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>(Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL, false),
                        new UsesType<>(Constants.Jpa.ENTITY_ANNOTATION_FULL, false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        RewriteUtils.findLeadingAnnotations(cd, Constants.Jpa.ENTITY_ANNOTATION_FULL).stream()
                                .findFirst()
                                .or(() -> RewriteUtils.findLeadingAnnotations(cd, Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL).stream()
                                        .findFirst()).ifPresent(annotation -> {
                            if (cd.getType() != null) {
                                acc.entityClasses.add(cd.getType().getFullyQualifiedName());
                            }
                        });
                        return cd;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, executionContext);
                        // Check if Collection or already has target annotation or no source annotation is found
                        Optional<J.Annotation> persistentAnno = FindAnnotations.find(mv, SOURCE_ANNOTATION_TYPE).stream().findFirst();
                        if (persistentAnno.isPresent()
                                && FindAnnotations.find(multiVariable, TARGET_ANNOTATION_TYPE).isEmpty()
                                && FindAnnotations.find(multiVariable, Constants.Jpa.ONE_TO_ONE_ANNOTATION_FULL).isEmpty()
                                // We are only searching for mappedby where the type is NOT a collection
                                && multiVariable.getType() != null && !multiVariable.getType().isAssignableFrom(Pattern.compile(Collection.class.getName()))) {
                            // Find mappedby argument
                            RewriteUtils.findArgumentAssignment(persistentAnno.get(), Constants.Jpa.ONE_TO_MANY_ARGUMENT_MAPPED_BY)
                                    .ifPresent(assignment -> {
                                        // Find type
                                        Optional.ofNullable(TypeUtils.asFullyQualified((multiVariable.getType())))
                                                .map(JavaType.FullyQualified::getFullyQualifiedName)
                                                // Is it an entity with a mappedBy definition?
                                                .ifPresent(name -> acc.varPersistentWithMappedBy
                                                        // Then add fqn#varname,column-name-value to accumulator
                                                        .put(name, assignment.getAssignment().toString()));
                                    });
                        }
                        return mv;
                    }
                });
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {

        return Preconditions.check(Preconditions.or(
                        new UsesType<>(Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL, false),
                        new UsesType<>(Constants.Jpa.ENTITY_ANNOTATION_FULL, false)),
                new ReplacePersistentWithManyToOneAnnotationVisitor(acc));
    }

    public class ReplacePersistentWithManyToOneAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        Accumulator acc;

        public ReplacePersistentWithManyToOneAnnotationVisitor(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariableOrg, ExecutionContext ctx) {
            J.VariableDeclarations multiVariable = super.visitVariableDeclarations(multiVariableOrg, ctx);
            // Exit if owner has no @Entity or @PersistenceCapable annotation
            if (RewriteUtils.ownerOfFirstVarToFullyQualifiedName(multiVariable).stream().noneMatch(acc.entityClasses::contains)) {
                return multiVariable;
            }
            // Exit if Collection
            if (multiVariable.getType() == null || multiVariable.getType().isAssignableFrom(Pattern.compile(Collection.class.getName()))) {
                return multiVariable;
            }
            // Exit if already has target annotation
            if (!RewriteUtils.findLeadingAnnotations(multiVariable, TARGET_ANNOTATION_TYPE).isEmpty()
                    || !RewriteUtils.findLeadingAnnotations(multiVariable, Constants.Jpa.ONE_TO_ONE_ANNOTATION_FULL).isEmpty()) {
                return multiVariable;
            }
            // Exit if var part of method
            if (RewriteUtils.isMethodOwnerOfVar(multiVariable)) {
                return multiVariable;
            }
            // Exit if an annotation with mappedBy exists
            if (RewriteUtils.findArgumentAssignment(
                    FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE),
                    Constants.Jpa.ONE_TO_MANY_ARGUMENT_MAPPED_BY).isPresent()) {
                return multiVariable;
            }
            // Verify that the field refers to an entity
            if (RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)
                    || RewriteUtils.hasAnnotation(multiVariable.getTypeAsFullyQualified(), Constants.Jpa.ENTITY_ANNOTATION_FULL)) {
                // Entity field found, hence OneToOne or ManyToOne applies
                StringBuilder template = new StringBuilder("@");
                var fieldName = multiVariable.getVariables().stream()
                        .findFirst()
                        .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                        .orElse(null);
                if (Optional.ofNullable(RewriteUtils.findParentClass(getCursor()))
                        .map(J.ClassDeclaration::getType)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .map(fqn -> acc.varPersistentWithMappedBy.get(fqn))
                        .map(mappedByName -> mappedByName.equals(fieldName))
                        .orElse(false)) {
                    // It is a bi-directional relationship using a @OneToOne relationship
                    template.append(Constants.Jpa.ONE_TO_ONE_ANNOTATION_NAME).append("(");
                } else {
                    // Using the ManyToOne
                    template.append(TARGET_TYPE_NAME).append("(");
                }


                List<J.Annotation> leadAnnos = new ArrayList<>(multiVariable.getLeadingAnnotations());
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
                                colTemplate.append(" name = ");
                                if (nameArg.getAssignment() instanceof J.Literal literal) {
                                    colTemplate
                                            .append("\"")
                                            .append(sanitizeTableName(literal.toString()))
                                            .append("\"");
                                } else {
                                    colTemplate.append(nameArg.getAssignment().toString());
                                }
                                colTemplate.append(")");
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

                // Find optional source annotation (@Persistent)
                Optional<J.Annotation> sourceAnnotationIfAny = FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE).stream().findFirst();
                // Search for dependentElement
                boolean isDependent = sourceAnnotationIfAny
                        .flatMap(annotation -> RewriteUtils.findArgumentAsBoolean(annotation, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT))
                        .orElse(false);
                sourceAnnotationIfAny.ifPresent(annotation -> {
                    // When @Persistence is found replace
                    leadAnnos.remove(annotation);

                    // dependentElement
                    if (isDependent) {
                        template
                                .append(added.get() ? ", " : "")
                                .append("cascade = {CascadeType.REMOVE")
                                .append(StringUtils.isBlank(defaultCascade) ? "" : ", " + defaultCascade)
                                .append("}");
                        added.set(true);
                    }

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
                    added.set(true);
                });
                if (sourceAnnotationIfAny.isEmpty() || !isDependent) {
                    // Add the default cascade only
                    if (!StringUtils.isBlank(defaultCascade)) {
                        template
                                .append(added.get() ? ", " : "")
                                .append(" cascade = {")
                                .append(defaultCascade)
                                .append("}");
                    }
                }

                template.append(")");
                // Add @OneToMany and CascadeType
                maybeAddImport(TARGET_TYPE);
                maybeAddImport(Constants.Jpa.ONE_TO_ONE_ANNOTATION_FULL);
                maybeAddImport(Constants.Jpa.CASCADE_TYPE_FULL);
                maybeAddImport(Constants.Jpa.FETCH_TYPE_FULL);
                maybeAddImport(Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.PERSISTENT_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.COLUMN_ANNOTATION_FULL);

                var leadAnnosResult = ListUtils.concat(leadAnnos, ((J.VariableDeclarations) JavaTemplate.builder(template.toString())
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(TARGET_TYPE, Constants.Jpa.CASCADE_TYPE_FULL, Constants.Jpa.FETCH_TYPE_FULL, Constants.Jpa.ONE_TO_ONE_ANNOTATION_FULL)
                        .build()
                        .apply(getCursor(), multiVariable.getCoordinates().replaceAnnotations()))
                        .getLeadingAnnotations().get(0));

                // Add @JoinColumn when a @Column with name attribute is defined
                if (!colTemplate.isEmpty()) {// && !RewriteUtils.hasCollectionMemberOfSameTypeAsOwner(multiVariable)) {
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

    @Data
    protected static class Accumulator {
        Set<String> entityClasses = new HashSet<>();
        Map<String, String> varPersistentWithMappedBy = new java.util.HashMap<>();
    }
}
