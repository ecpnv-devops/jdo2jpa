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
    public static Optional<J.Assignment> findArgumentAssignment(Set<J.Annotation> sourceAnnotations, String varName) {
        if (sourceAnnotations == null || sourceAnnotations.isEmpty() || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        return findArgumentAssignment(sourceAnnotations.iterator().next(), varName);
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
        return findArgumentValue(sourceAnnotation, varName)
                .map(Object::toString);
    }

    /**
     * Finds the first assignment expression within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J.Assignment> findArgumentAssignment(J.Annotation sourceAnnotation, String varName) {
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
     * Finds the first assignment expression within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<J> findArgument(J.Annotation sourceAnnotation, String varName) {
        if (sourceAnnotation == null || Optional.ofNullable(sourceAnnotation)
                .map(J.Annotation::getArguments)
                .map(CollectionUtils::isEmpty)
                .orElse(Boolean.TRUE)) {
            return Optional.empty();
        }
        return sourceAnnotation.getArguments().stream()
                .map(a -> {
                    // Test if assignment
                    if (a instanceof J.Assignment assignment) {
                        if (assignment.getVariable().toString().equals(varName)) {
                            return assignment;
                        }
                    } else
                        // Otherwise test if looking for value
                        if (varName == null || "value".equals(varName)) {
                            return (J) a;
                        }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Finds the first assignment value within the arguments of the specified annotation that matches the given variable name.
     *
     * @param sourceAnnotation the annotation whose arguments will be searched. Must not be null.
     * @param varName          the name of the variable to match within the assignment. Must not be null.
     * @return an Optional containing the matching assignment, or an empty Optional if no match is found.
     */
    public static Optional<Object> findArgumentValue(J.Annotation sourceAnnotation, String varName) {
        if (sourceAnnotation == null || Optional.ofNullable(sourceAnnotation)
                .map(J.Annotation::getArguments)
                .map(CollectionUtils::isEmpty)
                .orElse(Boolean.TRUE)) {
            return Optional.empty();
        }
        return sourceAnnotation.getArguments().stream()
                .map(a -> {
                    // Test if literal
                    if (varName == null || "value".equals(varName)) {
                        if (a instanceof J.Literal lit) {
                            return lit.getValue();
                        }
                    } else
                        // Test if assignment
                        if (a instanceof J.Assignment assignment && assignment.getVariable().toString().equals(varName)) {
                            return assignment.getAssignment();
                        }
                    return null;
                })
                .filter(Objects::nonNull)
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
    public static Optional<Boolean> findArgumentAsBoolean(J.Annotation sourceAnnotation, String varName) {
        return findArgumentValue(sourceAnnotation, varName)
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
     * Checks whether the provided multi-variable declaration contains a collection member
     * of the same type as the owner of its first variable.
     *
     * @param multiVariable the multi-variable declaration to analyze.
     * @return true if the multi-variable declaration contains a collection member
     * whose type parameter matches the owner of its first variable, false otherwise.
     */
    public static boolean hasCollectionMemberOfSameTypeAsOwner(J.VariableDeclarations multiVariable) {
        if (multiVariable == null) return false;
        return multiVariable
                .getTypeAsFullyQualified()
                .getMembers().stream()
                .map(m -> m.getType())
                .filter(t -> t.isAssignableFrom(Pattern.compile("java.util.Collection")))
                .map(t -> (JavaType.Parameterized) t)
                .anyMatch(p -> p.getTypeParameters().get(0).equals(
                        multiVariable.getVariables().get(0).getVariableType().getOwner()));
    }

    /**
     * Finds and returns a list of annotations from the provided Java type that match the specified annotation type.
     *
     * @param javaType       the Java type to search for annotations. Must not be null.
     * @param annotationType the fully qualified name of the annotation to look for. Must not be null or blank.
     * @return a list of matching annotations, or an empty list if no matching annotations are found.
     */
    public static List<JavaType.FullyQualified> findAnnotations(JavaType javaType, String annotationType) {
        if (javaType == null || StringUtils.isBlank(annotationType)) {
            return new ArrayList<>();
        }
        List<JavaType.FullyQualified> annotations = switch (javaType) {
            case JavaType.Array a -> a.getAnnotations();
            case JavaType.FullyQualified fq -> fq.getAnnotations();
            case JavaType.Method m -> m.getAnnotations();
            case JavaType.Variable v -> v.getAnnotations();
            default -> new ArrayList<>();
        };
        return annotations.stream()
                .filter(a -> a.getFullyQualifiedName().equals(annotationType))
                .toList();
    }

    /**
     * Constructs a fully qualified name for a given variable by combining its owner's fully qualified name
     * and the variable's simple name.
     *
     * @param variable the variable for which the fully qualified name is to be created. Must not be null.
     * @return a fully qualified name in the format "owner#variableName", or null if the variable's type is null.
     */
    public static String toFullyQualifiedNameWithVar(J.VariableDeclarations.NamedVariable variable) {
        if (variable.getVariableType() == null) return null;
        return variable.getVariableType().getOwner() + "#" + variable.getSimpleName();
    }

    public Optional<JavaType.FullyQualified> getParameterType(J.VariableDeclarations multiVariable, int varIndex, int paramIndex) {
        if (varIndex >= multiVariable.getVariables().size()) {
            return Optional.empty();
        }
        return Optional.of(multiVariable.getVariables().get(varIndex))
                .map(J.VariableDeclarations.NamedVariable::getVariableType)
                .map(JavaType.Variable::getType)
                .filter(jt -> jt instanceof JavaType.Parameterized)
                .map(jt -> (JavaType.Parameterized) jt)
                .map(JavaType.Parameterized::getTypeParameters)
                .filter(l -> paramIndex < l.size())
                .map(l -> l.get(paramIndex))
                // Found parameter type
                .filter(jt -> jt instanceof JavaType.FullyQualified)
                .map(jt -> (JavaType.FullyQualified) jt);
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
