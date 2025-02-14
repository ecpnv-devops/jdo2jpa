package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindMissingTypes;
import org.openrewrite.java.tree.J;

import com.ecpnv.openrewrite.util.JavaParserFactory;

/**
 * A recipe that adds a {@link org.springframework.boot.autoconfigure.domain.EntityScan} when it finds a
 * {@link org.springframework.context.annotation.Configuration} with a
 * {@link org.springframework.context.annotation.ComponentScan} and uses the package name for the entity scan path.
 * <p>
 * The parameter 'configSubPackageName' can be used to indicate the sub package where the spring configuration can be found.
 * The parameter 'entitySubPackageName' can be used to indicate the sub package where the entities can be found.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
public class AddEntityScanAnnotationConditionally extends Recipe {

    private static final String COMPONENT_SCAN_CLASS_NAME = "ComponentScan";
    private static final String COMPONENT_SCAN_FULL_CLASS = "org.springframework.context.annotation." + COMPONENT_SCAN_CLASS_NAME;
    private static final String COMPONENT_SCAN_ANNOTATION = "@" + COMPONENT_SCAN_FULL_CLASS;
    private static final String ENTITY_SCAN_CLASS_NAME = "EntityScan";
    private static final String ENTITY_SCAN_FULL_CLASS = "org.springframework.boot.autoconfigure.domain." + ENTITY_SCAN_CLASS_NAME;
    private static final String ENTITY_SCAN_FULL_ANNOTATION = "@" + ENTITY_SCAN_FULL_CLASS;

    @Option(displayName = "Configuration sub package name",
            description = "The sub package name where Spring configuration is located.",
            example = "config")
    @Nullable
    String configSubPackageName;

    @Option(displayName = "Entities sub package name",
            description = "The sub package name where entities are located.",
            example = "entities")
    @Nullable
    String entitySubPackageName;

    @JsonCreator
    public AddEntityScanAnnotationConditionally(
            @Nullable @JsonProperty("configSubPackageName") String configSubPackageName,
            @Nullable @JsonProperty("entitySubPackageName") String entitySubPackageName) {
        this.configSubPackageName = configSubPackageName;
        this.entitySubPackageName = entitySubPackageName;
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {


            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                doAfterVisit(new FindMissingTypes().getVisitor());
                return super.visitVariableDeclarations(multiVariable, executionContext);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final List<J.Annotation> annotations = cd.getLeadingAnnotations();
                final Set<J.Annotation> componentScanAnnotations = FindAnnotations.find(cd, COMPONENT_SCAN_ANNOTATION);

                J.Annotation entityScanAnnotation = annotations.stream()
                        .filter(annotation -> annotation.getSimpleName().contains(ENTITY_SCAN_CLASS_NAME))
                        .findFirst()
                        .orElse(null);

                if (CollectionUtils.isNotEmpty(componentScanAnnotations) && entityScanAnnotation == null) {
                    String packageName = classDecl.getType().getPackageName();
                    if (StringUtils.isNotBlank(configSubPackageName) && packageName.endsWith(configSubPackageName)) {
                        packageName = packageName.substring(0, packageName.length() - 1 - configSubPackageName.length());
                    }
                    if (StringUtils.isNotBlank(entitySubPackageName)) {
                        packageName += "." + entitySubPackageName;
                    }
                    final String template = ENTITY_SCAN_FULL_ANNOTATION + "({\"%s\"})".formatted(packageName);

                    return JavaTemplate.builder(template)
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(ENTITY_SCAN_FULL_CLASS)
                            .build()
                            .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }

                return cd;
            }
        };
    }
}