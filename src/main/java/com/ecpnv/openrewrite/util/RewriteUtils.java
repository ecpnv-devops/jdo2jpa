package com.ecpnv.openrewrite.util;

import java.util.Optional;
import java.util.Set;

import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.J;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RewriteUtils {

    /**
     * Finds the first assignment expression within the arguments of the first annotation in the provided annotation set
     * that matches the specified variable name.
     *
     * @param sourceAnnotations the set of annotations to search for the assignment. Must not be null or empty.
     * @param varName           the name of the variable to match within the assignment. Must not be blank.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J.Assignment> findArguments(Set<J.Annotation> sourceAnnotations, String varName) {
        if (sourceAnnotations == null || sourceAnnotations.isEmpty() || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        return findArguments(sourceAnnotations.iterator().next(), varName);
    }

    /**
     * Finds the first assignment expression within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J.Assignment> findArguments(J.Annotation sourceAnnotation, String varName) {
        if (sourceAnnotation == null || sourceAnnotation.getArguments() == null || sourceAnnotation.getArguments().isEmpty()) {
            return Optional.empty();
        }
        return sourceAnnotation.getArguments().stream()
                .filter(a -> a instanceof J.Assignment)
                .map(a -> (J.Assignment) a)
                .filter(a -> varName.equals(a.getVariable().toString()))
                .findFirst();
    }
}
