package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplacePersistentFetchGroupWithColumnAnnotation extends Recipe {

    public final static String SOURCE_ANNOTATION_TYPE = "@javax.jdo.annotations.Persistent";
    public final static String TARGET_TYPE_NAME = "Column";
    public final static String TARGET_TYPE = Constants.PERSISTENCE_BASE_PACKAGE + "." + TARGET_TYPE_NAME;
    public final static String TARGET_ANNOTATION_TYPE = "@" + TARGET_TYPE;
    public final static String ARGUMENT_DEFAULT_FETCH_GROUP = "defaultFetchGroup";
    public final static String FETCH_TYPE = Constants.PERSISTENCE_BASE_PACKAGE + "." + "FetchType";

    @Override
    public @NotNull String getDisplayName() {
        return "When there is an `" + SOURCE_ANNOTATION_TYPE + "` annotation with '" + ARGUMENT_DEFAULT_FETCH_GROUP +
                "' argument, it must be replaced by a " + TARGET_ANNOTATION_TYPE + " annotation";
    }

    @Override
    public @NotNull String getDescription() {
        return "When an JDO entity is annotated with `" + SOURCE_ANNOTATION_TYPE + "` which has an argument '" +
                ARGUMENT_DEFAULT_FETCH_GROUP + "', JPA must have a " + TARGET_ANNOTATION_TYPE +
                " annotation with a fetch lazy or eager argument.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // Find target annotation
                Optional<J.Annotation> existingColumnAnno = FindAnnotations.find(multiVariable, TARGET_ANNOTATION_TYPE).stream().findFirst();
                // Exit if the column already has a fetch argument
                if (existingColumnAnno.isPresent() && RewriteUtils.findArguments(existingColumnAnno.get(), "fetch").isPresent()) {
                    // TODO throw exception as this may not occur, e.g. illegal JDO definition on field
                    return multiVariable;
                }
                // Exit if no source annotation is found
                Optional<J.Annotation> annotationIfAny = FindAnnotations.find(multiVariable, SOURCE_ANNOTATION_TYPE).stream().findFirst();
                if (!annotationIfAny.isPresent()) {
                    return multiVariable;
                }
                // Find fetchGroup argument
                AtomicReference<J.Annotation> annotation = new AtomicReference<>(annotationIfAny.get());
                Optional<J.Assignment> fetchGroup = RewriteUtils.findArguments(annotation.get(), ARGUMENT_DEFAULT_FETCH_GROUP);
                if (fetchGroup.isPresent()) {
                    // fetchGroup argument found, hence @Column applies
                    StringBuilder template = new StringBuilder("@").append(TARGET_TYPE_NAME).append("( ");
                    existingColumnAnno.ifPresent(a -> {
                        a.getArguments().forEach(arg -> template.append(arg).append(", "));
                        annotation.set(a);
                    });
                    template.append("fetch = FetchType.");
                    if (fetchGroup.get().getAssignment().toString().equals("true")) {
                        template.append("EAGER");
                    } else {
                        template.append("LAZY");
                    }
                    template.append(")");
                    // Add @OneToMany and CascadeType
                    maybeAddImport(TARGET_TYPE);
                    maybeAddImport(FETCH_TYPE);

                    return JavaTemplate.builder(template.toString())
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.JPA_CLASS_PATH))
                            .imports(TARGET_TYPE, FETCH_TYPE)
                            .build()
                            .apply(getCursor(), annotation.get().getCoordinates().replace());
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

        };
    }
}
