package com.ecpnv.openrewrite.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.jdo2jpa.Constants;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utility class providing helper methods for handling and analyzing Java annotation metadata,
 * variable declarations, and related structures.
 *
 * @author Patrick Deenen & Wouter Veltmaat @ Open Circle Solutions
 */
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
        if (Optional.ofNullable(sourceAnnotations)
                .map(CollectionUtils::isEmpty)
                .orElse(Boolean.TRUE) || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        return findArgument(sourceAnnotations.iterator().next(), varName);
    }

    /**
     * Retrieves the string value of the assignment expression from the specified annotation's arguments that matches
     * the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the string value of the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<String> findArgumentValueAsString(J.Annotation sourceAnnotation, String varName) {
        return findArgument(sourceAnnotation, varName)
                .map(J.Assignment::getAssignment)
                .map(Expression::toString);
    }

    /**
     * Finds the first assignment expression within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J.Assignment> findArgument(J.Annotation sourceAnnotation, String varName) {
        if (sourceAnnotation == null || Optional.ofNullable(sourceAnnotation)
                .map(J.Annotation::getArguments)
                .map(CollectionUtils::isEmpty)
                .orElse(Boolean.TRUE)) {
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
        if (sourceType == null || Optional.ofNullable(sourceType)
                .map(JavaType.FullyQualified::getAnnotations)
                .map(CollectionUtils::isEmpty)
                .orElse(Boolean.TRUE)) {
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


    /**
     * Finds and returns a list of leading annotations in the given class declaration that match the specified
     * annotation type. The result will maintain order of the annotations as in the source code.
     *
     * @param classDecl      the class declaration to search for leading annotations. Must not be null.
     * @param annotationType the type of annotation to match. Must not be blank.
     * @return a list of matching leading annotations, or an empty list if no matching annotations are found.
     */
    public static List<J.Annotation> findLeadingAnnotations(J.ClassDeclaration classDecl, String annotationType) {
        if (classDecl == null || StringUtils.isBlank(annotationType) ||
                Optional.ofNullable(classDecl)
                        .map(J.ClassDeclaration::getLeadingAnnotations)
                        .map(CollectionUtils::isEmpty)
                        .orElse(Boolean.TRUE)) {
            return new ArrayList<>();
        }
        Pattern pattern = Pattern.compile(annotationType);
        List<J.Annotation> annotations = classDecl.getLeadingAnnotations().stream()
                .filter(annotation -> annotation.getType().isAssignableFrom(pattern))
                .toList();
        if (CollectionUtils.isEmpty(annotations)) {
            annotations = FindAnnotations.find(classDecl, annotationType).stream()
                    .filter(a -> TypeUtils.isOfClassType(a.getType(), annotationType))
                    .sorted(Comparator.comparing(a -> getOrder(classDecl.getLeadingAnnotations(), a)))
                    .toList();
        }
        return annotations;
    }

    /**
     * Computes a unique order value for the specified annotation among a list of annotations.
     * The order value is determined based on the annotation's index in the provided list and position in the
     * optional owner annotation.
     *
     * @param annotations the list of annotations to search. Can be null or empty.
     * @param annotation  the annotation whose order value needs to be determined. Must not be null.
     * @return the computed order value as a Long. Returns 0 if the annotations list is null or empty.
     */
    public static Long getOrder(List<J.Annotation> annotations, J.Annotation annotation) {
        Long order = 0l;
        if (CollectionUtils.isEmpty(annotations)) {
            return order;
        }
        var anno = annotation.toString().replace(Constants.REWRITE_ANNOTATION_PREFIX, "");
        for (int i = 0; i < annotations.size(); i++) {
            var as = annotations.get(i).toString();
            if (as.contains(anno)) {
                String index = "0000" + as.indexOf(anno);
                order = Long.valueOf(i + index.substring(index.length() - 4));
                break;
            }
        }
        return order;
    }

    /**
     * Checks if a collection of comments contains a text comment containing the text
     *
     * @param comments collection of comments. Can be empty.
     * @param text     text that may contain any text comment. Can not be null.
     * @return if comments contain any text comment containing text.
     */
    public static boolean commentsContains(final List<Comment> comments, @NonNull final String text) {
        for (Comment comment : comments) {
            if (comment instanceof TextComment textComment && textComment.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
