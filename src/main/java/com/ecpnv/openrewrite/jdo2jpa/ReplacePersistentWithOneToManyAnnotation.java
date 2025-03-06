package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Map;
import java.util.Optional;
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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.RemoveAnnotationAttribute;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.java.AddAnnotationConditionally;
import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * This class defines a migration recipe for replacing occurrences of the <code>@javax.jdo.annotations.Persistent</code>
 * annotation with the equivalent JPA <code>@OneToMany</code> annotation in Java code.
 * <p>
 * The transformation ensures compatibility with JPA by locating fields annotated with
 * <code>@javax.jdo.annotations.Persistent</code>, analyzing the attributes of the annotation (e.g., `mappedBy`),
 * and it replaces the annotation with the corresponding JPA compliant <code>@OneToMany</code> annotation.
 * <p>
 * The migration adheres to the following rules:
 * <ul>
 * <li> Fields must be assignable from {@link java.util.Collection}.
 * <li> If a field already has a <code>@OneToMany</code> annotation, it will be skipped.
 * <li> If the <code>@javax.jdo.annotations.Persistent</code> annotation does not exist, no transformation occurs.
 * <li> When the JDO `mappedBy` attribute is provided, it is correctly mapped to the equivalent `mappedBy` attribute in JPA's <code>@OneToMany</code> annotation.
 * <li> Ensures that relevant imports (<code>javax.persistence.OneToMany</code>) are updated or added when necessary.
 * </ul>
 * <p>
 * The class uses a `JavaIsoVisitor` to traverse the Abstract Syntax Tree (AST) of the Java source code and
 * apply the required transformations to the target variable declarations.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistentWithOneToManyAnnotation extends ScanningRecipe<ReplacePersistentWithOneToManyAnnotation.Accumulator> {

    public static final String SOURCE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENT_ANNOTATION_FULL;
    public static final String TARGET_TYPE_NAME = Constants.Jpa.ONE_TO_MANY_ANNOTATION_NAME;
    public static final String TARGET_TYPE = Constants.Jpa.ONE_TO_MANY_ANNOTATION_FULL;
    public static final String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;

    @Option(displayName = "Default cascade types to apply",
            description = "When the " + TARGET_ANNOTATION_TYPE +
                    " is applied, then these optional cascade type default is applied.",
            required = false,
            example = "CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH")
    @Nullable
    String defaultCascade;

    @JsonCreator
    public ReplacePersistentWithOneToManyAnnotation(@NonNull @JsonProperty("defaultCascade") String defaultCascade) {
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
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, executionContext);
                        // Check if Collection or already has target annotation or no source annotation is found
                        Optional<J.Annotation> persistentAnno = getPersistentAnnotation(mv);
                        if (validateVar(mv) && persistentAnno.isPresent()) {
                            // Find mappedby argument
                            RewriteUtils.findArgumentAssignment(persistentAnno.get(), Constants.Jpa.ONE_TO_MANY_ARGUMENT_MAPPED_BY)
                                    .ifPresent(assignment -> {
                                        // Add fqn#varname,column-name-value to accumulator
                                        RewriteUtils.getParameterType(multiVariable, 0, 0)
                                                .map(JavaType.FullyQualified::getFullyQualifiedName)
                                                .ifPresent(name -> acc.varPersistentWithMappedBy
                                                        .put(name, assignment.getAssignment().toString()));
                                    });
                        } else {
                            // Find @Column#name
                            FindAnnotations.find(mv, Constants.Jdo.COLUMN_ANNOTATION_FULL).stream()
                                    .findFirst()
                                    .ifPresent(ca -> {
                                        // Find name
                                        RewriteUtils.findArgumentAssignment(ca, Constants.Jdo.ARGUMENT_NAME)
                                                .map(J.Assignment::getAssignment)
                                                .ifPresent(e -> {
                                                    // Add fqn#varname,column-name-value to accumulator
                                                    var name = RewriteUtils.toFullyQualifiedNameWithVar(mv.getVariables().get(0));
                                                    acc.varColumnWithName.put(name, e.toString());
                                                });
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
                new ReplacePersistentWithOneToManyAnnotationVisitor(acc));
    }


    static boolean validateVar(J.VariableDeclarations multiVariable) {
        // Should have a Collection
        return multiVariable.getType() != null && multiVariable.getType().isAssignableFrom(Pattern.compile("java.util.Collection"))
                // Should not have target annotation
                && FindAnnotations.find(multiVariable, TARGET_ANNOTATION_TYPE).isEmpty();
    }

    static Optional<J.Annotation> getPersistentAnnotation(J.VariableDeclarations multiVariable) {
        // Exit if no source annotation is found
        return FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE).stream().findFirst();
    }

    public class ReplacePersistentWithOneToManyAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        Accumulator acc;

        public ReplacePersistentWithOneToManyAnnotationVisitor(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            multiVariable = super.visitVariableDeclarations(multiVariable, ctx);
            // Find @Element annotation
            Optional<J.Annotation> elemAnno = FindAnnotations.find(multiVariable, Constants.Jdo.ELEMENT_ANNOTATION_FULL).stream().findFirst();
            // Find source annotation == @Persistent
            J.Annotation persistentAnno = getPersistentAnnotation(multiVariable).orElse(elemAnno.orElse(null));
            // Find @Column
            Optional<J.Annotation> colAnno = FindAnnotations.find(multiVariable, Constants.Jdo.COLUMN_ANNOTATION_FULL).stream().findFirst();
            if (persistentAnno == null && colAnno.isPresent()) {
                // Remove name from @Column?
                var varColName = RewriteUtils.toFullyQualifiedNameWithVar(multiVariable.getVariables().get(0));
                var persistentVarType = multiVariable.getVariables().get(0).getVariableType().getOwner().toString();
                var varName = multiVariable.getVariables().get(0).getSimpleName();
                if (acc.varColumnWithName.containsKey(varColName) && varName.equals(acc.varPersistentWithMappedBy.get(persistentVarType))) {
                    // Remove annotation attribute
                    multiVariable = (J.VariableDeclarations) new RemoveAnnotationAttribute(Constants.Jdo.COLUMN_ANNOTATION_FULL,
                            Constants.Jdo.ARGUMENT_NAME).getVisitor().visit(multiVariable, ctx);
                }
                return multiVariable;
            }
            // Check for Collection or already has target annotation
            if (!validateVar(multiVariable)) {
                return multiVariable;
            }
            // Find mappedby argument
            Optional<J.Assignment> mappedBy = RewriteUtils.findArgumentAssignment(persistentAnno, Constants.Jpa.ONE_TO_MANY_ARGUMENT_MAPPED_BY);
            // Find Table argument
            Optional<J.Assignment> table = RewriteUtils.findArgumentAssignment(persistentAnno, Constants.Jdo.PERSISTENT_ARGUMENT_TABLE);
            // Find @Join annotation
            Optional<J.Annotation> joinAnno = FindAnnotations.find(multiVariable, Constants.Jdo.JOIN_ANNOTATION_FULL).stream().findFirst();
            if (mappedBy.isPresent() || table.isPresent() || joinAnno.isPresent()) {
                // oneToMany applies
                StringBuilder template = new StringBuilder("@").append(TARGET_TYPE_NAME).append("(");
                mappedBy.ifPresent(template::append);

                // Search for dependentElement
                AtomicBoolean added = new AtomicBoolean(false);
                RewriteUtils.findArgumentAsBoolean(persistentAnno, Constants.Jdo.PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT)
                        .filter(isDependent -> isDependent)
                        .ifPresentOrElse(isDependent -> {
                            mappedBy.ifPresent(ma -> template.append(", "));
                            template
                                    .append("cascade = {CascadeType.REMOVE")
                                    .append(StringUtils.isBlank(defaultCascade) ? "" : ", " + defaultCascade)
                                    .append("}");
                            added.set(true);
                        }, () -> {
                            if (!StringUtils.isBlank(defaultCascade)) {
                                mappedBy.ifPresent(ma -> template.append(", "));
                                template
                                        .append("cascade = {")
                                        .append(defaultCascade)
                                        .append("}");
                                added.set(true);
                            }
                        });

                // Search for defaultFetchGroup
                if (mappedBy.isPresent() || added.get()) {
                    template.append(", ");
                }
                template.append("fetch = FetchType.");
                RewriteUtils.findArgumentAsBoolean(persistentAnno, Constants.Jdo.PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP)
                        .ifPresentOrElse(isDefault -> {
                            if (Boolean.TRUE.equals(isDefault))
                                template.append("EAGER");
                            else
                                template.append("LAZY");
                        }, () -> template.append("LAZY")
                        );

                template.append(")");
                // Add @OneToMany and CascadeType
                maybeAddImport(TARGET_TYPE);
                maybeAddImport(Constants.Jpa.CASCADE_TYPE_FULL);
                maybeAddImport(Constants.Jpa.FETCH_TYPE_FULL);
                maybeAddImport(Constants.Jpa.JOIN_TABLE_ANNOTATION_FULL);
                maybeAddImport(Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.PERSISTENT_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.JOIN_ANNOTATION_FULL);
                maybeRemoveImport(Constants.Jdo.ELEMENT_ANNOTATION_FULL);

                // Add @OneToMany
                multiVariable = JavaTemplate.builder(template.toString())
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(TARGET_TYPE, Constants.Jpa.CASCADE_TYPE_FULL, Constants.Jpa.FETCH_TYPE_FULL)
                        .build()
                        .apply(getCursor(), persistentAnno.getCoordinates().replace());

                if (mappedBy.isPresent()) {
                    // Must we add a @JoinColumn?
                    Optional<J.Annotation> joinColAnno = FindAnnotations.find(multiVariable, Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL).stream().findFirst();
                    if (!joinAnno.isPresent() && !joinColAnno.isPresent()) {
                        var templateJC = RewriteUtils.getParameterType(multiVariable, 0, 0)
                                .map(pt -> pt.getFullyQualifiedName() + "#" + mappedBy.get().getAssignment())
                                .map(key -> acc.varColumnWithName.get(key))
                                .map(name -> new StringBuilder("@")
                                                .append(Constants.Jpa.JOIN_COLUMN_ANNOTATION_NAME)
                                                .append("(name = \"")
                                                .append(name)
                                                .append("\")\n")
                                                .toString()
                                );
                        if (templateJC.isPresent()) {
                            multiVariable = (J.VariableDeclarations) new AddAnnotationConditionally(
                                    ".*" + Constants.Jpa.ONE_TO_MANY_ANNOTATION_NAME + ".*",
                                    Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL, templateJC.get(), "VAR")
                                    .getVisitor().visit(multiVariable, ctx, getCursor().getParent());
                        }
                    }
                }

                if (table.isPresent() || joinAnno.isPresent()) {
                    // Add @JoinTable to var with table name
                    StringBuilder joinTableTemplate = new StringBuilder("@")
                            .append(Constants.Jpa.JOIN_TABLE_ANNOTATION_NAME)
                            .append("( ");
                    table.ifPresent(t -> joinTableTemplate.append("name = \"")
                            .append(t.getAssignment())
                            .append("\""));
                    // Add join column
                    addJoinColumns(joinAnno, joinTableTemplate, "joinColumns", table.isPresent());
                    // Add inverse join column
                    addJoinColumns(elemAnno, joinTableTemplate, "inverseJoinColumns", true);
                    joinTableTemplate.append(")");
                    // Add @JoinTable
                    multiVariable = (J.VariableDeclarations) new AddAnnotationConditionally(
                            ".*" + Constants.Jpa.ONE_TO_MANY_ANNOTATION_NAME + ".*",
                            Constants.Jpa.JOIN_TABLE_ANNOTATION_FULL, joinTableTemplate.toString(), "VAR")
                            .getVisitor().visit(multiVariable, ctx, getCursor().getParent());
                    // Remove @Join
                    multiVariable = (J.VariableDeclarations) new RemoveAnnotation(Constants.Jdo.JOIN_ANNOTATION_FULL).getVisitor().visit(multiVariable, ctx);
                    // Remove @Element
                    multiVariable = (J.VariableDeclarations) new RemoveAnnotation(Constants.Jdo.ELEMENT_ANNOTATION_FULL).getVisitor().visit(multiVariable, ctx);
                } else if (elemAnno.isPresent()) {
                    // Add @JoinColumn to var
                    StringBuilder joinColTemplate = new StringBuilder("@")
                            .append(Constants.Jpa.JOIN_COLUMN_ANNOTATION_NAME)
                            .append("(");
                    RewriteUtils.findArgumentAssignment(elemAnno.get(), Constants.Jdo.ARGUMENT_NAME)
                            .map(assignment -> assignment.getAssignment().toString())
                            .ifPresent(name -> joinColTemplate
                                    .append("name = \"")
                                    .append(name)
                                    .append("\""));
                    joinColTemplate.append(")\n");
                    multiVariable = (J.VariableDeclarations) new AddAnnotationConditionally(
                            ".*" + Constants.Jpa.ONE_TO_MANY_ANNOTATION_NAME + ".*",
                            Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL, joinColTemplate.toString(), "VAR")
                            .getVisitor().visit(multiVariable, ctx, getCursor().getParent());
                }
                return multiVariable;
            }
            return multiVariable;
        }

        private void addJoinColumns(
                Optional<J.Annotation> joinAnno, StringBuilder joinTableTemplate,
                String joinColumnsName, boolean hasPreviousArg) {
            joinAnno.ifPresent(ja -> {
                if (hasPreviousArg) {
                    joinTableTemplate.append(",\n");
                }
                Optional<J.Assignment> colArg = RewriteUtils.findArgumentAssignment(ja, Constants.Jdo.JOIN_ARGUMENT_COLUMN);
                joinTableTemplate
                        .append(joinColumnsName)
                        .append(" = {@")
                        .append(Constants.Jpa.JOIN_COLUMN_ANNOTATION_FULL)
                        .append("(");
                colArg.ifPresent(c -> joinTableTemplate
                        .append("name = \"")
                        .append(c.getAssignment())
                        .append("\")}"));
            });
        }
    }

    @Data
    protected static class Accumulator {
        Map<String, String> varColumnWithName = new java.util.HashMap<>();
        Map<String, String> varPersistentWithMappedBy = new java.util.HashMap<>();
    }
}
