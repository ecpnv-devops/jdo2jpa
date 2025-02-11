package com.ecpnv.openrewrite.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that removes annotations of a specified type when they have no attributes.
 * This is useful for cleaning up redundant or unused annotations in source code.
 * <p>
 * The annotation type to be removed is specified by the `annotationType` parameter,
 * which is provided when instantiating this class.
 * <p>
 * This class utilizes a visitor, `RemoveAnnotationVisitor`, with a specialized matcher
 * that identifies annotations with no arguments for removal based on the given annotation type.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveAnnotationWithoutAttributes extends Recipe {

    @Option(displayName = "Annotation type",
            description = "An annotation type.",
            example = "@java.lang.SuppressWarnings")
    String annotationType;

    @JsonCreator
    public RemoveAnnotationWithoutAttributes(@NonNull @JsonProperty("annotationType") String annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public String getDisplayName() {
        return "Remove empty annotation";
    }

    @Override
    public String getDescription() {
        return "Remove annotations with no attributes";
    }

    @Override
    public RemoveAnnotationVisitor getVisitor() {
        return new RemoveAnnotationVisitor(new EmptyAnnotationMatcher(annotationType));
    }

    class EmptyAnnotationMatcher extends AnnotationMatcher {

        public EmptyAnnotationMatcher(String signature) {
            super(signature);
        }

        @Override
        public boolean matches(J.Annotation annotation) {
            return super.matches(annotation) &&
                    (annotation.getArguments() == null || CollectionUtils.isEmpty(annotation.getArguments()));
        }
    }
}
