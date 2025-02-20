package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class CopyAnnotationAttributeFromSubclassToParentClass extends ScanningRecipe<CopyAnnotationAttributeFromSubclassToParentClass.Accumulator> {

    @Option(displayName = "Annotation type to match",
            description = "Only annotations that match this type will be copied to the parent.",
            example = "@javax.jdo.annotations.Discriminator")
    String annotationType;

    @Option(displayName = "The attribute to copy to parent",
            description = "The name of the attribute that has to be copied from the annotation on the subclass to the annotation of the parent class.",
            example = "strategy")
    String attributeToCopyToParent;

    @Option(displayName = "Regular expression to match",
            description = "When specified, only annotations that match the regular expression will be changed.",
            required = false,
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @JsonCreator
    public CopyAnnotationAttributeFromSubclassToParentClass(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("attributeToCopyToParent") String attributeToCopyToParent,
            @Nullable @JsonProperty("matchByRegularExpression") String matchByRegularExpression) {
        this.annotationType = annotationType;
        this.attributeToCopyToParent = attributeToCopyToParent;
        this.matchByRegularExpression = matchByRegularExpression;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Copy annotation attribute from subclass to parent class";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Copy annotation attribute from subclass to parent class.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getType() != null) {

                    // Collect the names of all super classes and interfaces.
                    JavaType.FullyQualified currentFq = cd.getType();
                    while (currentFq != null) {
                        for (JavaType.FullyQualified i : currentFq.getInterfaces()) {
                            acc.childrenByParent.computeIfAbsent(i, v -> new HashSet<>()).add(currentFq);
                        }
                        JavaType.FullyQualified supertype = currentFq.getSupertype();
                        if (supertype != null) {
                            acc.childrenByParent.computeIfAbsent(supertype, v -> new HashSet<>()).add(currentFq);
                        }
                        currentFq = supertype;
                    }
                    // Find all matching annotations for each type
                    JavaType.FullyQualified classFqn = cd.getType();
                    for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                        JavaType.FullyQualified annoFq = TypeUtils.asFullyQualified(annotation.getType());
                        if (annoFq != null && annotationType.equals(annoFq.getFullyQualifiedName())) {
                            acc.annotationsByType.computeIfAbsent(classFqn, v -> new HashSet<>()).add(annotation);
                        }
                    }
                }
                return cd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {

        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                JavaType.FullyQualified currentFq = cd.getType();
                if (currentFq != null && acc.childrenByParent.containsKey(currentFq)) {
                    // Match every annotation of this class with the specified type
                    var curAnnos = new ArrayList<>(cd.getLeadingAnnotations());
                    var newAnnos = RewriteUtils.findLeadingAnnotations(cd, annotationType).stream()
                            // Filter by the optional regular expression
                            .filter(ac -> matchByRegularExpression == null || ac.toString().matches(matchByRegularExpression))
                            .map(ac ->
                                    // Filter annotations in the child
                                    acc.childrenByParent.get(currentFq).stream()
                                            .map(acc.annotationsByType::get)
                                            .flatMap(Set::stream)
                                            // That have a value in the specified attribute
                                            .map(a -> RewriteUtils.findArgument(a, attributeToCopyToParent))
                                            .filter(Optional::isPresent)
                                            .map(Optional::get)
                                            // Use the first found assignment
                                            .findFirst()
                                            .filter(assignmentOfChild -> !ac.getArguments().contains(assignmentOfChild))
                                            // Update current (parent) annotation with the value found in one of the children
                                            .map(assignmentOfChild -> {
                                                curAnnos.remove(ac);
                                                var newArg = ac.getArguments();
                                                RewriteUtils.findArgument(ac, attributeToCopyToParent).ifPresent(newArg::remove);
                                                newArg.add(assignmentOfChild);
                                                return ac.withArguments(newArg);
                                            })
                                            .orElse(null)
                            )
                            .filter(Objects::nonNull)
                            .toList();
                    if (!newAnnos.isEmpty()) {
                        cd = cd.withLeadingAnnotations(ListUtils.concatAll(curAnnos, newAnnos));
                    }
                }
                return cd;
            }
        };
    }

    @Data
    class Accumulator {
        final Map<JavaType.FullyQualified, Set<JavaType.FullyQualified>> childrenByParent = new HashMap<>();
        final Map<JavaType.FullyQualified, Set<J.Annotation>> annotationsByType = new HashMap<>();
    }
}
