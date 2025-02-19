package com.ecpnv.openrewrite.java;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.With;

/**
 * The AddOrUpdateAnnotationAttribute class is responsible for modifying attributes of annotations
 * within a source file. Depending on the operation specified, this class can add a new attribute,
 * update an existing one, or perform both actions. It also supports appending new values to arrays
 * within annotation attributes.
 * <p>
 * Note that this basically a copy of the original <code>org.openrewrite.java.AddOrUpdateAnnotationAttribute</code>
 * with the additional feature to not add and only update the value of an attribute.
 *
 * @author Original Open Rewrite authors
 * @author Patrick Deenen @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class AddOrUpdateAnnotationAttribute extends Recipe {

    public static final String ANNOTATION_ARGUMENT_TEMPLATE = "#{} = #{}";
    public static final String ANNOTATION_REGEX = "[\\s+{}\"]";
    public static final String ANNOTATION_VALUE = "value";

    public enum Operation {
        ADD, UPDATE, BOTH;

        public boolean isAdd() {
            return this == ADD || this == BOTH;
        }

        public boolean isUpdate() {
            return this == UPDATE || this == BOTH;
        }
    }

    @Override
    public String getDisplayName() {
        return "Add or update annotation attribute";
    }

    @Override
    public String getDescription() {
        return "Some annotations accept arguments. This recipe sets an existing argument to the specified value, " +
                "or adds the argument if it is not already set.";
    }

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation.",
            example = "org.junit.Test")
    String annotationType;

    @Option(displayName = "Attribute name",
            description = "The name of attribute to change. If omitted defaults to 'value'.",
            required = false,
            example = "timeout")
    @Nullable
    String attributeName;

    @Option(displayName = "Attribute value",
            description = "The value to set the attribute to. Set to `null` to remove the attribute.",
            example = "500")
    String attributeValue;

    @Option(displayName = "Old Attribute value",
            description = "The current value of the attribute, this can be used to filter where the change is applied. " +
                    "Set to `null` for wildcard behavior.",
            required = false,
            example = "400")
    String oldAttributeValue;

    @Option(displayName = "Add, update or both",
            description = "When set to `ADD` will not change existing annotation attribute values. When set to " +
                    "`UPDATE` will only update existing annotation attribute values. `BOTH` will do both.")
    Operation operation;

    @Option(displayName = "Append array",
            description = "If the attribute is an array, setting this option to `true` will append the value(s). " +
                    "In conjunction with `addOnly`, it is possible to control duplicates: " +
                    "`addOnly=true`, always append. " +
                    "`addOnly=false`, only append if the value is not already present.")
    Boolean appendArray;

    @JsonIgnore
    @Getter AddOrUpdateAnnotationAttributeVisitor addOrUpdateAnnotationAttributeVisitor;

    @JsonCreator
    public AddOrUpdateAnnotationAttribute(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("appendArray") Boolean appendArray,
            @Nullable @JsonProperty("attributeName") String attributeName,
            @NonNull @JsonProperty("attributeValue") String attributeValue,
            @Nullable @JsonProperty("oldAttributeValue") String oldAttributeValue,
            @NonNull @JsonProperty("operation") Operation operation) {
        this.annotationType = annotationType;
        this.appendArray = appendArray;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.oldAttributeValue = "null".equals(oldAttributeValue) ? null : oldAttributeValue;
        this.operation = operation;
        this.addOrUpdateAnnotationAttributeVisitor = new AddOrUpdateAnnotationAttributeVisitor();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false),
                addOrUpdateAnnotationAttributeVisitor);
    }

    public class AddOrUpdateAnnotationAttributeVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation original = super.visitAnnotation(annotation, ctx);
            if (!TypeUtils.isOfClassType(annotation.getType(), annotationType)) {
                return original;
            }

            String newAttributeValue = maybeQuoteStringArgument(attributeName, attributeValue, annotation);
            List<Expression> currentArgs = annotation.getArguments();
            if (currentArgs == null || currentArgs.isEmpty() || currentArgs.get(0) instanceof J.Empty) {
                if (newAttributeValue == null || oldAttributeValue != null) {
                    return annotation;
                }

                J.Annotation result = null;
                if (attributeName == null || ANNOTATION_VALUE.equals(attributeName)) {
                    result = JavaTemplate.builder("#{}")
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), annotation.getCoordinates().replaceArguments(), newAttributeValue);
                } else {
                    String newAttributeValueResult = newAttributeValue;
                    if (((JavaType.FullyQualified) requireNonNull(annotation.getAnnotationType().getType())).getMethods().stream().anyMatch(method -> method.getReturnType().toString().equals("java.lang.String[]"))) {
                        String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll(ANNOTATION_REGEX, "");
                        List<String> attributeList = Arrays.asList(attributeValueCleanedUp.contains(",") ? attributeValueCleanedUp.split(",") : new String[]{attributeValueCleanedUp});
                        newAttributeValueResult = attributeList.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining("\", \"", "{\"", "\"}"));
                    }
                    result = JavaTemplate.builder(ANNOTATION_ARGUMENT_TEMPLATE)
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), annotation.getCoordinates().replaceArguments(), attributeName, newAttributeValueResult);
                }
                if (getCursor().getParentTreeCursor().getParent() != null) {
                    result = maybeAutoFormat(original, result, ctx);
                }
                return result;
            } else {
                // First assume the value exists amongst the arguments and attempt to update it
                AtomicBoolean foundOrSetAttributeWithDesiredValue = new AtomicBoolean(false);
                final J.Annotation finalA = annotation;
                List<Expression> newArgs = ListUtils.map(currentArgs, expression -> {
                    if (expression instanceof J.Assignment assignment) {
                        J.Identifier variable = (J.Identifier) assignment.getVariable();
                        if (attributeName == null && !ANNOTATION_VALUE.equals(variable.getSimpleName())) {
                            return expression;
                        }
                        if (attributeName != null && !attributeName.equals(variable.getSimpleName())) {
                            return expression;
                        }

                        foundOrSetAttributeWithDesiredValue.set(true);

                        if (newAttributeValue == null) {
                            return null;
                        }

                        if (assignment.getAssignment() instanceof J.NewArray newArray) {
                            List<Expression> jLiteralList = requireNonNull(newArray.getInitializer());
                            String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll(ANNOTATION_REGEX, "");
                            List<String> attributeList = Arrays.asList(attributeValueCleanedUp.contains(",") ? attributeValueCleanedUp.split(",") : new String[]{attributeValueCleanedUp});

                            if (assignment.getMarkers().findFirst(AlreadyAppended.class).filter(ap -> ap.getValues().equals(newAttributeValue)).isPresent()) {
                                return assignment;
                            }

                            if (Boolean.TRUE.equals(appendArray)) {
                                boolean changed = false;
                                for (String attrListValues : attributeList) {
                                    String newAttributeListValue = maybeQuoteStringArgument(attributeName, attrListValues, finalA);
                                    if (!operation.isAdd() && attributeValIsAlreadyPresent(jLiteralList, newAttributeListValue)) {
                                        continue;
                                    }
                                    if (oldAttributeValue != null && !oldAttributeValue.equals(attrListValues)) {
                                        continue;
                                    }
                                    changed = true;
                                    Expression e = requireNonNull(((J.Annotation) JavaTemplate.builder(newAttributeListValue)
                                            .contextSensitive()
                                            .build()
                                            .apply(getCursor(), finalA.getCoordinates().replaceArguments()))
                                            .getArguments()).get(0);
                                    jLiteralList.add(e);
                                }
                                return changed ? assignment.withAssignment(((J.NewArray) assignment.getAssignment()).withInitializer(jLiteralList))
                                        .withMarkers(assignment.getMarkers().add(new AlreadyAppended(randomId(), newAttributeValue))) : assignment;
                            }
                            int m = 0;
                            for (int i = 0; i < requireNonNull(jLiteralList).size(); i++) {
                                if (i >= attributeList.size()) {
                                    jLiteralList.remove(i);
                                    i--;
                                    continue;
                                }

                                String newAttributeListValue = maybeQuoteStringArgument(attributeName, attributeList.get(i), finalA);
                                if (jLiteralList.size() == i + 1) {
                                    m = i + 1;
                                }

                                if (newAttributeListValue.equals(((J.Literal) jLiteralList.get(i)).getValueSource()) || operation.isAdd()) {
                                    continue;
                                }
                                if (oldAttributeValue != null && !oldAttributeValue.equals(attributeList.get(i))) {
                                    continue;
                                }

                                jLiteralList.set(i, ((J.Literal) jLiteralList.get(i)).withValue(newAttributeListValue).withValueSource(newAttributeListValue).withPrefix(jLiteralList.get(i).getPrefix()));
                            }
                            if (jLiteralList.size() < attributeList.size() || operation.isAdd()) {
                                if (operation.isAdd()) {
                                    m = 0;
                                }
                                for (int j = m; j < attributeList.size(); j++) {
                                    String newAttributeListValue = maybeQuoteStringArgument(attributeName, attributeList.get(j), finalA);
                                    jLiteralList.add(j, new J.Literal(randomId(), jLiteralList.get(j - 1).getPrefix(), Markers.EMPTY, newAttributeListValue, newAttributeListValue, null, JavaType.Primitive.String));
                                }
                            }

                            return assignment.withAssignment(((J.NewArray) assignment.getAssignment()).withInitializer(jLiteralList));
                        } else if (assignment.getAssignment() instanceof J.FieldAccess fieldAccess) {
                            String fullQn = assignment.getAssignment().getType() + "." + fieldAccess.getName();
                            if (newAttributeValue.equals(fullQn) || newAttributeValue.equals(assignment.getAssignment().toString())
                                    || (operation.isAdd() && !operation.isUpdate())) {
                                return expression;
                            }
                            if (!valueMatches(assignment.getAssignment(), oldAttributeValue)) {
                                return expression;
                            }
                            return ((J.Annotation) JavaTemplate.builder(ANNOTATION_ARGUMENT_TEMPLATE)
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), finalA.getCoordinates().replaceArguments(),
                                            ((J.Identifier) assignment.getVariable()).getSimpleName(), newAttributeValue)
                            ).getArguments().get(0);
                        } else {
                            J.Literal value = (J.Literal) assignment.getAssignment();
                            if (newAttributeValue.equals(value.getValueSource()) || (operation.isAdd() && !operation.isUpdate())) {
                                return expression;
                            }
                            if (!valueMatches(value, oldAttributeValue)) {
                                return expression;
                            }
                            return assignment.withAssignment(value.withValue(newAttributeValue).withValueSource(newAttributeValue));
                        }
                    } else if (expression instanceof J.Literal literal) {
                        // The only way anything except an assignment can appear is if there's an implicit assignment to "value"
                        if (attributeName == null || ANNOTATION_VALUE.equals(attributeName)) {
                            foundOrSetAttributeWithDesiredValue.set(true);
                            if (newAttributeValue == null) {
                                return null;
                            }
                            if (newAttributeValue.equals(literal.getValueSource()) || (operation.isAdd() && !operation.isUpdate())) {
                                return expression;
                            }
                            if (!valueMatches(literal, oldAttributeValue)) {
                                return expression;
                            }
                            return ((J.Literal) expression).withValue(newAttributeValue).withValueSource(newAttributeValue);
                        } else {
                            // Make the attribute name explicit, before we add the new value below
                            //noinspection ConstantConditions
                            return ((J.Annotation) JavaTemplate.builder("value = #{}")
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), finalA.getCoordinates().replaceArguments(), expression)
                            ).getArguments().get(0);
                        }
                    } else if (expression instanceof J.FieldAccess fieldAccess) {
                        // The only way anything except an assignment can appear is if there's an implicit assignment to "value"
                        if (attributeName == null || ANNOTATION_VALUE.equals(attributeName)) {
                            foundOrSetAttributeWithDesiredValue.set(true);
                            if (!valueMatches(expression, oldAttributeValue)) {
                                return expression;
                            }
                            if (newAttributeValue == null) {
                                return null;
                            }
                            if (newAttributeValue.equals(fieldAccess.toString()) || (operation.isAdd() && !operation.isUpdate())) {
                                return expression;
                            }
                            //noinspection ConstantConditions
                            return ((J.Annotation) JavaTemplate.apply(newAttributeValue, getCursor(), finalA.getCoordinates().replaceArguments()))
                                    .getArguments().get(0);
                        } else {
                            // Make the attribute name explicit, before we add the new value below
                            //noinspection ConstantConditions
                            return ((J.Annotation) JavaTemplate.builder("value = #{any()}")
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), finalA.getCoordinates().replaceArguments(), expression))
                                    .getArguments().get(0);
                        }
                    } else if (expression instanceof J.NewArray newArray) {
                        if (expression.getMarkers().findFirst(AlreadyAppended.class).filter(ap -> ap.getValues().equals(newAttributeValue)).isPresent()) {
                            return expression;
                        }

                        if (newAttributeValue == null) {
                            return null;
                        }

                        List<Expression> jLiteralList = requireNonNull(newArray.getInitializer());
                        String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll(ANNOTATION_REGEX, "");
                        List<String> attributeList = Arrays.asList(attributeValueCleanedUp.contains(",") ? attributeValueCleanedUp.split(",") : new String[]{attributeValueCleanedUp});

                        if (Boolean.TRUE.equals(appendArray)) {
                            boolean changed = false;
                            for (String attrListValues : attributeList) {
                                String newAttributeListValue = maybeQuoteStringArgument(attributeName, attrListValues, finalA);
                                if (!operation.isAdd() && attributeValIsAlreadyPresent(jLiteralList, newAttributeListValue)) {
                                    continue;
                                }
                                changed = true;

                                Expression e = requireNonNull(((J.Annotation) JavaTemplate.builder(newAttributeListValue)
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), finalA.getCoordinates().replaceArguments()))
                                        .getArguments()).get(0);
                                jLiteralList.add(e);
                            }
                            if (oldAttributeValue != null) { // remove old value from array
                                jLiteralList = ListUtils.map(jLiteralList, val -> valueMatches(val, oldAttributeValue) ? null : val);
                            }

                            return changed ? newArray.withInitializer(jLiteralList)
                                    .withMarkers(expression.getMarkers().add(new AlreadyAppended(randomId(), newAttributeValue))) : expression;
                        }
                        int m = 0;
                        for (int i = 0; i < requireNonNull(jLiteralList).size(); i++) {
                            if (i >= attributeList.size()) {
                                jLiteralList.remove(i);
                                i--;
                                continue;
                            }

                            String newAttributeListValue = maybeQuoteStringArgument(attributeName, attributeList.get(i), finalA);
                            if (jLiteralList.size() == i + 1) {
                                m = i + 1;
                            }

                            if (newAttributeListValue.equals(((J.Literal) jLiteralList.get(i)).getValueSource()) || (operation.isAdd() && !operation.isUpdate())) {
                                continue;
                            }
                            if (oldAttributeValue != null && !oldAttributeValue.equals(newAttributeListValue)) {
                                continue;
                            }

                            jLiteralList.set(i, ((J.Literal) jLiteralList.get(i)).withValue(newAttributeListValue).withValueSource(newAttributeListValue).withPrefix(jLiteralList.get(i).getPrefix()));
                        }
                        if (jLiteralList.size() < attributeList.size() || operation.isAdd()) {
                            if (operation.isAdd()) {
                                m = 0;
                            }
                            for (int j = m; j < attributeList.size(); j++) {
                                String newAttributeListValue = maybeQuoteStringArgument(attributeName, attributeList.get(j), finalA);

                                Expression e = requireNonNull(((J.Annotation) JavaTemplate.builder(newAttributeListValue)
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), finalA.getCoordinates().replaceArguments()))
                                        .getArguments()).get(0);
                                jLiteralList.add(j, e);
                            }
                        }

                        return newArray.withInitializer(jLiteralList);
                    }
                    return expression;
                });

                if (newArgs != currentArgs) {
                    annotation = annotation.withArguments(newArgs);
                }
                if (operation.isAdd() && !foundOrSetAttributeWithDesiredValue.get()
                        && !attributeValIsAlreadyPresent(newArgs, newAttributeValue)) {
                    // There was no existing value to update, so add a new value into the argument list
                    String effectiveName = (attributeName == null) ? ANNOTATION_VALUE : attributeName;
                    //noinspection ConstantConditions
                    J.Assignment as = (J.Assignment) ((J.Annotation) JavaTemplate.builder(ANNOTATION_ARGUMENT_TEMPLATE)
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), annotation.getCoordinates().replaceArguments(), effectiveName, newAttributeValue))
                            .getArguments().get(0);
                    annotation = annotation.withArguments(ListUtils.concat(as, annotation.getArguments()));
                }
                if (foundOrSetAttributeWithDesiredValue.get() && StringUtils.isNotBlank(attributeName)) {
                    // The attribute that is replaced might refer to an attributeType which might not be needed anymore
                    RewriteUtils.findArgument(original, attributeName)
                            .map(J.Assignment::getType)
                            .map(JavaType::toString)
                            .filter(name -> StringUtils.isNotBlank(name) && !"Unknown".equals(name))
                            .ifPresent(this::maybeRemoveImport);
                }
            }
            if (getCursor().getParentTreeCursor().getParent() != null) {
                annotation = maybeAutoFormat(original, annotation, ctx);
            }
            return annotation;
        }
    }

    private static boolean valueMatches(@Nullable Expression expression, @Nullable String oldAttributeValue) {
        if (expression == null) {
            return oldAttributeValue == null;
        }
        if (oldAttributeValue == null) { // null means wildcard
            return true;
        } else if (expression instanceof J.Literal literal) {
            return oldAttributeValue.equals(literal.getValue());
        } else if (expression instanceof J.FieldAccess fieldAccess) {
            String currentValue = fieldAccess.toString();
            if (fieldAccess.getTarget() instanceof J.Identifier identifier) {
                currentValue = identifier.getSimpleName() + "." + fieldAccess.getSimpleName();
            }
            return oldAttributeValue.equals(currentValue);
        } else if (expression instanceof J.Identifier identifier) { // class names, static variables ..
            if (oldAttributeValue.endsWith(".class")) {
                String className = TypeUtils.toString(requireNonNull(expression.getType())) + ".class";
                return className.endsWith(oldAttributeValue);
            } else {
                return oldAttributeValue.equals(identifier.getSimpleName());
            }
        } else {
            throw new IllegalArgumentException("Unexpected expression type: " + expression.getClass());
        }
    }

    @Contract("_, null, _ -> null; _, !null, _ -> !null")
    private static @Nullable String maybeQuoteStringArgument(@Nullable String attributeName, @Nullable String attributeValue, J.Annotation annotation) {
        if ((attributeValue != null) && attributeIsString(attributeName, annotation)) {
            return "\"" + attributeValue + "\"";
        } else {
            return attributeValue;
        }
    }

    private static boolean attributeIsString(@Nullable String attributeName, J.Annotation annotation) {
        String actualAttributeName = (attributeName == null) ? ANNOTATION_VALUE : attributeName;
        JavaType.Class annotationType = (JavaType.Class) annotation.getType();
        if (annotationType != null) {
            for (JavaType.Method m : annotationType.getMethods()) {
                if (m.getName().equals(actualAttributeName)) {
                    return TypeUtils.isOfClassType(m.getReturnType(), "java.lang.String");
                }
            }
        }
        return false;
    }

    private static boolean attributeValIsAlreadyPresent(@Nullable List<Expression> expression, @Nullable String attributeValue) {
        if (expression == null) {
            return attributeValue == null;
        }
        for (Expression e : expression) {
            if (e instanceof J.Literal literal &&
                    Objects.equals(literal.getValueSource(), attributeValue)) {
                return true;
            }
            if (e instanceof J.NewArray newArray) {
                return attributeValIsAlreadyPresent(newArray.getInitializer(), attributeValue);
            }
        }
        return false;
    }

    @Value
    @With
    private static class AlreadyAppended implements Marker {
        UUID id;
        String values;
    }
}

