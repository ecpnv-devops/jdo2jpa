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
 * A recipe that processes class declarations to copy or move annotations from a subclass to its superclass.
 * The annotations to be copied or moved are restricted to a specified set of fully qualified annotation types.
 *
 * @author Original Open Rewrite authors
 * @author Patrick Deenen @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class CopyAnnotationToSuper extends ScanningRecipe<CopyAnnotationToSuper.Accumulator> {

    @Option(displayName = "Set of annotations to match",
            description = "Only annotations that match this set will be copied.",
            example = "@javax.jdo.annotations.Discriminator")
    Set<String> annotationTypes;

    @Option(displayName = "Move instead of copy",
            description = "When true then move instead of copy, e.g. delete the annotation on the subclass. Default is false.",
            required = false,
            example = "true")
    boolean move;

    @JsonCreator
    public CopyAnnotationToSuper(
            @NonNull @JsonProperty("annotationTypes") Set<String> annotationTypes,
            @NonNull @JsonProperty("move") boolean move) {
        this.annotationTypes = annotationTypes;
        this.move = move;
    }

    @Override
    public String getDisplayName() {
        return "Copy annotations to super class";
    }

    @Override
    public String getDescription() {
        return "This recipe copies annotations from current class to super classes.";
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
                if (cd.getType() != null && cd.getType().getSupertype() != null) {
                    String classFqn = cd.getType().getSupertype().getFullyQualifiedName();
                    if (!"java.lang.Object".equals(classFqn)) {
                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            JavaType.FullyQualified annoFq = TypeUtils.asFullyQualified(annotation.getType());
                            if (annoFq != null && annotationTypes.stream().anyMatch(fqn -> fqn.equals(annoFq.getFullyQualifiedName()))
                                    && (acc.childAnnotationsByParentType.get(classFqn) == null ||
                                    acc.childAnnotationsByParentType.get(classFqn).stream()
                                            .noneMatch(a -> TypeUtils.asFullyQualified(a.getType()).equals(annoFq)))) {
                                acc.getChildAnnotationsByParentType().computeIfAbsent(classFqn, v -> new ArrayList<>()).add(annotation);
                            }
                        }
                    }
                }
                return cd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.getChildAnnotationsByParentType().isEmpty()) {
            return TreeVisitor.noop();
        }

        return new CopyAnnoVisitor(acc.getChildAnnotationsByParentType(), move);
    }

    public static class CopyAnnoVisitor extends JavaIsoVisitor<ExecutionContext> {
        protected final Map<String, List<J.Annotation>> childAnnotationsByParentType;
        protected final boolean move;

        public CopyAnnoVisitor(Map<String, List<J.Annotation>> childAnnotationsByParentType, boolean move) {
            this.childAnnotationsByParentType = childAnnotationsByParentType;
            this.move = move;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            JavaType.FullyQualified currentFq = cd.getType();

            // For every found child annotation
            List<J.Annotation> annotationsToAdd = new ArrayList<>();
            final var clsdecl = cd;
            if (currentFq != null && childAnnotationsByParentType.containsKey(currentFq.getFullyQualifiedName())) {
                annotationsToAdd.addAll(
                        childAnnotationsByParentType.get(currentFq.getFullyQualifiedName()).stream()
                                // Verify it is not already available on this class (the parent)
                                .filter(annotation -> clsdecl.getLeadingAnnotations().stream()
                                        .noneMatch(la -> TypeUtils.asFullyQualified(la.getType()).getFullyQualifiedName()
                                                .equals(TypeUtils.asFullyQualified(annotation.getType()).getFullyQualifiedName())))
                                .toList());
            }

            // Then add it to this class
            if (!annotationsToAdd.isEmpty()) {
                List<J.Annotation> afterAnnotationList = ListUtils.concatAll(cd.getLeadingAnnotations(), annotationsToAdd);
                cd = cd.withLeadingAnnotations(afterAnnotationList);
                cd = autoFormat(cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
                for (J.Annotation annotation : annotationsToAdd) {
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(annotation.getType());
                    if (fullyQualified != null) {
                        maybeAddImport(fullyQualified.getFullyQualifiedName());
                    }
                }
            }

            // For every child this has an annotation that is copied remove the annotation, when move is true
            if (move
                    && currentFq != null
                    && currentFq.getSupertype() != null
                    && childAnnotationsByParentType.containsKey(currentFq.getSupertype().getFullyQualifiedName())) {
                cd = cd.withLeadingAnnotations(clsdecl.getLeadingAnnotations().stream()
                        .filter(la -> childAnnotationsByParentType.get(currentFq.getSupertype().getFullyQualifiedName()).stream()
                                .noneMatch(ca -> TypeUtils.asFullyQualified(la.getType()).getFullyQualifiedName()
                                        .equals(TypeUtils.asFullyQualified(ca.getType()).getFullyQualifiedName())))
                        .toList());
            }

            return cd;
        }
    }

    @Data
    protected static class Accumulator {
        final Map<String, List<J.Annotation>> childAnnotationsByParentType = new HashMap<>();
    }
}
