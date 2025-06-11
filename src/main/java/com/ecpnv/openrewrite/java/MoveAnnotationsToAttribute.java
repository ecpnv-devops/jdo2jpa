package com.ecpnv.openrewrite.java;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import com.ecpnv.openrewrite.util.JavaParserFactory;
import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * MoveAnnotationsToAttribute is a recipe that facilitates the migration of annotations by transferring
 * existing annotations into a specified attribute on another annotation. This can help dynamically refactor
 * code where certain annotations need to be consolidated into attributes of a target annotation.
 * <p>
 * Functionality:
 * - Identifies and processes a source annotation type from a class declaration.
 * - Moves the identified annotations into a specified attribute of a target annotation.
 * - Maintains existing attributes of the target annotation while appending the migrated annotations.
 * - Removes transferred annotations from their original positions.
 * - Adds necessary imports based on the types of annotations being processed.
 * <p>
 * Configuration:
 * - Source annotation type: Specifies the fully qualified name of the annotation to be moved.
 * - Target annotation type: Specifies the fully qualified name of the annotation to which the attributes will
 * be added.
 * - Attribute name: Specifies the name of the attribute to be created or updated in the target annotation.
 * <p>
 * Remarks:
 * - If the specified target attribute already exists in the target annotation, the recipe does not make changes.
 * - The recipe also ensures that the source annotation type and target annotation type are properly managed
 * in terms of imports and syntax.
 * <p>
 *
 * @author Patrick Deenen @ Open Circle Solutions
 * <p>
 * TODO Currently only supports annotations on a class; should be extended for annotations on fields and methods.
 */
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
            required = false,
            example = "timeout")
    String targetAttributeName;

    @Option(displayName = "Skip when no target and all on class",
            description = "When true and the target is not found and all of the source annotations are " +
                    "already on the class. The default is false.",
            required = false,
            example = "true")
    Boolean skipWhenNoTargetAndAllOnClass;

    @JsonCreator
    public MoveAnnotationsToAttribute(
            @NonNull @JsonProperty("sourceAnnotationType") String sourceAnnotationType,
            @NonNull @JsonProperty("targetAnnotationType") String targetAnnotationType,
            @Nullable @JsonProperty("targetAttributeName") String targetAttributeName,
            @Nullable @JsonProperty("skipWhenNoTargetAndAllOnClass") Boolean skipWhenNoTargetAndAllOnClass
    ) {
        this.sourceAnnotationType = sourceAnnotationType;
        this.targetAnnotationType = targetAnnotationType;
        this.targetAttributeName = targetAttributeName;
        this.skipWhenNoTargetAndAllOnClass = skipWhenNoTargetAndAllOnClass != null && skipWhenNoTargetAndAllOnClass;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(sourceAnnotationType, false), new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, ctx);
                // Find all source annotations
                List<J.Annotation> annotations = RewriteUtils.findLeadingAnnotations(classDeclaration, sourceAnnotationType, true);
                // Exit when no source annotations are found
                if (annotations.isEmpty()) {
                    return classDeclaration;
                }
                String shortTargetType = targetAnnotationType.substring(targetAnnotationType.lastIndexOf('.') + 1);
                StringBuilder template = new StringBuilder("@")
                        .append(shortTargetType)
                        .append("( ");
                // Find target annotation
                Optional<J.Annotation> targetAnnotation = FindAnnotations.find(classDeclaration, targetAnnotationType)
                        .stream()
                        .findFirst();
                // Exit when target attribute already exist
                if (targetAnnotation.isPresent() && RewriteUtils.findArgument(targetAnnotation.get(), targetAttributeName).isPresent()) {
                    return classDeclaration;
                }
                // Exit when skip is enabled and the target is not found and all of the source annotations are already on the class
                if (Boolean.TRUE.equals(skipWhenNoTargetAndAllOnClass) && targetAnnotation.isEmpty()
                        && classDeclaration.getLeadingAnnotations().containsAll(annotations)) {
                    return classDeclaration;
                }
                // Add existing attributes
                targetAnnotation.map(a -> a.getArguments())
                        .filter(Objects::nonNull)
                        .ifPresent(l -> l.forEach(e -> template.append(e).append(", ")));
                // Add target attribute to target annotation
                if (targetAttributeName != null && !"value".equals(targetAttributeName)) {
                    template.append(targetAttributeName).append(" = ");
                }
                template.append("{");
                String shortSourceType = sourceAnnotationType.substring(sourceAnnotationType.lastIndexOf('.') + 1);
                for (Iterator<J.Annotation> it = annotations.iterator(); it.hasNext(); ) {
                    J.Annotation annotation = it.next();
                    template.append("\n@").append(shortSourceType);
                    if (CollectionUtils.isNotEmpty(annotation.getArguments())) {
                        template
                                .append("( ")
                                .append(String.join(", ", annotation.getArguments().stream().map(Objects::toString).toList()))
                                .append(")");
                    }
                    if (it.hasNext()) template.append(", ");
                }
                template.append("})");
                // Imports when needed
                maybeAddImport(sourceAnnotationType);
                maybeAddImport(targetAnnotationType);
                // Remove existing annotations
                classDeclaration = new RemoveAnnotationVisitor(new AnnotationMatcher(sourceAnnotationType))
                        .visitClassDeclaration(classDeclaration, ctx);
                // When target annotation exists then add or replace attribute
                var annos = classDeclaration.getLeadingAnnotations();
                targetAnnotation.ifPresent(annos::remove);
                // Add annotation as attribute
                annos.add(((J.ClassDeclaration) JavaTemplate.builder(template.toString())
                        .javaParser(JavaParserFactory.create(ctx))
                        .imports(targetAnnotationType)
                        .build()
                        .apply(getCursor(), classDeclaration.getCoordinates().replaceAnnotations()))
                        .getLeadingAnnotations()
                        .get(0)
                        .withPrefix(Space.format("\n")));
                classDeclaration = classDeclaration
                        .withLeadingAnnotations(annos);
                maybeAutoFormat(classDecl, classDeclaration, ctx);
                return classDeclaration;
            }
        });
    }

}
