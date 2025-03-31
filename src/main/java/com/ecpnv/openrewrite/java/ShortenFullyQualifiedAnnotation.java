package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;

import io.micrometer.core.instrument.util.StringUtils;


/**
 * A recipe that shortens fully qualified annotations.
 * If two colliding annotation shortnames are found the one that is imported the first will be short and the
 * other colliding ones will remain original.
 * <p>
 * The parameter 'fullClassname' is the full class name of the annotation to be shortened.
 * If the parameter is null all annotations found will be shortened.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class ShortenFullyQualifiedAnnotation extends Recipe {

    @Option(displayName = "Full class name of annotation",
            description = "Full class name of annotation to be shortened.",
            example = "lombok.ToString")
    @Nullable
    String fullClassName;

    @JsonCreator
    public ShortenFullyQualifiedAnnotation(@Nullable @JsonProperty("fullClassName") String fullClassName) {
        this.fullClassName = fullClassName;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Shorten Fully Qualified Annotation";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Shortens fully qualified annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            final List<J.Import> imports = new ArrayList<>();

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                imports.add(_import);
                return super.visitImport(_import, ctx);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {

                if (annotation.getAnnotationType() instanceof J.FieldAccess &&
                        annotation.getType() instanceof JavaType.Class aClass &&
                        (StringUtils.isBlank(fullClassName) ||
                                Objects.equals(fullClassName, aClass.getFullyQualifiedName()))) {
                    // works on the basis of first come, first serve
                    if (checkImports(imports, aClass)) {
                        J.Annotation newAnnotation = ((J.Annotation) JavaTemplate.builder("@" + aClass.getClassName())
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
                                .imports(aClass.getFullyQualifiedName())
                                .build()
                                .apply(getCursor(), annotation.getCoordinates().replace()))
                                .withArguments(annotation.getArguments());
                        if (aClass.getOwningClass() == null) {
                            doAfterVisit(new AddImport<>(aClass.getFullyQualifiedName(), null, false));
                        }
                        //keep track of the import
                        final J.Import importToAdd = new J.Import(randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                new JLeftPadded<>(Space.SINGLE_SPACE, Boolean.FALSE, Markers.EMPTY),
                                TypeTree.build(aClass.getFullyQualifiedName()).withPrefix(Space.SINGLE_SPACE),
                                null);
                        imports.add(importToAdd);
                        return newAnnotation;
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }

            private boolean checkImports(List<J.Import> imports, JavaType.Class aClass) {
                for (J.Import _import : imports) {
                    if (Objects.equals(aClass.getClassName(), _import.getClassName())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
}
