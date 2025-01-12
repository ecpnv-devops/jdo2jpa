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
