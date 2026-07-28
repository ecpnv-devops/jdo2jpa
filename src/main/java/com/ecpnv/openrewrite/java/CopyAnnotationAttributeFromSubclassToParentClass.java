package com.ecpnv.openrewrite.java;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import static org.openrewrite.Tree.randomId;

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
 * <p>
 * <b>Conflict handling.</b> JPA supports only a single inheritance strategy per entity hierarchy
 * (the strategy is declared on the root entity and applies to the whole hierarchy). This recipe
 * runs on the raw JDO {@code @Inheritance} annotation, before the JDO {@code InheritanceStrategy}
 * values are translated to JPA {@code InheritanceType} values, so two differing JDO values map to
 * two different JPA strategies. When the (direct) subclasses of a parent carry <i>different</i>
 * values for the requested attribute, there is no single value that can be copied safely. In that
 * case the recipe does <b>not</b> transform and instead marks the parent class with a
 * {@link org.openrewrite.marker.SearchResult} describing the conflict, so a human can resolve it.
 * <p>
 * <b>Limitations.</b> Only attributes expressed as {@code name = value} assignments are copied; the
 * single-element {@code value} shorthand (e.g. {@code @Discriminator("X")}) is not copied. The
 * optional regular expression is matched (unanchored, {@link java.util.regex.Matcher#find()})
 * against the printed form of the <i>parent</i> annotation that would be changed.
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

            final Pattern matchPattern = matchByRegularExpression == null ? null : Pattern.compile(matchByRegularExpression);

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                JavaType.FullyQualified currentFq = cd.getType();
                if (currentFq == null || !acc.childrenByParent.containsKey(currentFq)
                        // When copyToBaseClassOnly == true then only process the base class that carries the annotation
                        || (copyToBaseClassOnly && !isAnnotatedBaseClass(cd, currentFq, acc))) {
                    return cd;
                }

                // Collect the distinct values that the (direct) subclasses declare for the attribute.
                // Keyed by the printed value so that semantically identical values collapse to one entry.
                Map<String, J.Assignment> childValues = new LinkedHashMap<>();
                for (JavaType.FullyQualified child : acc.childrenByParent.get(currentFq)) {
                    Set<J.Annotation> childAnnotations = acc.annotationsByType.get(child);
                    if (childAnnotations == null) {
                        continue;
                    }
                    for (J.Annotation childAnnotation : childAnnotations) {
                        RewriteUtils.findArgumentAssignment(childAnnotation, attributeToCopyToParent)
                                .ifPresent(a -> childValues.putIfAbsent(a.getAssignment().printTrimmed(getCursor()), a));
                    }
                }
                if (childValues.isEmpty()) {
                    // No subclass declares the attribute -> nothing to copy.
                    return cd;
                }

                if (childValues.size() > 1) {
                    // JPA supports only one inheritance strategy per hierarchy. Refuse to guess which
                    // conflicting subclass value should win; flag the class for manual resolution instead.
                    if (cd.getMarkers().findFirst(SearchResult.class).isPresent()) {
                        return cd;
                    }
                    return SearchResult.found(cd, "Conflicting values for attribute '" + attributeToCopyToParent
                            + "' on subclasses " + childValues.keySet().stream().sorted().toList()
                            + "; JPA supports only one strategy per inheritance hierarchy - resolve manually.");
                }

                J.Assignment childAssignment = childValues.values().iterator().next();
                String childValue = childValues.keySet().iterator().next();
                return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(),
                        annotation -> copyAttributeToAnnotation(annotation, childAssignment, childValue)));
            }

            /**
             * Updates {@code parentAnnotation} with the given attribute value, in place, when it matches the
             * configured annotation type (and optional regular expression). Returns the annotation unchanged
             * when it does not match or already holds the desired value.
             */
            private J.Annotation copyAttributeToAnnotation(J.Annotation parentAnnotation, J.Assignment childAssignment, String childValue) {
                JavaType.FullyQualified annoFq = TypeUtils.asFullyQualified(parentAnnotation.getType());
                if (annoFq == null || !annotationType.equals(annoFq.getFullyQualifiedName())) {
                    return parentAnnotation;
                }
                if (matchPattern != null && !matchPattern.matcher(parentAnnotation.printTrimmed(getCursor())).find()) {
                    return parentAnnotation;
                }

                // Existing "real" arguments, ignoring the J.Empty placeholder of an empty argument list "()".
                List<Expression> existingArgs = parentAnnotation.getArguments() == null
                        ? List.of()
                        : parentAnnotation.getArguments().stream()
                        .filter(a -> !(a instanceof J.Empty))
                        .toList();

                Optional<J.Assignment> existing = RewriteUtils.findArgumentAssignment(parentAnnotation, attributeToCopyToParent);
                if (existing.isPresent()
                        && childValue.equals(existing.get().getAssignment().printTrimmed(getCursor()))) {
                    // Already has the desired value -> idempotent no-op.
                    return parentAnnotation;
                }

                List<Expression> newArgs;
                if (existing.isPresent()) {
                    // Replace only the value on the original position, keeping the parent's attribute name
                    // and the surrounding whitespace. Reuse the subclass' (fully type-attributed) value node
                    // with a fresh id so downstream matchers keep working while ids stay unique.
                    J.Assignment old = existing.get();
                    final Expression newValue = (Expression) childAssignment.getAssignment()
                            .withPrefix(old.getAssignment().getPrefix())
                            .withId(randomId());
                    newArgs = ListUtils.map(existingArgs, a -> a == old ? old.withAssignment(newValue) : a);
                } else if (existingArgs.isEmpty()) {
                    // Sole argument, directly after "(".
                    newArgs = List.<Expression>of(copyOfChildAssignment(childAssignment, Space.EMPTY));
                } else {
                    // Append after the existing arguments with a single leading space.
                    newArgs = ListUtils.concat(existingArgs, copyOfChildAssignment(childAssignment, Space.SINGLE_SPACE));
                }
                return parentAnnotation.withArguments(newArgs);
            }

            /**
             * Returns a copy of the subclass' assignment with a fresh id and the given prefix, so it can be
             * placed on the parent annotation without duplicating the original node's id.
             */
            private J.Assignment copyOfChildAssignment(J.Assignment childAssignment, Space prefix) {
                J.Assignment copy = childAssignment.withId(randomId());
                return copy.withPrefix(prefix);
            }
        };
    }

    /**
     * Returns {@code true} when {@code fq} is the base class of the inheritance hierarchy for the configured
     * annotation (i.e. its supertype is not itself a registered parent) and the class actually carries the
     * configured annotation.
     */
    private boolean isAnnotatedBaseClass(J.ClassDeclaration cd, JavaType.FullyQualified fq, Accumulator acc) {
        boolean isBase = fq.getSupertype() == null || !acc.childrenByParent.containsKey(fq.getSupertype());
        return isBase && cd.getLeadingAnnotations().stream()
                .map(J.Annotation::getType)
                .map(TypeUtils::asFullyQualified)
                .filter(Objects::nonNull)
                .map(JavaType.FullyQualified::getFullyQualifiedName)
                .anyMatch(annotationType::equals);
    }

    @Data
    class Accumulator {
        final Map<JavaType.FullyQualified, Set<JavaType.FullyQualified>> childrenByParent = new HashMap<>();
        final Map<JavaType.FullyQualified, Set<J.Annotation>> annotationsByType = new HashMap<>();
    }
}
