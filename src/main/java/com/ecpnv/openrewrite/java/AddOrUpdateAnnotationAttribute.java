package com.ecpnv.openrewrite.java;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

import lombok.EqualsAndHashCode;
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
 * @author Open Rewrite
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddOrUpdateAnnotationAttribute extends Recipe {

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

    @JsonCreator
    public AddOrUpdateAnnotationAttribute(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("appendArray") Boolean appendArray,
            @Nullable @JsonProperty("attributeName") String attributeName,
            @NonNull @JsonProperty("attributeValue") String attributeValue,
            @NonNull @JsonProperty("oldAttributeValue") String oldAttributeValue,
            @NonNull @JsonProperty("operation") Operation operation) {
        this.annotationType = annotationType;
        this.appendArray = appendArray;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.oldAttributeValue = oldAttributeValue;
        this.operation = operation;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation a, ExecutionContext ctx) {
                J.Annotation original = super.visitAnnotation(a, ctx);
                if (!TypeUtils.isOfClassType(a.getType(), annotationType)) {
                    return original;
                }

                String newAttributeValue = maybeQuoteStringArgument(attributeName, attributeValue, a);
                List<Expression> currentArgs = a.getArguments();
                if (currentArgs == null || currentArgs.isEmpty() || currentArgs.get(0) instanceof J.Empty) {
                    if (newAttributeValue == null || oldAttributeValue != null) {
                        return a;
                    }

                    if (attributeName == null || "value".equals(attributeName)) {
                        return JavaTemplate.builder("#{}")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), a.getCoordinates().replaceArguments(), newAttributeValue);
                    } else {
                        String newAttributeValueResult = newAttributeValue;
                        if (((JavaType.FullyQualified) requireNonNull(a.getAnnotationType().getType())).getMethods().stream().anyMatch(method -> method.getReturnType().toString().equals("java.lang.String[]"))) {
                            String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll("[\\s+{}\"]", "");
                            List<String> attributeList = Arrays.asList(attributeValueCleanedUp.contains(",") ? attributeValueCleanedUp.split(",") : new String[]{attributeValueCleanedUp});
                            newAttributeValueResult = attributeList.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining("\", \"", "{\"", "\"}"));
                        }
                        return JavaTemplate.builder("#{} = #{}")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), a.getCoordinates().replaceArguments(), attributeName, newAttributeValueResult);
                    }
                } else {
                    // First assume the value exists amongst the arguments and attempt to update it
                    AtomicBoolean foundOrSetAttributeWithDesiredValue = new AtomicBoolean(false);
                    final J.Annotation finalA = a;
                    List<Expression> newArgs = ListUtils.map(currentArgs, it -> {
                        if (it instanceof J.Assignment) {
                            J.Assignment as = (J.Assignment) it;
                            J.Identifier var = (J.Identifier) as.getVariable();
                            if (attributeName == null && !"value".equals(var.getSimpleName())) {
                                return it;
                            }
                            if (attributeName != null && !attributeName.equals(var.getSimpleName())) {
                                return it;
                            }

                            foundOrSetAttributeWithDesiredValue.set(true);

                            if (newAttributeValue == null) {
                                return null;
                            }

                            if (as.getAssignment() instanceof J.NewArray) {
                                List<Expression> jLiteralList = requireNonNull(((J.NewArray) as.getAssignment()).getInitializer());
                                String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll("[\\s+{}\"]", "");
                                List<String> attributeList = Arrays.asList(attributeValueCleanedUp.contains(",") ? attributeValueCleanedUp.split(",") : new String[]{attributeValueCleanedUp});

                                if (as.getMarkers().findFirst(AlreadyAppended.class).filter(ap -> ap.getValues().equals(newAttributeValue)).isPresent()) {
                                    return as;
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
                                    return changed ? as.withAssignment(((J.NewArray) as.getAssignment()).withInitializer(jLiteralList))
                                            .withMarkers(as.getMarkers().add(new AlreadyAppended(randomId(), newAttributeValue))) : as;
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

                                return as.withAssignment(((J.NewArray) as.getAssignment()).withInitializer(jLiteralList));
                            } else {
                                J.Literal value = (J.Literal) as.getAssignment();
                                if (newAttributeValue.equals(value.getValueSource()) || operation.isAdd()) {
                                    return it;
                                }
                                if (!valueMatches(value, oldAttributeValue)) {
                                    return it;
                                }
                                return as.withAssignment(value.withValue(newAttributeValue).withValueSource(newAttributeValue));
                            }
                        } else if (it instanceof J.Literal) {
                            // The only way anything except an assignment can appear is if there's an implicit assignment to "value"
                            if (attributeName == null || "value".equals(attributeName)) {
                                foundOrSetAttributeWithDesiredValue.set(true);
                                if (newAttributeValue == null) {
                                    return null;
                                }
                                J.Literal value = (J.Literal) it;
                                if (newAttributeValue.equals(value.getValueSource()) || operation.isAdd()) {
                                    return it;
                                }
                                if (!valueMatches(value, oldAttributeValue)) {
                                    return it;
                                }
                                return ((J.Literal) it).withValue(newAttributeValue).withValueSource(newAttributeValue);
                            } else {
                                // Make the attribute name explicit, before we add the new value below
                                //noinspection ConstantConditions
                                return ((J.Annotation) JavaTemplate.builder("value = #{}")
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), finalA.getCoordinates().replaceArguments(), it)
                                ).getArguments().get(0);
                            }
                        } else if (it instanceof J.FieldAccess) {
                            // The only way anything except an assignment can appear is if there's an implicit assignment to "value"
                            if (attributeName == null || "value".equals(attributeName)) {
                                foundOrSetAttributeWithDesiredValue.set(true);
                                if (!valueMatches(it, oldAttributeValue)) {
                                    return it;
                                }
                                if (newAttributeValue == null) {
                                    return null;
                                }
                                J.FieldAccess value = (J.FieldAccess) it;
                                if (newAttributeValue.equals(value.toString()) || operation.isAdd()) {
                                    return it;
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
                                        .apply(getCursor(), finalA.getCoordinates().replaceArguments(), it))
                                        .getArguments().get(0);
                            }
                        } else if (it instanceof J.NewArray) {
                            if (it.getMarkers().findFirst(AlreadyAppended.class).filter(ap -> ap.getValues().equals(newAttributeValue)).isPresent()) {
                                return it;
                            }

                            if (newAttributeValue == null) {
                                return null;
                            }

                            J.NewArray arrayValue = (J.NewArray) it;
                            List<Expression> jLiteralList = requireNonNull(arrayValue.getInitializer());
                            String attributeValueCleanedUp = attributeValue.replaceAll("\\s+", "").replaceAll("[\\s+{}\"]", "");
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

                                return changed ? arrayValue.withInitializer(jLiteralList)
                                        .withMarkers(it.getMarkers().add(new AlreadyAppended(randomId(), newAttributeValue))) : it;
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

                            return arrayValue.withInitializer(jLiteralList);
                        }
                        return it;
                    });

                    if (newArgs != currentArgs) {
                        a = a.withArguments(newArgs);
                    }
                    if (operation.isAdd() && !foundOrSetAttributeWithDesiredValue.get()
                            && !attributeValIsAlreadyPresent(newArgs, newAttributeValue)) {
                        // There was no existing value to update, so add a new value into the argument list
                        String effectiveName = (attributeName == null) ? "value" : attributeName;
                        //noinspection ConstantConditions
                        J.Assignment as = (J.Assignment) ((J.Annotation) JavaTemplate.builder("#{} = #{}")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), a.getCoordinates().replaceArguments(), effectiveName, newAttributeValue))
                                .getArguments().get(0);
                        a = a.withArguments(ListUtils.concat(as, a.getArguments()));
                    }
                }
                a = maybeAutoFormat(original, a, ctx);
                return a;
            }
        });
    }

    private static boolean valueMatches(@Nullable Expression expression, @Nullable String oldAttributeValue) {
        if (expression == null) {
            return oldAttributeValue == null;
        }
        if (oldAttributeValue == null) { // null means wildcard
            return true;
        } else if (expression instanceof J.Literal) {
            return oldAttributeValue.equals(((J.Literal) expression).getValue());
        } else if (expression instanceof J.FieldAccess) {
            J.FieldAccess fa = (J.FieldAccess) expression;
            String currentValue = ((J.Identifier) fa.getTarget()).getSimpleName() + "." + fa.getSimpleName();
            return oldAttributeValue.equals(currentValue);
        } else if (expression instanceof J.Identifier) { // class names, static variables ..
            if (oldAttributeValue.endsWith(".class")) {
                String className = TypeUtils.toString(requireNonNull(expression.getType())) + ".class";
                return className.endsWith(oldAttributeValue);
            } else {
                return oldAttributeValue.equals(((J.Identifier) expression).getSimpleName());
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
        String actualAttributeName = (attributeName == null) ? "value" : attributeName;
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
            if (e instanceof J.Literal) {
                J.Literal literal = (J.Literal) e;
                if (literal.getValueSource() != null && literal.getValueSource().equals(attributeValue)) {
                    return true;
                }
            }
            if (e instanceof J.NewArray) {
                return attributeValIsAlreadyPresent(((J.NewArray) e).getInitializer(), attributeValue);
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

