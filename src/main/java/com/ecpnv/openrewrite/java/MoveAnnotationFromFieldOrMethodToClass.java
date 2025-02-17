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

import java.beans.Introspector;
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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import lombok.EqualsAndHashCode;

/**
 * This recipe is designed to move specific annotations from fields or methods to the class level.
 * It allows for selective annotation matching, and optionally includes a mechanism to add an
 * attribute to the annotation with the name of the field or method it is moved from.
 * <p>
 * Features:
 * - Moves annotations that match a specified pattern to the class level.
 * - Optionally adds an attribute to the annotation with the field or method name
 * as its value, when an attribute name is provided.
 * - Operates on class declarations, variable declarations, and method declarations.
 * <p>
 * Parameters:
 * - `annotationPattern`: Defines the annotation pattern to match. Only annotations that match
 * this pattern will be moved.
 * - `attributeNameToAdd`: An optional parameter specifying an attribute to include in the moved
 * annotation, with the name of the field or method being used as the value.
 */
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

            List<J.Annotation> annotationsToAddToClass;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                annotationsToAddToClass = new ArrayList<>();
                classDecl = super.visitClassDeclaration(classDecl, executionContext);
                if (!annotationsToAddToClass.isEmpty()) {
                    // Add annotations from fields and methods
                    List<J.Annotation> la = classDecl.getLeadingAnnotations();
                    annotationsToAddToClass.addAll(la);
                    classDecl = classDecl.withLeadingAnnotations(annotationsToAddToClass);
                }
                return classDecl;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, executionContext);
                Set<J.Annotation> removeAnno = processAnnotation(mv, mv.getVariables().get(0).getSimpleName());
                if (!removeAnno.isEmpty()) {
                    List<J.Annotation> newAnno = new ArrayList<>(mv.getLeadingAnnotations());
                    newAnno.removeAll(removeAnno);
                    mv = mv.withLeadingAnnotations(newAnno);
                }
                return mv;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                String name = md.getName().getSimpleName();
                if (StringUtils.isNotBlank(name)) {
                    name = name.substring(name.startsWith("is") ? 2 : name.length() > 2 ? 3 : name.length());
                    if (StringUtils.isNotBlank(name)) {
                        name = Introspector.decapitalize(name);
                        Set<J.Annotation> removeAnno = processAnnotation(md, name);
                        if (!removeAnno.isEmpty()) {
                            List<J.Annotation> newAnno = new ArrayList<>(md.getLeadingAnnotations());
                            newAnno.removeAll(removeAnno);
                            md = md.withLeadingAnnotations(newAnno);
                        }
                    }
                }
                return md;
            }

            Set<J.Annotation> processAnnotation(@NonNull J current, @NonNull String name) {
                if (name != null && StringUtils.isNotBlank(attributeNameToAdd)) {
                    Set<J.Annotation> removeAnno = FindAnnotations.find(current, annotationPattern);
                    Set<J.Annotation> curAnno = removeAnno.stream()
                            // Add an attribute to the annotation with the name of the field or method
                            .map(a -> {
                                J result = JavaTemplate.builder("#{} = #{}")
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), a.getCoordinates().replaceArguments(), attributeNameToAdd, '"' + name + '"');
                                Expression as = FindAnnotations
                                        .find(result, annotationPattern).stream()
                                        .findFirst()
                                        .flatMap(arg -> arg.getArguments().stream().findFirst())
                                        .orElse(null);
                                return a.withArguments(ListUtils.concat(as, a.getArguments()));
                            })
                            .collect(Collectors.toSet());
                    if (!curAnno.isEmpty()) {
                        annotationsToAddToClass.addAll(curAnno);
                        return removeAnno;
                    }
                }
                return Set.of();
            }
        });
    }
}
