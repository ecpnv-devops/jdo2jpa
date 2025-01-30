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
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A recipe that copies non-inherited annotations from superclasses or interfaces to the current class declaration.
 * The annotations to be copied can be limited using the specified set of non-inherited annotation types.
 * <p>
 * Non-inherited annotations are defined as annotations not annotated with `@Inherited`.
 * These annotations are explicitly copied to ensure they are present when analyzing or transforming the
 * current class.
 * <p>
 * Key operations include:
 * - Collecting non-inherited annotations from parent types such as superclasses and interfaces.
 * - Avoiding duplication by skipping annotations already present on the current class.
 * - Automatically adding the necessary imports for the copied annotations.
 * <p>
 * The recipe leverages two primary stages:
 * 1. Scanning the parent classes and interfaces to accumulate non-inherited annotations.
 * 2. Applying the accumulated annotations to the current class and adding missing imports.
 * <p>
 * Note: this class is basically a copy from org.openrewrite.java.micronaut.CopyNonInheritedAnnotations, but with the
 * added feature that a list of non-inherited annotations can be provided.
 *
 * @author Original Open Rewrite authors
 * @author Patrick Deenen @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class CopyNonInheritedAnnotations extends ScanningRecipe<CopyNonInheritedAnnotations.Accumulator> {

    @Option(displayName = "Set of annotations to match",
            description = "Only annotations that match this set will be copied.",
            example = "@javax.jdo.annotations.Discriminator")
    Set<String> nonInheritedAnnotationTypes;

    @JsonCreator
    public CopyNonInheritedAnnotations(
            @NonNull @JsonProperty("nonInheritedAnnotationTypes") Set<String> nonInheritedAnnotationTypes) {
        this.nonInheritedAnnotationTypes = nonInheritedAnnotationTypes;
    }

    @Override
    public String getDisplayName() {
        return "Copy non-inherited annotations from super class";
    }

    @Override
    public String getDescription() {
        return "This recipe copies non-inherited annotations from super classes to the current class.";
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
        private final Map<String, List<J.Annotation>> parentAnnotationsByType;

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
                parentTypes.add(currentFq.getFullyQualifiedName());
                for (JavaType.FullyQualified i : currentFq.getInterfaces()) {
                    parentTypes.add(i.getFullyQualifiedName());
                }
                currentFq = currentFq.getSupertype();
                if (currentFq != null && parentTypes.contains(currentFq.getFullyQualifiedName())) {
                    break;
                }
            }

            //Collect the annotation names already applied to the class.
            Set<String> existingAnnotations = new HashSet<>();
            for (J.Annotation leadingAnnotation : cd.getLeadingAnnotations()) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(leadingAnnotation.getType());
                if (fullyQualified != null) {
                    existingAnnotations.add(fullyQualified.getFullyQualifiedName());
                }
            }

            List<J.Annotation> annotationsFromParentClass = new ArrayList<>();
            for (String parentTypeFq : parentTypes) {
                List<J.Annotation> parentAnnotations = parentAnnotationsByType.get(parentTypeFq);
                if (parentAnnotations != null) {
                    for (J.Annotation annotation : parentAnnotations) {
                        JavaType.FullyQualified annotationName = TypeUtils.asFullyQualified(annotation.getType());
                        if (annotationName != null && !existingAnnotations.contains(annotationName.getFullyQualifiedName())) {
                            //If the annotation does not exist on the current class, add it.
                            annotation = processAddedAnnotation(cd, annotation, ctx);
                            annotationsFromParentClass.add(annotation);
                            existingAnnotations.add(annotationName.getFullyQualifiedName());
                        }
                    }
                }
            }

            List<J.Annotation> afterAnnotationList = ListUtils.concatAll(cd.getLeadingAnnotations(), annotationsFromParentClass);
            if (afterAnnotationList != cd.getLeadingAnnotations()) {
                cd = cd.withLeadingAnnotations(afterAnnotationList);
                cd = autoFormat(cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
                for (J.Annotation annotation : annotationsFromParentClass) {
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(annotation.getType());
                    if (fullyQualified != null) {
                        maybeAddImport(fullyQualified.getFullyQualifiedName());
                    }
                }
            }
            return cd;
        }

        protected J.Annotation processAddedAnnotation(
                J.ClassDeclaration classDeclaration,
                J.Annotation annotation,
                ExecutionContext ctx) {
            return annotation;
        }
    }

    @Data
    protected static class Accumulator {
        final Map<String, List<J.Annotation>> parentAnnotationsByType = new HashMap<>();
    }
}
