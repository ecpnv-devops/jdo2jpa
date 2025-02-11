package com.ecpnv.openrewrite.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.RemoveImport;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class MaybeRemoveImport extends Recipe {

    @Option(displayName = "Type",
            description = "The fully qualified name of the type.",
            example = "org.junit.Test")
    @NonNull
    String type;

    @Override
    public String getDisplayName() {
        return "Remove import when type is no longer used";
    }

    @Override
    public String getDescription() {
        return "When a type is no longer used in the class, remove from import.";
    }

    @JsonCreator
    public MaybeRemoveImport(
            @NonNull @JsonProperty("type") String type) {
        this.type = type;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveImport<>(type);
    }
}
