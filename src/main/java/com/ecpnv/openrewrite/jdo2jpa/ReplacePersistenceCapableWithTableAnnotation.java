package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Comparator;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The `ReplacePersistenceCapableWithTableAnnotation` class represents a recipe for replacing
 * the `@javax.jdo.annotations.PersistenceCapable` annotation with the corresponding
 * `@javax.persistence.Table` annotation in Java classes.
 * <p>
 * This recipe ensures compatibility with JPA by transforming JDO entity classes that
 * utilize the `@PersistenceCapable` annotation to use the JPA equivalent `@Table` annotation.
 * <p>
 * Features:
 * - Searches for class declarations containing the `@javax.jdo.annotations.PersistenceCapable` annotation.
 * - Adds the `@javax.persistence.Table` annotation if not already present.
 * - Automatically handles the required imports for `javax.persistence.Table`.
 * - Copies the `schema` argument from the original `@PersistenceCapable` annotation, if present,
 * to the newly added `@Table` annotation.
 * <p>
 * Implementation:
 * - Uses a `TreeVisitor` to identify classes that fulfill the criteria for transformation.
 * - Applies a Java template to add the `@Table` annotation and update imports as needed.
 * - Maintains the semantic integrity of the annotated class during transformation.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistenceCapableWithTableAnnotation extends Recipe {

    public final static String SOURCE_ANNOTATION_TYPE = "@javax.jdo.annotations.PersistenceCapable";
    public final static String TARGET_TYPE_NAME = "Table";
    public final static String TARGET_TYPE = Constants.PERSISTENCE_BASE_PACKAGE + "." + TARGET_TYPE_NAME;
    public final static String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;
    public final static String ARGUMENT_NAME = "schema";

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

        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public @NotNull J.ClassDeclaration visitClassDeclaration(
                    @NotNull J.ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
                Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, SOURCE_ANNOTATION_TYPE);
                if (!sourceAnnotations.isEmpty()
                        && FindAnnotations.find(classDecl, TARGET_ANNOTATION_TYPE).isEmpty()) {
                    J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();

                    // Get schema
                    String template = "@" + TARGET_TYPE_NAME +
                            RewriteUtils.findArguments(sourceAnnotation, ARGUMENT_NAME)
                                    .map(J.Assignment::toString)
                                    .map(t -> "(" + t + ")")
                                    .orElse("");
                    // Add @Entity
                    maybeAddImport(TARGET_TYPE);
                    return JavaTemplate.builder(template)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.JPA_CLASS_PATH))
                            .imports(TARGET_TYPE)
                            .build()
                            .apply(getCursor(), sourceAnnotation.getCoordinates().replace());

                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }
}
