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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A recipe that removes inherited annotations from subclasses or interfaces to the current class declaration.
 * The annotations to be removed can be specified using the specified set of inherited annotation types.
 * <p>
 * Key operations include:
 * - Collecting inherited annotations from parent types such as superclasses and interfaces.
 * - Automatically removing the necessary imports for the removed annotations.
 * <p>
 * The recipe leverages two primary stages:
 * 1. Scanning the parent classes and interfaces to accumulate inherited annotations.
 * 2. Applying the accumulated annotations to the current class and adding missing imports.
 * <p>
 * Note: this class is inspired by org.openrewrite.java.micronaut.CopyNonInheritedAnnotations, but with the
 * changed feature that a list of inherited annotations can be provided, which will be remove in the subclasses.
 *
 * @author Original Open Rewrite authors
 * @author Patrick Deenen @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class RemoveInheritedAnnotations extends ScanningRecipe<RemoveInheritedAnnotations.Accumulator> {

    @Option(displayName = "Set of annotations to match",
            description = "Only annotations that match this set will be removed.",
            example = "@javax.jdo.annotations.Discriminator")
    Set<String> nonInheritedAnnotationTypes;

    @JsonCreator
    public RemoveInheritedAnnotations(
            @NonNull @JsonProperty("nonInheritedAnnotationTypes") Set<String> nonInheritedAnnotationTypes) {
        this.nonInheritedAnnotationTypes = nonInheritedAnnotationTypes;
    }

    @Override
    public String getDisplayName() {
        return "Remove inherited annotations from subclass";
    }

    @Override
    public String getDescription() {
        return "This recipe removes inherited annotations from sub classes.";
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
                    String classFqn = cd.getType().getFullyQualifiedName();
                    for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                        JavaType.FullyQualified annoFq = TypeUtils.asFullyQualified(annotation.getType());
                        if (annoFq != null && nonInheritedAnnotationTypes.stream().anyMatch(fqn -> fqn.equals(annoFq.getFullyQualifiedName()))) {
                            acc.getParentAnnotationsByType().computeIfAbsent(classFqn, v -> new ArrayList<>()).add(annotation);
                        }
                    }
                }
                return cd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.getParentAnnotationsByType().isEmpty()) {
            return TreeVisitor.noop();
        }

        return new CopyAnnoVisitor(acc.getParentAnnotationsByType());
    }

    public static class CopyAnnoVisitor extends JavaIsoVisitor<ExecutionContext> {
        protected final Map<String, List<J.Annotation>> parentAnnotationsByType;

        public CopyAnnoVisitor(Map<String, List<J.Annotation>> parentAnnotationsByType) {
            this.parentAnnotationsByType = parentAnnotationsByType;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            JavaType.FullyQualified currentFq = cd.getType();

            // Collect the names of all super classes and interfaces.
            Set<String> parentTypes = new HashSet<>();
            while (currentFq != null) {
                currentFq = currentFq.getSupertype();
                if (currentFq != null) {
                    if (currentFq.getFullyQualifiedName().equals("java.lang.Object")
                            || parentTypes.contains(currentFq.getFullyQualifiedName())) {
                        break;
                    }
                    parentTypes.add(currentFq.getFullyQualifiedName());
                    for (JavaType.FullyQualified i : currentFq.getInterfaces()) {
                        parentTypes.add(i.getFullyQualifiedName());
                    }
                }
            }

            // Collect the annotations to remove
            List<J.Annotation> annotationsToRemove = cd.getLeadingAnnotations().stream()
                    // Is there any parent type
                    .filter(ca -> parentTypes.stream()
                            // That has candidate annotations
                            .map(parentAnnotationsByType::get)
                            .filter(Objects::nonNull)
                            .flatMap(List::stream)
                            // Matching annotations in the current class?
                            .anyMatch(pa -> ca.getType() != null && ca.getType().equals(pa.getType())))
                    .toList();

            if (!annotationsToRemove.isEmpty()) {
                List<J.Annotation> leadingAnnotations = new ArrayList<>(cd.getLeadingAnnotations());
                leadingAnnotations.removeAll(annotationsToRemove);
                cd = cd.withLeadingAnnotations(leadingAnnotations);
                annotationsToRemove.forEach(annotation ->
                        maybeRemoveImport(TypeUtils.asFullyQualified(annotation.getType())));
            }

            return cd;
        }
    }

    @Data
    protected static class Accumulator {
        final Map<String, List<J.Annotation>> parentAnnotationsByType = new HashMap<>();
    }
}
