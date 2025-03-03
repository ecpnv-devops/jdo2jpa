package com.ecpnv.openrewrite.java;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class MoveMetaAnnotation extends Recipe {

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be moved.",
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @JsonCreator
    public MoveMetaAnnotation(
            @NonNull @JsonProperty("matchByRegularExpression") String matchByRegularExpression) {
        this.matchByRegularExpression = matchByRegularExpression;
    }

    @Override
    public String getDisplayName() {
        return "Move annotation from meta-annotation";
    }

    @Override
    public String getDescription() {
        return "Move matching annotation from meta annotation to field, method or class declaration where the meta annotation is present.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vars = super.visitVariableDeclarations(multiVariable, ctx);
                return (J.VariableDeclarations) moveAnnotation(vars, vars.getLeadingAnnotations(), ctx,
                        () -> vars.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

//            @Override
//            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
//                J.ClassDeclaration classD = super.visitClassDeclaration(classDecl, ctx);
//                if (declarationType != DeclarationType.CLASS) {
//                    return classD;
//                }
//                Pattern pattern = Pattern.compile(annotationType);
//                if (classD.getLeadingAnnotations().isEmpty() || classD.getLeadingAnnotations().stream()
//                        .anyMatch(a -> a.getAnnotationType().getType().isAssignableFrom(pattern))) {
//                    // Do nothing when the annotation already is present
//                    return classD;
//                }
//                return (J.ClassDeclaration) addAnnotationConditionally(classD.getLeadingAnnotations(), ctx,
//                        () -> classD.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)))
//                        .orElse(classD);
//            }
//
//            @Override
//            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
//                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
//                if (declarationType != DeclarationType.METHOD) {
//                    return m;
//                }
//                return (J.MethodDeclaration) addAnnotationConditionally(m, m.getLeadingAnnotations(), ctx,
//                        () -> m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
//            }

            public Statement moveAnnotation(Statement j, List<J.Annotation> annotations, ExecutionContext ctx,
                                            Supplier<JavaCoordinates> coordinates) {
                if (!FindAnnotations.find(j, annotationType).isEmpty()) {
                    // Do nothing when the annotation already is present
                    return j;
                }
                return moveAnnotation(annotations, ctx, coordinates).orElse(j);
            }

            public Optional<Statement> moveAnnotation(List<J.Annotation> annotations, ExecutionContext ctx,
                                                      Supplier<JavaCoordinates> coordinates) {
                return annotations.stream()
                        .filter(a -> a.toString().matches(matchByRegularExpression))
                        .findFirst()
                        .map(a -> {
                            // Add annotation to variable
                            maybeAddImport(annotationType);
                            return (Statement) JavaTemplate.builder(annotationTemplate)
                                    .javaParser(JavaParserFactory.create(ctx))
                                    .imports(annotationType)
                                    .build()
                                    .apply(getCursor(), coordinates.get());
                        });
            }
        };
    }
}
