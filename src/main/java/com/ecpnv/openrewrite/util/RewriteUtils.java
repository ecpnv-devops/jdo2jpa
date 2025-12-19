package com.ecpnv.openrewrite.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.openrewrite.Cursor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;

import com.ecpnv.openrewrite.jdo2jpa.Constants;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utility class providing helper methods for handling and analyzing Java annotation metadata,
 * variable declarations, and related structures.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@UtilityClass
public class RewriteUtils {

    /**
     * Finds and returns the parent class declaration from the given cursor.
     * This method traverses up the cursor's tree until it finds a node representing
     * a class declaration.
     *
     * @param cursor the cursor from which to start the search for a parent class declaration
     * @return the parent class declaration if found, or null if no class declaration is found
     */
    public static J.ClassDeclaration findParentClass(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        if (cursor.getValue() instanceof J.ClassDeclaration classDeclaration) {
            return classDeclaration;
        }
        return findParentClass(cursor.getParentTreeCursor());
    }

    /**
     * Recursively finds the nearest parent of type {@code J.VariableDeclarations} from the given cursor.
     *
     * @param cursor the current {@code Cursor} instance used to traverse the abstract syntax tree.
     * @return the nearest {@code J.VariableDeclarations} instance found in the parent chain, or {@code null} if not found.
     */
    public static J.VariableDeclarations findParentVar(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        if (cursor.getValue() instanceof J.ClassDeclaration classDeclaration) {
            return null;
        }
        if (cursor.getValue() instanceof J.VariableDeclarations varDeclarations) {
            return varDeclarations;
        }
        return findParentVar(cursor.getParentTreeCursor());
    }

    /**
     * Finds the closest parent variable declaration of a specific type from the given cursor.
     *
     * @param cursor the cursor representing the current position in the syntax tree
     * @param type   the fully qualified name of the type to match against the parent variable declaration
     * @return the parent variable declaration of the specified type if found, otherwise null
     */
    public static J.VariableDeclarations findParentVarOfType(Cursor cursor, String type) {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        var parentVar = findParentVar(cursor);
        if (parentVar != null) {
            if (parentVar.getTypeAsFullyQualified() != null &&
                    parentVar.getTypeAsFullyQualified().getFullyQualifiedName().equals(type)) {
                return parentVar;
            } else if (parentVar.getTypeExpression() != null && type.equals(parentVar.getTypeExpression().toString())) {
                return parentVar;
            }
        }
        return null;
    }

    /**
     * Finds the first argument assignment for a specific variable name within a set of annotations.
     *
     * @param sourceAnnotations the set of annotations to search for the variable assignment. Must not be null or empty.
     * @param varName           the name of the variable to search for in the annotations. Must not be null or blank.
     * @return an Optional containing the found assignment if present, or an empty Optional if no assignment is found.
     */
    public static Optional<J.Assignment> findArgumentAssignment(Set<J.Annotation> sourceAnnotations, String varName) {
        if (sourceAnnotations == null || sourceAnnotations.isEmpty() || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        return sourceAnnotations.stream()
                .map(a -> findArgumentAssignment(a, varName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
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
                        if (assignment.getVariable().toString().equals(varName)
                                || ((varName == null || "null".equals(varName)) && "value".equals(assignment.getVariable().toString()))) {
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
        return findArgument(sourceAnnotation, varName)
                .map(a -> {
                    // Test if literal
                    if (a instanceof J.Literal lit) {
                        return lit.getValue();
                    } else
                        // Test if assignment
                        if (a instanceof J.Assignment assignment) {
                            return assignment.getAssignment();
                        }
                    return null;
                });
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

    public static boolean hasAnnotation(List<J.Annotation> annotations, String annotationPattern, Cursor cursor) {
        if (CollectionUtils.isEmpty(annotations)) {
            return false;
        }
        for (J.Annotation annotation : annotations) {
            if (annotation.getType() instanceof JavaType.FullyQualified fullyQualified &&
                    Objects.equals(annotationPattern, fullyQualified.getFullyQualifiedName())) {
                return true;
            }
            if ((annotation.getAnnotationType() instanceof J.FieldAccess fieldAccess &&
                    Objects.equals(annotationPattern, fieldAccess.print(cursor)))
                    || (annotation.getAnnotationType() instanceof J.Identifier identifier &&
                    Objects.equals(JavaType.ShallowClass.build(annotationPattern).getClassName(), identifier.print(cursor)))) {
                return true;
            }
        }
        return false;
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
     * Finds and returns a list of leading annotations in the given java declaration that match the specified
     * annotation type. The result will maintain order of the annotations as in the source code.
     *
     * @param javaElement    the java declaration to search for leading annotations. Must not be null.
     * @param annotationType the type of annotation to match. Must not be blank.
     * @param subAnnotations should annatations in annotations be included? True=yes
     * @return a list of matching leading annotations, or an empty list if no matching annotations are found.
     */
    public static List<J.Annotation> findLeadingAnnotations(
            J javaElement, String annotationType, boolean subAnnotations) {
        List<J.Annotation> leadingAnnotations = getLeadingAnnotations(javaElement);
        if (javaElement == null || StringUtils.isBlank(annotationType) || leadingAnnotations.isEmpty()) {
            return new ArrayList<>();
        }
        Pattern pattern = Pattern.compile(annotationType);
        var at = annotationType.startsWith("@") ? annotationType.substring(1) : annotationType;
        List<J.Annotation> annotations = Stream.concat(
                // Find all root annotations on class
                leadingAnnotations.stream()
                        .filter(annotation -> annotation.getType().isAssignableFrom(pattern)
                                || at.equals(annotation.getAnnotationType().toString())),
                // Should we search for sub-annotations?
                subAnnotations ? leadingAnnotations.stream()
                        .filter(a -> !a.getType().isAssignableFrom(pattern))
                        .filter(a -> a.getArguments() != null && !a.getArguments().isEmpty())
                        .flatMap(a -> a.getArguments().stream())
                        .filter(a -> a instanceof J.Annotation)
                        .filter(a -> TypeUtils.isOfClassType(a.getType(), annotationType))
                        .map(a -> (J.Annotation) a)
                        // Otherwise skip sub-annotations
                        : Stream.empty()
        ).toList();
        if (CollectionUtils.isEmpty(annotations)) {
            annotations = FindAnnotations.find(javaElement, annotationType).stream()
                    .filter(a -> TypeUtils.isOfClassType(a.getType(), at))
                    .sorted(Comparator.comparing(a -> getOrder(leadingAnnotations, a)))
                    .toList();
        }
        return annotations;
    }

    public static List<J.Annotation> findLeadingAnnotations(J javaElement, String annotationType) {
        return findLeadingAnnotations(javaElement, annotationType, false);
    }

    public static List<J.Annotation> getLeadingAnnotations(J javaElement) {
        return switch (javaElement) {
            case J.ClassDeclaration cd -> cd.getLeadingAnnotations();
            case J.MethodDeclaration md -> md.getLeadingAnnotations();
            case J.VariableDeclarations vd -> vd.getLeadingAnnotations();
            default -> new ArrayList<>();
        };
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

    /**
     * Retrieves the fully qualified name of the owner of the first variable
     * in the given variable declarations. If the owner is not resolvable or not
     * present, an empty Optional is returned.
     *
     * @param mv the variable declarations containing multiple variables. Can be null.
     * @return an Optional containing the fully qualified name of the owner of the first
     * variable, or an empty Optional if no owner can be resolved.
     */
    public static Optional<String> ownerOfFirstVarToFullyQualifiedName(J.VariableDeclarations mv) {
        return Optional.ofNullable(mv).stream()
                .map(J.VariableDeclarations::getVariables)
                .flatMap(Collection::stream)
                .map(J.VariableDeclarations.NamedVariable::getVariableType)
                .filter(Objects::nonNull)
                .map(JavaType.Variable::getOwner)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst();
    }

    /**
     * Retrieves the parameter type of a variable declaration at a specified variable index and parameter index.
     *
     * @param multiVariable the variable declarations containing multiple variables. Must not be null.
     * @param varIndex      the index of the variable within the variable declarations. Must be a non-negative integer.
     * @param paramIndex    the index of the parameter within the type parameters of the variable's type. Must be a non-negative integer.
     * @return an Optional containing the fully qualified parameter type if found, or an empty Optional if the parameter type cannot be determined.
     */
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

    /**
     * Creates an import statement for the specified fully qualified class name.
     *
     * @param fullClassName the fully qualified name of the class to be imported. Must not be null or blank.
     * @return a {@link J.Import} object representing the import statement for the specified class.
     */
    public static J.Import createImport(String fullClassName) {
        return new J.Import(randomId(),
                Space.format("\n"),
                Markers.EMPTY,
                new JLeftPadded<>(Space.SINGLE_SPACE, Boolean.FALSE, Markers.EMPTY),
                TypeTree.build(fullClassName).withPrefix(Space.SINGLE_SPACE),
                null);
    }

    /**
     * Sanitizes the given string by replacing periods with underscores if the string contains a period.
     * If the input string is blank, it returns null.
     *
     * @param string the input string to be sanitized. Can be null or blank.
     * @return a sanitized string.
     */
    public static String sanitizeTableName(String string) {
        if (StringUtils.isBlank(string)) {
            return null;
        } else if (string.contains(".")) {
            return string.replace(".", "_");
        }
        return string;
    }

    /**
     * Adds double quotes around a string if it is not already quoted with either single
     * or double quotes. If the input is already quoted, it is returned as is.
     *
     * @param unquotedString The input string to potentially quote. It can be null.
     * @return A string wrapped in double quotes if it was not already quoted, or the
     * original string if it was either null or already quoted.
     */
    public String maybeQuoteString(String unquotedString) {
        if (unquotedString != null && !unquotedString.startsWith("'") && !unquotedString.endsWith("'")
                && !unquotedString.startsWith("\"") && !unquotedString.endsWith("\"")) {
            return "\"" + unquotedString + "\"";
        }
        return unquotedString;
    }

    /**
     * Removes surrounding quotes from a given string if present.
     * Supported quotes are single quotes ('') and double quotes ("").
     *
     * @param quotedString the string which may have surrounding single or double quotes
     * @return the unquoted string if quotes were present, otherwise returns the original string
     */
    public String maybeUnquoteString(String quotedString) {
        if (quotedString != null && (quotedString.startsWith("'") && quotedString.endsWith("'")
                || quotedString.startsWith("\"") && quotedString.endsWith("\""))) {
            return quotedString.substring(1, quotedString.length() - 1);
        }
        return quotedString;
    }

    /**
     * Determines if the given variable declarations contain all specified modifiers.
     *
     * @param multiVariable the variable declarations to check for the specified modifiers
     * @param modifiers     the modifiers to be checked within the variable declarations
     * @return {@code true} if all specified modifiers are present in the variable declarations; otherwise {@code false}
     */
    public boolean hasModifiers(J.VariableDeclarations multiVariable, J.Modifier.Type... modifiers) {
        if (modifiers == null || modifiers.length == 0) {
            return false;
        }
        return Arrays.stream(modifiers)
                .allMatch(m -> multiVariable.getModifiers().stream()
                        .anyMatch(mm -> mm.getType() == m));
    }
}
