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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
public class MoveAnnotationFromFieldOrMethodToClass extends Recipe {

    @Option(displayName = "Annotation to match",
            description = "Only the annotation that matches will be moved.",
            example = "@javax.jdo.annotations.Index")
    String annotationPattern;

    @Option(displayName = "Use the field or method name as attribute value",
            description = "Optional: When this attribute name is specified, then it will be added to the annotation with the name of the field or method as value.",
            required = false,
            example = "@javax.jdo.annotations.Index")
    String attributeNameToAdd;


    @JsonCreator
    public MoveAnnotationFromFieldOrMethodToClass(
            @NonNull @JsonProperty("annotationPattern") String annotationPattern,
            @JsonProperty("attributeNameToAdd") String attributeNameToAdd) {
        this.annotationPattern = annotationPattern;
        this.attributeNameToAdd = attributeNameToAdd;
    }

    @Override
    public String getDisplayName() {
        return "Move annotation from field or method";
    }

    @Override
    public String getDescription() {
        return "This recipe moves the specified annotation from field or method to class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return Preconditions.check(new UsesType<>(annotationPattern, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                List<J.Annotation> annotationsToAddToClass = new ArrayList<>();
                List<Statement> modifiedStatements = classDecl.getBody().getStatements().stream()
                        .map(v -> {
                            // Search for fields and methods that have given annotation
                            Set<J.Annotation> curAnno = FindAnnotations.find(v, annotationPattern);
                            if (!curAnno.isEmpty()) {
                                String name;
                                if (v instanceof J.VariableDeclarations vd) {
                                    // When found then remove from field
                                    List<J.Annotation> la = vd.getLeadingAnnotations();
                                    curAnno = addAttributeName(curAnno, vd.getVariables().get(0).getSimpleName());
                                    la.removeAll(curAnno);
                                    v = vd.withLeadingAnnotations(la);
                                } else if (v instanceof J.MethodDeclaration md) {
                                    // When found then remove from method
                                    List<J.Annotation> la = md.getLeadingAnnotations();
                                    curAnno = addAttributeName(curAnno, md.getName().getSimpleName());
                                    la.removeAll(curAnno);
                                    v = md.withLeadingAnnotations(la);
                                }
                                annotationsToAddToClass.addAll(curAnno);
                            }
                            return v;
                        })
                        .toList();
                if (!annotationsToAddToClass.isEmpty()) {
                    // Replace modified statements
                    classDecl = classDecl.withBody(classDecl.getBody().withStatements(modifiedStatements));
                    List<J.Annotation> la = classDecl.getLeadingAnnotations();
                    annotationsToAddToClass.addAll(la);
                    // Add annotations from fields and methods
                    classDecl = classDecl.withLeadingAnnotations(annotationsToAddToClass);
                } else {
                    // When nothing found call super
                    classDecl = super.visitClassDeclaration(classDecl, executionContext);
                }
                return classDecl;
            }

            Set<J.Annotation> addAttributeName(Set<J.Annotation> curAnno, String name) {
                if (name != null && StringUtils.isNotBlank(attributeNameToAdd)) {
                    // Add an attribute to the annotation with the name of the field or method
                    curAnno = curAnno.stream()
                            .map(a -> {
                                J.Assignment as = (J.Assignment) ((J.Annotation) JavaTemplate.builder("#{} = #{}")
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), a.getCoordinates().replaceArguments(), attributeNameToAdd, '"' + name + '"'))
                                        .getArguments().get(0);
                                return a.withArguments(ListUtils.concat(as, a.getArguments()));
                            })
                            .collect(Collectors.toSet());
                }
                return curAnno;
            }
        });
    }
}
