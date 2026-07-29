/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import lombok.EqualsAndHashCode;

/**
 * A recipe that moves a specified annotation from a parent annotation to the class declaration.
 * Optionally, the parent annotation can be deleted after the unwrapping operation.
 */
@EqualsAndHashCode(callSuper = false)
public class UnwrapAnnotationToClass extends Recipe {

    @Option(displayName = "Annotation to match",
            description = "Only the annotation that matches will be unwrapped, e.g. moved from a parent annotation to class.",
            example = "@javax.jdo.annotations.Index")
    String annotationPattern;

    @Option(displayName = "Delete the parent annotation",
            description = "Optional: When true, remove the parent annotation.",
            required = false,
            example = "true")
    Boolean removeParentAnnotation;


    @JsonCreator
    public UnwrapAnnotationToClass(
            @NonNull @JsonProperty("annotationPattern") String annotationPattern,
            @JsonProperty("removeParentAnnotation") Boolean removeParentAnnotation) {
        this.annotationPattern = annotationPattern;
        this.removeParentAnnotation = removeParentAnnotation;
    }

    @Override
    public String getDisplayName() {
        return "Unwrap annotation to class";
    }

    @Override
    public String getDescription() {
        return "This recipe moves the specified annotation from a parent annotation to class and optionally deletes the parent annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return Preconditions.check(new UsesType<>(annotationPattern, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                List<J.Annotation> unwrappedAnnotations = new ArrayList<>();
                List<J.Annotation> annotationsToRemove = cd.getLeadingAnnotations().stream()
                        // Is the annotation a potental wrapper/parent?
                        .filter(annotation -> !TypeUtils.isOfClassType(annotation.getType(), annotationPattern))
                        .filter(parentAnnotation -> {
                            // Find wrapped annotations
                            var toUnwrap = FindAnnotations.find(parentAnnotation, annotationPattern);
                            toUnwrap.remove(parentAnnotation);
                            if (!toUnwrap.isEmpty()) {
                                unwrappedAnnotations.addAll(toUnwrap);
                                if (Boolean.TRUE.equals(removeParentAnnotation)) {
                                    maybeRemoveImport(TypeUtils.asFullyQualified(parentAnnotation.getType()));
                                    return true;
                                }
                            }
                            return false;
                        })
                        .toList();
                if (!unwrappedAnnotations.isEmpty()) {
                    // Remove parent annotations. Copy the list first: getLeadingAnnotations() may return the
                    // backing list of the (immutable) LST node, and mutating it in place corrupts the original.
                    List<J.Annotation> otherAnnotations = new ArrayList<>(cd.getLeadingAnnotations());
                    boolean removedParent = otherAnnotations.removeAll(annotationsToRemove);
                    // Track annotations already present on the class so that repeated runs (or a retained parent
                    // wrapper when removeParentAnnotation is false) don't add duplicates.
                    Set<String> existingSignatures = new HashSet<>();
                    otherAnnotations.forEach(a -> existingSignatures.add(signature(a)));
                    // Add unwrapped annotations that are not already present on the class
                    List<J.Annotation> annotationsToAdd = unwrappedAnnotations.stream()
                            .sorted(Comparator.comparing(J.Annotation::toString))
                            .map(annotation -> annotation.withMarkers(annotation.getMarkers().removeByType(SearchResult.class)))
                            .map(annotation -> annotation.withPrefix(annotation.getPrefix().withWhitespace(
                                    annotation.getPrefix().getWhitespace().stripIndent())))
                            .filter(annotation -> existingSignatures.add(signature(annotation)))
                            .toList();
                    // Only rebuild the class declaration when something actually changed, so that the recipe is
                    // idempotent across cycles.
                    if (removedParent || !annotationsToAdd.isEmpty()) {
                        otherAnnotations.addAll(annotationsToAdd);
                        cd = cd.withLeadingAnnotations(List.of()); // Force the creation of a new classdeclaration, should NOT be needed :-(
                        cd = cd.withLeadingAnnotations(otherAnnotations);
                    }
                }
                return cd;
            }

        });
    }

    /**
     * A stable textual signature of an annotation, ignoring the transient {@link SearchResult} marker and any
     * surrounding whitespace, used to detect annotations that are already present on the class.
     */
    private static String signature(J.Annotation annotation) {
        return annotation.withMarkers(annotation.getMarkers().removeByType(SearchResult.class))
                .toString()
                .trim();
    }
}
