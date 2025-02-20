package com.ecpnv.openrewrite.jdo2jpa;

import java.util.List;
import java.util.Set;

import com.ecpnv.openrewrite.java.AddOrUpdateAnnotationAttribute;
import com.ecpnv.openrewrite.java.CopyNonInheritedAnnotations;
import com.ecpnv.openrewrite.util.RewriteUtils;

import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A specialization of the `CopyNonInheritedAnnotations` class that is focused specifically on copying the
 * `javax.jdo.annotations.Discriminator` annotation from a parent class to the current class declaration.
 * <p>
 * This class ensures that the `@javax.jdo.annotations.Discriminator` annotation is properly transferred and
 * applied to the current class if it exists on any of the parent classes. Additionally, the annotation's discriminator
 * value is changed to the fully qualified class name of the current class when no discriminator value is set
 * (discriminator strategy is class name).
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
            protected J.Annotation processExistingAnnotation(J.ClassDeclaration classDeclaration, J.Annotation annotation, ExecutionContext ctx) {
                if (classDeclaration.getType() != null) {
                    String classFgn = classDeclaration.getType().getFullyQualifiedName();
                    List<J.Annotation> foundAnnotations = parentAnnotationsByType.get(classFgn);
                    // When the annotation already exists
                    if (foundAnnotations != null && foundAnnotations.contains(annotation)
                            // and the value argument is not provided
                            && RewriteUtils.findArgumentValue(annotation, null).isEmpty()) {
                        // Then apply the class name when not available
                        return processAddedAnnotation(classDeclaration, annotation, ctx);
                    }
                }
                return super.processExistingAnnotation(classDeclaration, annotation, ctx);
            }

            @Override
            protected J.Annotation processAddedAnnotation(
                    J.ClassDeclaration classDeclaration, J.Annotation annotation, ExecutionContext ctx) {
                // When no discriminator value exist then strategy is class name, hence the class name has to be added explicitly for JPA
                J.Annotation newAnno = (J.Annotation) new AddOrUpdateAnnotationAttribute(
                        Constants.Jdo.DISCRIMINATOR_ANNOTATION_FULL, false,
                        null, classDeclaration.getType().getFullyQualifiedName(), "null",
                        AddOrUpdateAnnotationAttribute.Operation.BOTH)
                        .getAddOrUpdateAnnotationAttributeVisitor().visit(annotation, ctx, getCursor());
                classDeclaration.getLeadingAnnotations().remove(annotation);
                classDeclaration.getLeadingAnnotations().add(newAnno);
                return newAnno;
            }
        };
    }

}
