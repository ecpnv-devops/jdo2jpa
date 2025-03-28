package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.RewriteUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe for copying a specific attribute from an annotation on a subclass
 * to the corresponding annotation on a parent class. This class provides functionality
 * to scan for annotations in a subclass that match a specific type and optionally
 * a regular expression, and then replicate an attribute from those annotations
 * to the annotations on the parent class.
 * <p>
 * This recipe can be configured with the following options:
 * - The type of the annotation to target.
 * - The specific attribute of the annotation to copy.
 * - An optional regular expression to filter the annotations further.
 * - A flag to restrict copying to the immediate parent class only.
 * <p>
 * It utilizes scanning capabilities to initially capture the relevant relationships between
 * subclasses and their parent classes, as well as the matching annotations. Then it applies
 * transformations to copy attributes between the matched annotations on subclasses to their
 * parent classes.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
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

    @Option(displayName = "Copy to base class only",
            description = "When specified, only annotations that are one level down the base class are copied.",
            required = false,
            example = "true")
    Boolean copyToBaseClassOnly;

    @JsonCreator
    public CopyAnnotationAttributeFromSubclassToParentClass(
            @NonNull @JsonProperty("annotationType") String annotationType,
            @NonNull @JsonProperty("attributeToCopyToParent") String attributeToCopyToParent,
            @Nullable @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @Nullable @JsonProperty("copyToBaseClassOnly") Boolean copyToBaseClassOnly) {
        this.annotationType = annotationType;
        this.attributeToCopyToParent = attributeToCopyToParent;
        this.matchByRegularExpression = matchByRegularExpression;
        this.copyToBaseClassOnly = copyToBaseClassOnly != null && copyToBaseClassOnly;
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
                if (ctx.getCycle() == 1 && cd.getType() != null) {

                    // Find all matching annotations for each type
                    JavaType.FullyQualified classFqn = cd.getType();
                    for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                        JavaType.FullyQualified annoFq = TypeUtils.asFullyQualified(annotation.getType());
                        if (annoFq != null && annotationType.equals(annoFq.getFullyQualifiedName())) {
                            acc.annotationsByType.computeIfAbsent(classFqn, v -> new HashSet<>()).add(annotation);
                        }
                    }

                    // Collect the names of all super classes and interfaces.
                    JavaType.FullyQualified currentFq = cd.getType();
                    while (currentFq != null) {
                        JavaType.FullyQualified supertype = currentFq.getSupertype();
                        for (JavaType.FullyQualified i : currentFq.getInterfaces()) {
                            acc.childrenByParent.computeIfAbsent(i, v -> new HashSet<>()).add(currentFq);
                        }
                        if (supertype != null && !"java.lang.Object".equals(supertype.getFullyQualifiedName())) {
                            acc.childrenByParent.computeIfAbsent(supertype, v -> new HashSet<>()).add(currentFq);
                        }
                        currentFq = supertype;
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
                if (currentFq != null && acc.childrenByParent.containsKey(currentFq)
                        // When copyToBaseClassOnly == true then current class should be the base class for given annotation
                        && (!copyToBaseClassOnly || currentFq.getSupertype() == null || !acc.childrenByParent.containsKey(currentFq.getSupertype()))
                        // When copyToBaseClassOnly == true, then only process class when it has the given annotation
                        && (!copyToBaseClassOnly || cd.getLeadingAnnotations().stream()
                        .map(J.Annotation::getType)
                        .map(TypeUtils::asFullyQualified)
                        .filter(Objects::nonNull)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .anyMatch(annotationType::equals))
                ) {
                    // Match every annotation of this class with the specified type
                    var curAnnos = new ArrayList<>(cd.getLeadingAnnotations());
                    var newAnnos = RewriteUtils.findLeadingAnnotations(cd, annotationType).stream()
                            // Filter by the optional regular expression
                            .filter(ac -> matchByRegularExpression == null || ac.toString().matches(matchByRegularExpression))
                            .map(ac ->
                                    // Filter annotations in the child
                                    acc.childrenByParent.get(currentFq).stream()
                                            .map(acc.annotationsByType::get)
                                            .filter(Objects::nonNull)
                                            .flatMap(Set::stream)
                                            // That have a value in the specified attribute
                                            .map(a -> RewriteUtils.findArgumentAssignment(a, attributeToCopyToParent))
                                            .filter(Optional::isPresent)
                                            .map(Optional::get)
                                            // Use the first found assignment
                                            .findFirst()
                                            .filter(assignmentOfChild -> ac.getArguments() == null || !ac.getArguments().contains(assignmentOfChild))
                                            // Update current (parent) annotation with the value found in one of the children
                                            .map(assignmentOfChild -> {
                                                List<Expression> newArg = new ArrayList<>();
                                                // Only if current has no arguments
                                                boolean add = ac.getArguments() == null;
                                                if (!add) {
                                                    newArg.addAll(ac.getArguments());
                                                    var argIfAny = RewriteUtils.findArgumentAssignment(ac, attributeToCopyToParent);
                                                    // Or has different argument
                                                    if (argIfAny.filter(assignment -> !assignment.getAssignment()
                                                            .equals(assignmentOfChild.getAssignment())).isPresent()) {
                                                        newArg.remove(argIfAny.get());
                                                        add = true;
                                                    } else if (!argIfAny.isPresent()) {
                                                        // Or doesn't have this argument
                                                        add = true;
                                                    }
                                                }
                                                if (add) {
                                                    curAnnos.remove(ac);
                                                    newArg.add(assignmentOfChild);
                                                    return ac.withArguments(newArg);
                                                }
                                                return null;
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
