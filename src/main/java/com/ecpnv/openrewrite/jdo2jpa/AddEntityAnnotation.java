package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Comparator;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The `AddEntityAnnotation` class represents a recipe for adding a `@javax.persistence.Entity` annotation
 * to a Java class when it is already annotated with `@javax.jdo.annotations.PersistenceCapable`.
 * This ensures that JPA entities are defined correctly when using JDO persistence annotations.
 * <p>
 * This recipe identifies Java class declarations that are annotated with `@PersistenceCapable`
 * but do not have the `@Entity` annotation. It automatically adds the `@Entity` annotation and imports
 * the required `javax.persistence.Entity` package if necessary.
 * <p>
 * Features:
 * - Searches for class declarations containing `@javax.jdo.annotations.PersistenceCapable`.
 * - Ensures that the `@Entity` annotation is added if missing.
 * - Automatically handles the required imports for `javax.persistence.Entity`.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddEntityAnnotation extends Recipe {

    public final static String SOURCE_ANNOTATION_TYPE = "@javax.jdo.annotations.PersistenceCapable";
    public final static String TARGET_TYPE_NAME = "Entity";
    public final static String TARGET_TYPE = Constants.PERSISTENCE_BASE_PACKAGE + "." + TARGET_TYPE_NAME;
    public final static String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;

    @Override
    public @NotNull String getDisplayName() {
        return "When there is an `" + SOURCE_ANNOTATION_TYPE + "` annotation it must be accompanied by " +
                "a defined `" + TARGET_ANNOTATION_TYPE + "` annotation";
    }

    @Override
    public @NotNull String getDescription() {
        return "When an JDO entity is annotated with `" + SOURCE_ANNOTATION_TYPE + "`, JPA must have a "
                + TARGET_ANNOTATION_TYPE + " annotation.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public @NotNull J.ClassDeclaration visitClassDeclaration(
                    @NotNull J.ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
                if (!FindAnnotations.find(classDecl, SOURCE_ANNOTATION_TYPE).isEmpty()
                        && FindAnnotations.find(classDecl, TARGET_ANNOTATION_TYPE).isEmpty()) {
                    // Add @Entity
                    maybeAddImport(TARGET_TYPE);
                    return JavaTemplate.builder("@" + TARGET_TYPE_NAME)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.JPA_CLASS_PATH))
                            .imports(TARGET_TYPE)
                            .build()
                            .apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }
}
