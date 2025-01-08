package com.ecpnv.openrewrite.jdo2jpa;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

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

	public final static String CLASS_PATH = "jakarta.persistence-api";

	@Override
	public @NotNull String getDisplayName() {
		return "When there is an `@javax.jdo.annotations.PersistenceCapable` annotation it must be accompanied by a defined `@javax.persistence.Entity` annotation";
	}

	@Override
	public @NotNull String getDescription() {
		return "When an JDO entity is annotated with `@PersistenceCapable`, JPA must have a @Entity annotation.";
	}

	@Override
	public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

		return new JavaIsoVisitor<ExecutionContext>() {

			@Override
			public @NotNull J.ClassDeclaration visitClassDeclaration(
					@NotNull J.ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
				if (!FindAnnotations.find(classDecl, "@javax.jdo.annotations.PersistenceCapable").isEmpty()
						&& FindAnnotations.find(classDecl, "@javax.persistence.Entity").isEmpty()) {
					// Add @Entity
					maybeAddImport("javax.persistence.Entity");
					return JavaTemplate.builder("@Entity")
							.javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, CLASS_PATH))
							.imports("javax.persistence.Entity")
							.build()
							.apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

				}
				return super.visitClassDeclaration(classDecl, ctx);
			}
		};
	}
}
