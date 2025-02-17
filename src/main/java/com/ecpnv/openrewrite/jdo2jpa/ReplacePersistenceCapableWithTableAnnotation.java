package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The <code>ReplacePersistenceCapableWithTableAnnotation</code> class represents a recipe for replacing
 * the <code>@javax.jdo.annotations.PersistenceCapable</code> annotation with the corresponding
 * <code>@javax.persistence.Table</code> annotation in Java classes.
 * <p>
 * This recipe ensures compatibility with JPA by transforming JDO entity classes that
 * utilize the <code>@PersistenceCapable</code> annotation to use the JPA equivalent <code>@Table</code> annotation.
 * <p>
 * Features:
 * <ul>
 * <li>Searches for class declarations containing the <code>@javax.jdo.annotations.PersistenceCapable</code> annotation.</li>
 * <li>Adds the <code>@javax.persistence.Table</code> annotation if not already present.</li>
 * <li>Automatically handles the required imports for <code>javax.persistence.Table</code>.</li>
 * <li>Copies the <code>schema</code> argument from the original <code>@PersistenceCapable</code> annotation, if present, to the newly added <code>@Table</code> annotation.</li>
 * </ul>
 * <p>
 * Implementation:
 * - Uses a <code>TreeVisitor</code> to identify classes that fulfill the criteria for transformation.
 * - Applies a Java template to add the <code>@Table</code> annotation and update imports as needed.
 * - Maintains the semantic integrity of the annotated class during transformation.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistenceCapableWithTableAnnotation extends Recipe {

    public static final String SOURCE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL;
    public static final String TARGET_TYPE_NAME = Constants.Jpa.TABLE_ANNOTATION_NAME;
    public static final String TARGET_TYPE = Constants.Jpa.TABLE_ANNOTATION_FULL;
    public static final String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;

    @Override
    public @NotNull String getDisplayName() {
        return "When there is an `" + SOURCE_ANNOTATION_TYPE + "` annotation it must be replaced by a " +
                TARGET_ANNOTATION_TYPE + " annotation";
    }

    @Override
    public @NotNull String getDescription() {
        return "When an JDO entity is annotated with `@PersistenceCapable`, JPA must have a @Table annotation.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {

            @Override
            public @NotNull J.ClassDeclaration visitClassDeclaration(
                    @NotNull J.ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
                Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, SOURCE_ANNOTATION_TYPE);
                if (!sourceAnnotations.isEmpty()
                        && FindAnnotations.find(classDecl, TARGET_ANNOTATION_TYPE).isEmpty()) {
                    J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();

                    StringBuilder template = new StringBuilder("@" + TARGET_TYPE_NAME);
                    // Get schema
                    boolean addedSchema = RewriteUtils.findArgument(sourceAnnotation, Constants.Jpa.TABLE_ARGUMENT_SCHEMA)
                            .map(t -> addSchema(t, template)).orElse(false);

                    // Get table
                    RewriteUtils.findArgumentValueAsString(sourceAnnotation, Constants.Jpa.TABLE_ARGUMENT_TABLE)
                            .ifPresentOrElse(t -> addTable(t, addedSchema, template), () -> {
                                if (addedSchema) {
                                    template.append(")");
                                }
                            });

                    // Add @Table import
                    maybeAddImport(TARGET_TYPE);
                    maybeRemoveImport(Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL);

                    return JavaTemplate.builder(template.toString())
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(TARGET_TYPE)
                            .build()
                            .apply(getCursor(), sourceAnnotation.getCoordinates().replace());

                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            private static void addTable(String t, boolean addedSchema, StringBuilder template) {
                if (addedSchema) {
                    template.append(", ");
                } else {
                    template.append("(");
                }
                template.append(" table = \"" + t + "\")");
            }

            private static boolean addSchema(J.Assignment assignment, StringBuilder template) {
                if (assignment.getAssignment() instanceof J.FieldAccess fieldAccess) {
                    template.append("( schema = " + fieldAccess);
                } else if (assignment.getAssignment() instanceof J.Literal literal) {
                    template.append("( schema = \"" + literal + "\"");
                }
                return true;
            }
        };
    }
}
