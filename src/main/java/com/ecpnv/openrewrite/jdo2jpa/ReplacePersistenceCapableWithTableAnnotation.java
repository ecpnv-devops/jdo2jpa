package com.ecpnv.openrewrite.jdo2jpa;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

	public final static String CLASS_PATH = "jakarta.persistence-api";
	public final static String SOURCE_ANNOTATION_TYPE = "@javax.jdo.annotations.PersistenceCapable";
	public final static String TARGET_TYPE_NAME = "Table";
	public final static String TARGET_TYPE = "javax.persistence." + TARGET_TYPE_NAME;
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
					// Get schema
					String template = "@" + TARGET_TYPE_NAME +
							findArguments(sourceAnnotations, ARGUMENT_NAME)
									.map(J.Assignment::toString)
									.map(t -> "(" + t + ")")
									.orElse("");
					// Add @Entity
					maybeAddImport(TARGET_TYPE);
					return JavaTemplate.builder(template)
							.javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, CLASS_PATH))
							.imports(TARGET_TYPE)
							.build()
							.apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

				}
				return super.visitClassDeclaration(classDecl, ctx);
			}
		};
	}

	public static Optional<J.Assignment> findArguments(Set<J.Annotation> sourceAnnotations, String varName) {
		if (sourceAnnotations == null || sourceAnnotations.isEmpty() || StringUtils.isBlank(varName)) {
			return Optional.empty();
		}
		List<Expression> sourceFirstAnnotationArguments = sourceAnnotations.iterator().next().getArguments();
		if (sourceFirstAnnotationArguments == null || sourceFirstAnnotationArguments.isEmpty()) {
			return Optional.empty();
		}
		return sourceFirstAnnotationArguments.stream()
				.filter(a -> a instanceof J.Assignment)
				.map(a -> (J.Assignment) a)
				.filter(a -> varName.equals(a.getVariable().toString()))
				.findFirst();
	}
}
