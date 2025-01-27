package com.ecpnv.openrewrite.java;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.jdo2jpa.Constants;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class MoveAnnotationsToAttribute extends Recipe {

    @Override
    public String getDisplayName() {
        return "Move annotation(s) to attribute of another annotation";
    }

    @Override
    public String getDescription() {
        return "Some annotations accept attributes of annotation types. This recipe moves existing annotations to the " +
                "specified new attribute on another annotation.";
    }

    @Option(displayName = "Source annotation type",
            description = "The fully qualified name of the source annotation to move.",
            example = "org.junit.Test")
    String sourceAnnotationType;

    @Option(displayName = "Target annotation type",
            description = "The fully qualified name of the target annotation.",
            example = "org.junit.Test")
    String targetAnnotationType;

    @Option(displayName = "Attribute name to create",
            description = "The name of attribute to create in the target.",
            example = "timeout")
    String targetAttributeName;

    @JsonCreator
    public MoveAnnotationsToAttribute(
            @NonNull @JsonProperty("sourceAnnotationType") String sourceAnnotationType,
            @Nullable @JsonProperty("targetAnnotationType") String targetAnnotationType,
            @NonNull @JsonProperty("targetAttributeName") String targetAttributeName) {
        this.sourceAnnotationType = sourceAnnotationType;
        this.targetAnnotationType = targetAnnotationType;
        this.targetAttributeName = targetAttributeName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(sourceAnnotationType, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, ctx);
                // Find all source annotations
                List<J.Annotation> annotations = FindAnnotations.find(classDeclaration, sourceAnnotationType).stream()
                        .filter(a -> TypeUtils.isOfClassType(a.getType(), sourceAnnotationType))
                        .toList();
                // Exit when no source annotations are found
                if (annotations.isEmpty()) {
                    return classDeclaration;
                }
                StringBuilder template = new StringBuilder("@")
                        .append(targetAnnotationType)
                        .append("( ");
                // Find target annotation
                Optional<J.Annotation> targetAnnotation = FindAnnotations.find(classDeclaration, targetAnnotationType)
                        .stream()
                        .findFirst();
                // Exit when target attribute already exist
                if (targetAnnotation.isPresent() && RewriteUtils.findArgument(targetAnnotation.get(), targetAttributeName).isPresent()) {
                    return classDeclaration;
                }
                // Add existing attributes
                targetAnnotation.map(a -> a.getArguments())
                        .filter(Objects::nonNull)
                        .ifPresent(l -> l.forEach(e -> template.append(e).append(", ")));
                // Add target attribute to target annotation
                template.append(targetAttributeName).append(" = {");
                for (Iterator<J.Annotation> it = annotations.iterator(); it.hasNext(); ) {
                    J.Annotation a = it.next();
                    template.append("@").append(sourceAnnotationType);
                    if (a.getArguments() != null && !a.getArguments().isEmpty()) {
                        template
                                .append("(")
                                .append(String.join(",", a.getArguments().stream().map(Objects::toString).toList()))
                                .append(")");
                    }
                    if (it.hasNext()) template.append(", ");
                }
                template.append("})");
                // Imports when needed
                maybeAddImport(targetAnnotationType);
                // Apply template
                return JavaTemplate.builder(template.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, Constants.Jpa.CLASS_PATH))
                        .imports(targetAnnotationType)
                        .build()
                        .apply(getCursor(), targetAnnotation
                                // When target annotation exists then add or replace attribute
                                .map(ta -> ta.getCoordinates().replace())
                                // When target annotation does not exist then add annotation
                                .orElseGet(() -> classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))));
            }
        });
    }

}
