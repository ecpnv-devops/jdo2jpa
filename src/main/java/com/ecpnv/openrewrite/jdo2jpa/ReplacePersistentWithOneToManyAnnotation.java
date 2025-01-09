package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistentWithOneToManyAnnotation extends Recipe {

    public final static String SOURCE_ANNOTATION_TYPE = "@javax.jdo.annotations.Persistent";
    public final static String TARGET_TYPE_NAME = "OneToMany";
    public final static String TARGET_TYPE = Constants.PERSISTENCE_BASE_PACKAGE + "." + TARGET_TYPE_NAME;
    public final static String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;
    public final static String ARGUMENT_MAPPEDBY = "mappedBy";

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
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // Exit if not Collection
                if (multiVariable.getType() == null || !multiVariable.getType().isAssignableFrom(Pattern.compile("java.util.Collection"))) {
                    return multiVariable;
                }
                // Exit if already has target annotation
                if (!FindAnnotations.find(multiVariable, TARGET_ANNOTATION_TYPE).isEmpty()) {
                    return multiVariable;
                }
                // Exit if no source annotation is found
                Set<J.Annotation> annotations = FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE);
                if (annotations.isEmpty()) {
                    return multiVariable;
                }
                // Find mappedby argument
                J.Annotation annotation = annotations.iterator().next();
                Optional<J.Assignment> mappedBy = RewriteUtils.findArguments(annotation, ARGUMENT_MAPPEDBY);
                if (mappedBy.isPresent()) {
                    // mappedBy argument found, hence oneToMany applies
                    String template = "@" + TARGET_TYPE_NAME + "(" + mappedBy.get() + ")";
                    // Add @Entity
                    maybeAddImport(TARGET_TYPE);

                    return JavaTemplate.builder(template)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.JPA_CLASS_PATH))
                            .imports(TARGET_TYPE)
                            .build()
                            .apply(getCursor(), annotation.getCoordinates().replace());
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

        };
    }
}
