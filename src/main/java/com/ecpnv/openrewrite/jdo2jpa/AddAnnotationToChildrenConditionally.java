package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static com.ecpnv.openrewrite.util.RewriteUtils.hasAnnotation;

/**
 * A recipe that adds an annotation to all children classes.
 * <p>
 * The 'fullClassName' is the name of the parent class.
 * The 'annotationPattern' is the annotation that should be inherited by the child classes.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
public class AddAnnotationToChildrenConditionally extends Recipe {

    @Option(displayName = "Full extending class name",
            description = "The fully qualified name of the extends class.",
            example = "a.SomeClass")
    @NonNull
    String fullClassName;

    @Option(displayName = "Annotation pattern",
            description = "An annotation pattern, expressed as a method pattern.",
            example = "lombok.NoArgsConstructor")
    @NonNull
    String annotationPattern;

    @JsonCreator
    public AddAnnotationToChildrenConditionally(
            @NonNull @JsonProperty("fullClassName") String fullClassName,
            @NonNull @JsonProperty("annotationPattern") String annotationPattern) {
        this.fullClassName = fullClassName;
        this.annotationPattern = annotationPattern;
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
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

                if (CollectionUtils.isEmpty(FindAnnotations.find(classDecl, annotationPattern)) &&
                        !hasAnnotation(classDecl.getLeadingAnnotations(), annotationPattern, getCursor()) &&
                        checkIsExtended(classDecl.getType(), fullClassName)) {
                    maybeAddImport(annotationPattern, null, false);
                    J.ClassDeclaration cd = JavaTemplate.builder(checkAnnotation(annotationPattern))
                            .imports(annotationPattern)
                            .build()
                            .apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    // this won't work in tests
                    doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(cd));
                    return cd;
                }

                return super.visitClassDeclaration(classDecl, ctx);
            }

            private String checkAnnotation(@NonNull String annotationPattern) {
                if (annotationPattern.startsWith("@")) {
                    return annotationPattern;
                }
                return "@" + annotationPattern;
            }

            private boolean checkIsExtended(@Nullable JavaType.FullyQualified type, String fullClassName) {
                if (type == null) {
                    return false;
                }
                if (fullClassName.equals(type.getFullyQualifiedName())) {
                    return true;
                }
                if (type.getSupertype() != null) {
                    return checkIsExtended(type.getSupertype(), fullClassName);
                }
                return false;
            }
        };
    }
}