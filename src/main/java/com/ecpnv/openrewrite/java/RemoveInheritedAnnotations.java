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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

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
public class RemoveInheritedAnnotations extends Recipe {

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveAnnotationVisitor(nonInheritedAnnotationTypes);
    }

    public static class RemoveAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        Set<String> nonInheritedAnnotationTypes;

        public RemoveAnnotationVisitor(Set<String> nonInheritedAnnotationTypes) {
            this.nonInheritedAnnotationTypes = nonInheritedAnnotationTypes;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            JavaType.FullyQualified currentFq = cd.getType();

            // Collect the names of all super classes and interfaces.
            Set<JavaType.FullyQualified> parentTypes = new HashSet<>();
            while (currentFq != null) {
                currentFq = currentFq.getSupertype();
                if (currentFq != null) {
                    if (currentFq.getFullyQualifiedName().equals("java.lang.Object")
                            || parentTypes.contains(currentFq)) {
                        break;
                    }
                    parentTypes.add(currentFq);
                    parentTypes.addAll(currentFq.getInterfaces());
                }
            }

            // Collect the annotations to remove
            J.ClassDeclaration finalCd = cd;
            List<J.Annotation> annotationsToRemove = cd.getLeadingAnnotations().stream()
                    .filter(ca -> ca.getAnnotationType().getType() != null)
                    .filter(ca -> nonInheritedAnnotationTypes.contains(((JavaType.FullyQualified) ca.getAnnotationType().getType()).getFullyQualifiedName()))
                    // Is there any parent type
                    .filter(ca -> parentTypes.stream()
                            // That has candidate annotations
                            .map(JavaType.FullyQualified::getAnnotations)
                            .flatMap(List::stream)
                            // Matching annotations in the current class?
                            .anyMatch(pa -> ca.getAnnotationType().getType().toString().equals(pa.getFullyQualifiedName())))
                    .filter(atr -> processAnnotationBeforeRemoval(finalCd, atr, ctx))
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

        /**
         * When overridden in the subclass this method provides the possibility to take additional actions
         * based on the removal of annotation or even cancel the removal.
         *
         * @param classDeclaration
         * @param annotation
         * @param ctx
         * @return true when the annotation should be removed otherwise false
         */
        protected boolean processAnnotationBeforeRemoval(
                J.ClassDeclaration classDeclaration,
                J.Annotation annotation,
                ExecutionContext ctx) {
            return true;
        }
    }
}
