package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.EqualsAndHashCode;


// TODO docs
// * @author Original Open Rewrite authors
// * @author Patrick Deenen @ Open Circle Solutions
@EqualsAndHashCode(callSuper = false)
public class UpdateAnnotationAttributeFromFieldAnnotationAttribute extends Recipe {

    @Override
    public String getDisplayName() {
        return "Update annotation attribute using field";
    }

    @Override
    public String getDescription() {
        return "Some annotations accept arguments referencing fields in the same class. This recipe sets an existing " +
                "argument to the specified value found in the attribute of the field annotation.";
    }

    @Option(displayName = "Target annotation type",
            description = "The fully qualified name of the annotation.",
            example = "javax.jdo.annotations.Index")
    String annotationType;

    @Option(displayName = "Target attribute name",
            description = "The name of attribute to change. If omitted defaults to 'value'.",
            required = false,
            example = "members")
    @Nullable
    String attributeName;

    @Option(displayName = "Field annotation type",
            description = "The fully qualified name of the field annotation.",
            example = "javax.jdo.annotations.Column")
    String fieldAnnotationType;

    @Option(displayName = "Field attribute name",
            description = "The name of attribute to use. If omitted defaults to 'value'.",
            required = false,
            example = "name")
    @Nullable
    String fieldAttributeName;

    @JsonCreator
    public UpdateAnnotationAttributeFromFieldAnnotationAttribute(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @Nullable @JsonProperty("attributeName") String attributeName,
            @NonNull @JsonProperty("fieldAnnotationType") String fieldAnnotationType,
            @Nullable @JsonProperty("fieldAttributeName") String fieldAttributeName) {
        this.annotationType = annotationType;
        this.attributeName = attributeName;
        this.fieldAnnotationType = fieldAnnotationType;
        this.fieldAttributeName = fieldAttributeName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false),
                new JavaIsoVisitor<>() {

                    Map<String, Object> fieldColumnNames = null;
                    Map<String, String> constants = null;
                    J.ClassDeclaration currentClassDecl = null;

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                        if (currentClassDecl == null) {
                            fieldColumnNames = new HashMap<>();
                            constants = new HashMap<>();
                            currentClassDecl = classDecl;
                        }
                        // Collect the fields info
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                        // Has the class leading annotations that match?
                        var newAnnos = cd.getLeadingAnnotations().stream()
                                .map(a -> {
                                    if (TypeUtils.isOfClassType(a.getType(), annotationType)) {
                                        return processAnnotation(executionContext, a);
                                    } else
                                        // Also process annotations in annotations
                                        if (a.getArguments() != null && !a.getArguments().isEmpty()
                                                && a.getArguments().get(0) instanceof J.NewArray newArray
                                                && !newArray.getInitializer().isEmpty() && TypeUtils.isOfClassType(
                                                newArray.getInitializer().get(0).getType(), annotationType)) {
                                            return a.withArguments(
                                                    newArray.getInitializer().stream()
                                                            .map(sa -> processAnnotation(executionContext, (J.Annotation) sa))
                                                            .map(sa -> (Expression) sa)
                                                            .toList()
                                            );
                                        }
                                    return a;
                                })
                                .toList();
                        ;
                        // And replace the value in the target annotation
                        if (newAnnos != cd.getLeadingAnnotations()) {
                            cd = cd.withLeadingAnnotations(newAnnos);
                        }
                        if (currentClassDecl == classDecl) {
                            currentClassDecl = null;
                        }
                        return cd;
                    }

                    private J.Annotation processAnnotation(ExecutionContext executionContext, J.Annotation a) {
                        // Are there any fields that match the annotation field name?
                        return RewriteUtils.findArgument(a, attributeName)
                                .map(arg -> {
                                    // Then Get the value of the fieldAnnotation attribute
                                    if (arg instanceof J.Literal literal) {
                                        return addOrUpdateAnnotationAttribute(executionContext, a, literal.getValue());
                                    } else if (arg instanceof J.Assignment assignment) {
                                        if (assignment.getAssignment() instanceof J.Literal literal) {
                                            return addOrUpdateAnnotationAttribute(executionContext, a, literal.getValue());
                                        } else if (assignment.getAssignment() instanceof J.NewArray newArray) {
                                            return addOrUpdateAnnotationAttribute(executionContext, a, newArray);
                                        }
                                    }
                                    return a;
                                })
                                .orElse(a);
                    }

                    private J.Annotation addOrUpdateAnnotationAttribute(
                            ExecutionContext executionContext, J.Annotation a, J.NewArray oldValue) {
                        if (oldValue == null)
                            return a;
                        List<String> newValue = new ArrayList<>();
                        boolean change = false;
                        for (Expression e : oldValue.getInitializer()) {
                            var nv = resolveValue(e);
                            if (nv != null && nv != e) change = true;
                            newValue.add(nv.toString());
                        }
                        if (!change || newValue.size() != oldValue.getInitializer().size())
                            return a;
                        var ov = oldValue.getInitializer().toString();
                        ov = ov.substring(1, ov.length() - 1);
                        return (J.Annotation) new AddOrUpdateAnnotationAttribute(annotationType, false,
                                attributeName, StringUtils.join(newValue, ","), ov, AddOrUpdateAnnotationAttribute.Operation.UPDATE)
                                .getVisitor().visit(a, executionContext);
                    }

                    private Object resolveValue(Object value) {
                        var ov = value.toString();
                        // Get the value from field annotation
                        var newValue = fieldColumnNames.get(RewriteUtils.maybeUnquoteString(ov));
                        if (newValue == null)
                            return value;
                        // When the value is a constant get it from the class
                        var constant = constants.get(newValue.toString());
                        if (constant != null)
                            newValue = constant;
                        return newValue;
                    }

                    private J.Annotation addOrUpdateAnnotationAttribute(
                            ExecutionContext executionContext, J.Annotation a, @Nullable Object oldValue) {
                        if (oldValue == null)
                            return a;
                        var ov = oldValue.toString();
                        // Get the value from field annotation
                        var newValue = resolveValue(oldValue);
                        if (newValue == null || newValue == oldValue)
                            return a;
                        return (J.Annotation) new AddOrUpdateAnnotationAttribute(annotationType, false,
                                attributeName, newValue, ov, AddOrUpdateAnnotationAttribute.Operation.UPDATE)
                                .getVisitor().visit(a, executionContext);
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, executionContext);
                        // Has field annotation with attribute?
                        FindAnnotations.find(mv, fieldAnnotationType, false)
                                .stream()
                                .map(a -> RewriteUtils.findArgumentValue(a, fieldAttributeName).orElse(null))
                                .filter(Objects::nonNull)
                                .findFirst()
                                // Then add the field name and column name to the map
                                .ifPresent(columnNameOrRef -> fieldColumnNames.put(mv.getVariables().get(0).getSimpleName(), columnNameOrRef));
                        // Is constant?
                        mv.getVariables();
                        if (!mv.getVariables().isEmpty() && mv.getVariables().get(0).getInitializer() != null) {
                            J.ClassDeclaration cls = RewriteUtils.findParentClass(getCursor());
                            if (cls != null) {
                                var name = cls.getName() + "." + mv.getVariables().get(0).getSimpleName();
                                constants.put(name, mv.getVariables().get(0).getInitializer().toString());
                            }
                        }
                        return mv;
                    }
                });
    }
}

