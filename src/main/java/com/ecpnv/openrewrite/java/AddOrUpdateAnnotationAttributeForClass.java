package com.ecpnv.openrewrite.java;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import com.ecpnv.openrewrite.java.search.FindClassesVistor;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddOrUpdateAnnotationAttributeForClass extends Recipe {

    @Option(displayName = "Fully-qualified type name",
            description = "A fully-qualified type name, that is used to find matching type references. " +
                    "Supports glob expressions. `java..*` finds every type from every subpackage of the `java` package.",
            example = "java.util.List")
    String fullyQualifiedTypeName;

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
    AddOrUpdateAnnotationAttribute.Operation operation;

    @Option(displayName = "Append array",
            description = "If the attribute is an array, setting this option to `true` will append the value(s). " +
                    "In conjunction with `addOnly`, it is possible to control duplicates: " +
                    "`addOnly=true`, always append. " +
                    "`addOnly=false`, only append if the value is not already present.")
    Boolean appendArray;


    @Override
    public String getDisplayName() {
        return "Add or update annotation attribute for a given class";
    }

    @Override
    public String getDescription() {
        return "Some annotations accept arguments. This recipe sets an existing argument to the specified value, " +
                "or adds the argument if it is not already set.";
    }

    public AddOrUpdateAnnotationAttributeForClass(
            @NonNull @JsonProperty("fullyQualifiedTypeName") String fullyQualifiedTypeName,
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("appendArray") Boolean appendArray,
            @Nullable @JsonProperty("attributeName") String attributeName,
            @NonNull @JsonProperty("attributeValue") String attributeValue,
            @Nullable @JsonProperty("oldAttributeValue") String oldAttributeValue,
            @JsonProperty("operation") AddOrUpdateAnnotationAttribute.Operation operation) {
        this.fullyQualifiedTypeName = fullyQualifiedTypeName;
        this.annotationType = annotationType;
        this.appendArray = appendArray;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.oldAttributeValue = "null".equals(oldAttributeValue) ? null : oldAttributeValue;
        this.operation = operation;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindClassesVistor(fullyQualifiedTypeName),
                new AddOrUpdateAnnotationAttribute(annotationType, appendArray, attributeName, attributeValue,
                        oldAttributeValue, operation, null, null).getVisitor());
    }

}
