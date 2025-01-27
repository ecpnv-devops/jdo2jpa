package com.ecpnv.openrewrite.util;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

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
    public static Optional<J.Assignment> findArgument(Set<J.Annotation> sourceAnnotations, String varName) {
        if (sourceAnnotations == null || sourceAnnotations.isEmpty() || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        return findArgument(sourceAnnotations.iterator().next(), varName);
    }

    /**
     * Finds the first assignment expression within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J.Assignment> findArgument(J.Annotation sourceAnnotation, String varName) {
        if (sourceAnnotation == null || sourceAnnotation.getArguments() == null || sourceAnnotation.getArguments().isEmpty()) {
            return Optional.empty();
        }
        return sourceAnnotation.getArguments().stream()
                .filter(a -> a instanceof J.Assignment)
                .map(a -> (J.Assignment) a)
                .filter(a -> varName.equals(a.getVariable().toString()))
                .findFirst();
    }

    /**
     * Retrieves a boolean value from the assignment expression of the specified annotation's arguments
     * that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the boolean value of the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<Boolean> findBooleanArgument(J.Annotation sourceAnnotation, String varName) {
        return findArgument(sourceAnnotation, varName)
                .map(J.Assignment::getAssignment)
                .map(Object::toString)
                .map(Boolean::parseBoolean);
    }

    /**
     * Checks if the specified source type contains an annotation with the given fully qualified name.
     *
     * @param sourceType     the type to check for the presence of the annotation. Must not be null.
     * @param annotationName the fully qualified name of the annotation to look for. Must not be null.
     * @return true if the source type contains the specified annotation, false otherwise.
     */
    public static boolean hasAnnotation(JavaType.FullyQualified sourceType, String annotationName) {
        if (sourceType == null || sourceType.getAnnotations().isEmpty()) {
            return false;
        }
        return sourceType.getAnnotations().stream()
                .anyMatch(a -> a.getFullyQualifiedName().equals(annotationName));
    }

    /**
     * Checks if any variable in the provided multi-variable declaration belongs to a method as its owner.
     *
     * @param multiVariable the multi-variable declaration to check. Must not be null.
     * @return true if any variable's owner is a method, false otherwise.
     */
    public static boolean isMethodOwnerOfVar(J.VariableDeclarations multiVariable) {
        if (multiVariable == null)
            return false;
        return multiVariable.getVariables().stream()
                .filter(namedVariable -> namedVariable.getVariableType() != null)
                .map(namedVariable -> namedVariable.getVariableType().getOwner())
                .filter(Objects::nonNull)
                .anyMatch(owner -> owner instanceof JavaType.Method);
    }
}
