package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.JavaParserFactory;

/**
 * A recipe that adds a {@link org.springframework.boot.autoconfigure.domain.EntityScan} when it finds a
 * {@link org.springframework.context.annotation.ComponentScan} and copies the arguments of those annotations.
 * <p>
 * After this recipe the full qualified classname of the EntityScan annotation needs to be shortened which can be done
 * with the {@link org.openrewrite.java.ShortenFullyQualifiedTypeReferences} or a similar variant.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
public class AddEntityScanAnnotationConditionally extends Recipe {

    private static final String COMPONENT_SCAN_CLASS_NAME = "org.springframework.context.annotation.ComponentScan";
    private static final String ENTITY_SCAN_CLASS_NAME = "EntityScan";
    private static final String ENTITY_SCAN_FULL_CLASS = "org.springframework.boot.autoconfigure.domain.EntityScan";
    private static final String ENTITY_SCAN_FULL_ANNOTATION = "@" + ENTITY_SCAN_FULL_CLASS;

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add Entity Scan Annotation Conditionally";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Add Entity Scan Annotation when class is annotated with @ComponentScan.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final List<J.Annotation> annotations = cd.getLeadingAnnotations();
                final Set<J.Annotation> componentScanAnnotations = FindAnnotations.find(cd, COMPONENT_SCAN_CLASS_NAME);

                J.Annotation entityScanAnnotation = annotations.stream()
                        .filter(annotation -> annotation.getSimpleName().contains(ENTITY_SCAN_CLASS_NAME))
                        .findFirst()
                        .orElse(null);

                if (CollectionUtils.isNotEmpty(componentScanAnnotations) && entityScanAnnotation == null) {
                    final List<String> elements = componentScanAnnotations.stream()
                            .map(this::findPackages)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    Collections.sort(elements);//sorting avoids quantum states in tests

                    String template;
                    if (CollectionUtils.isEmpty(elements)) {
                        template = ENTITY_SCAN_FULL_ANNOTATION;
                    } else if (elements.size() == 1) {
                        template = ENTITY_SCAN_FULL_ANNOTATION + "(basePackages = %s)".formatted(elements.stream()
                                .collect(Collectors.joining(",")));
                    } else {
                        template = ENTITY_SCAN_FULL_ANNOTATION + "(basePackages = {%s})".formatted(elements.stream()
                                .collect(Collectors.joining(",")));
                    }

                    return JavaTemplate.builder(template)
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(ENTITY_SCAN_FULL_CLASS)
                            .build()
                            .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }

                return cd;
            }

            private List<String> findPackages(J.Annotation annotation) {
                if (CollectionUtils.isEmpty(annotation.getArguments())) {
                    return Collections.emptyList();
                } else {
                    return annotation.getArguments().stream().map(arg -> {
                                final List<String> result = new ArrayList<>();
                                if (arg instanceof J.Assignment assignment) {
                                    if (assignment.getAssignment() instanceof J.Literal literal) {
                                        if (literal.getValue() instanceof String value) {
                                            result.add("\"%s\"".formatted(value));
                                        }
                                    }
                                }
                                return result;
                            })
                            .flatMap(List::stream)
                            .toList();
                }
            }
        };
    }
}