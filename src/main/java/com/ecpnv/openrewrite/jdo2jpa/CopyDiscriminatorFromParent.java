package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.java.AddOrUpdateAnnotationAttribute;
import com.ecpnv.openrewrite.java.CopyNonInheritedAnnotations;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A specialization of the `CopyNonInheritedAnnotations` class that is focused specifically on copying the
 * `javax.jdo.annotations.Discriminator` annotation from a parent class to the current class declaration.
 * <p>
 * This class ensures that the `@javax.jdo.annotations.Discriminator` annotation is properly transferred and
 * applied to the current class if it exists on any of the parent classes. Additionally, the annotation's discriminator
 * value is changed to the fully qualified class name of the current class.
 * <p>
 * Key behaviors include:
 * - Scanning parent types for the `javax.jdo.annotations.Discriminator` annotation.
 * - Adding the annotation to the current class if it is not already present.
 * - Processing the transferred annotation to ensure attributes are adjusted as needed.
 * <p>
 * This class simplifies the specific use case of handling the `Discriminator` annotation while leveraging
 * the general mechanisms provided by `CopyNonInheritedAnnotations`.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class CopyDiscriminatorFromParent extends CopyNonInheritedAnnotations {

    public CopyDiscriminatorFromParent() {
        super(Set.of(Constants.Jdo.DISCRIMINATOR_ANNOTATION_FULL));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {

        return new CopyAnnoVisitor(acc.getParentAnnotationsByType()) {

            @Override
            protected J.Annotation processAddedAnnotation(
                    J.ClassDeclaration classDeclaration, J.Annotation annotation, ExecutionContext ctx) {
                Object r = new AddOrUpdateAnnotationAttribute(Constants.Jdo.DISCRIMINATOR_ANNOTATION_FULL, false,
                        null, classDeclaration.getType().getFullyQualifiedName(), "null",
                        AddOrUpdateAnnotationAttribute.Operation.BOTH)
                        .getAddOrUpdateAnnotationAttributeVisitor().visit(annotation, ctx);
                return annotation;
            }
        };
    }

}
