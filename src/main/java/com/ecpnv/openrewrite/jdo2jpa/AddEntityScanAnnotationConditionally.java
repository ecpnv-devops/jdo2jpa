package com.ecpnv.openrewrite.jdo2jpa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonCreator;

import org.apache.commons.collections4.CollectionUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import com.ecpnv.openrewrite.util.JavaParserFactory;

/**
 * A recipe that adds a {@link org.springframework.boot.autoconfigure.domain.EntityScan} looking for entities and when
 * it finds a {@link org.springframework.context.annotation.Configuration} with a
 * {@link org.springframework.context.annotation.ComponentScan} and adds the package names of the found entites for the
 * entity scan path.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
public class AddEntityScanAnnotationConditionally extends ScanningRecipe<Set<String>> {

    private static final String COMPONENT_SCAN_CLASS_NAME = "ComponentScan";
    private static final String COMPONENT_SCAN_FULL_CLASS = "org.springframework.context.annotation." + COMPONENT_SCAN_CLASS_NAME;
    private static final String COMPONENT_SCAN_ANNOTATION = "@" + COMPONENT_SCAN_FULL_CLASS;
    private static final String ENTITY_SCAN_CLASS_NAME = "EntityScan";
    private static final String ENTITY_SCAN_FULL_CLASS = "org.springframework.boot.autoconfigure.domain." + ENTITY_SCAN_CLASS_NAME;
    private static final String ENTITY_SCAN_FULL_ANNOTATION = "@" + ENTITY_SCAN_FULL_CLASS;

    @JsonCreator
    public AddEntityScanAnnotationConditionally() {
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add Entity Scan Annotation Conditionally";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Add Entity Scan Annotation when class is annotated with @ComponentScan.";
    }

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> packageNames) {
        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                if (CollectionUtils.isNotEmpty(FindAnnotations.find(cd, Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL)) ||
                        CollectionUtils.isNotEmpty(FindAnnotations.find(cd, Constants.Jpa.ENTITY_ANNOTATION_FULL))) {
                    packageNames.add(cd.getType().getPackageName());
                }
                return cd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Set<String> packageNames) {
        return new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (CollectionUtils.isNotEmpty(packageNames)) {
                    final List<J.Annotation> annotations = cd.getLeadingAnnotations();
                    final Set<J.Annotation> componentScanAnnotations = FindAnnotations.find(cd, COMPONENT_SCAN_ANNOTATION);

                    J.Annotation entityScanAnnotation = getEntityScanAnnotation(annotations);
                    if (CollectionUtils.isNotEmpty(componentScanAnnotations)) {
                        if (entityScanAnnotation == null) {
                            final String packages = packageNames.stream()
                                    .map(name -> "\"%s\"".formatted(name))
                                    .collect(Collectors.joining(","));
                            final String template = ENTITY_SCAN_FULL_ANNOTATION + "({%s})".formatted(packages);

                            return JavaTemplate.builder(template)
                                    .javaParser(JavaParserFactory.create(ctx))
                                    .imports(ENTITY_SCAN_FULL_CLASS)
                                    .build()
                                    .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        } else if (entityScanAnnotation != null && entityScanAnnotation.getArguments().getFirst() instanceof J.NewArray newArray &&
                                newArray.getInitializer().stream()
                                        .filter(J.Literal.class::isInstance)
                                        .map(J.Literal.class::cast)
                                        .noneMatch(literal -> packageNames.contains(literal.getValue()))) {

                            List<String> mergedPackages = new ArrayList<>();
                            newArray.getInitializer().stream()
                                    .filter(J.Literal.class::isInstance)
                                    .map(J.Literal.class::cast)
                                    .forEach(literal -> mergedPackages.add(literal.getValue().toString()));
                            mergedPackages.addAll(packageNames);
                            final String packages = mergedPackages.stream()
                                    .map(name -> "\n\"%s\"".formatted(name))
                                    .collect(Collectors.joining(","));

                            final String template = ENTITY_SCAN_FULL_ANNOTATION + "({%s})".formatted(packages);

                            J.ClassDeclaration dummyCd = JavaTemplate.builder(template)
                                    .javaParser(JavaParserFactory.create(ctx))
                                    .imports(ENTITY_SCAN_FULL_CLASS)
                                    .build()
                                    .apply(getCursor(), cd.getCoordinates().replaceAnnotations());

                            J.Annotation newEntityScanAnnotation = getEntityScanAnnotation(dummyCd.getLeadingAnnotations());
                            replaceAnnotation(annotations, entityScanAnnotation, newEntityScanAnnotation);

                            return cd.withLeadingAnnotations(annotations);
                        }
                    }
                }

                return cd;
            }

            private static void replaceAnnotation(List<J.Annotation> annotations, J.Annotation oldAnnotation, J.Annotation newAnnotation) {
                final ListIterator<J.Annotation> listIterator = annotations.listIterator();
                while (listIterator.hasNext()) {
                    J.Annotation annotation = listIterator.next();
                    if (annotation == oldAnnotation) {
                        listIterator.set(newAnnotation.withPrefix(Space.build("\n", emptyList())));
                        break;
                    }
                }
            }

            private static J.Annotation getEntityScanAnnotation(final List<J.Annotation> annotations) {
                return annotations.stream()
                        .filter(annotation -> annotation.getSimpleName().contains(ENTITY_SCAN_CLASS_NAME))
                        .findFirst()
                        .orElse(null);
            }
        };
    }
}