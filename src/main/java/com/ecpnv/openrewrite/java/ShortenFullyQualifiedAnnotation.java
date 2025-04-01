package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
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
 * If the parameter is null all annotations found without an owning class will be shortened.
 * Example of an annotation with an owning class would be '@lombok.String.Include'.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class ShortenFullyQualifiedAnnotation extends ScanningRecipe<ShortenFullyQualifiedAnnotation.Accumulator> {

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
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final Accumulator acc) {
        return new JavaIsoVisitor<>() {
            J.CompilationUnit cu;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                this.cu = cu;
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                if (cu != null) {
                    acc.addImport(cu, _import);
                }
                return super.visitImport(_import, ctx);
            }

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                if (cu != null) {
                    if (statement instanceof J.ClassDeclaration classDeclaration && classDeclaration.getType() instanceof JavaType.Class aClass) {
                        acc.addImport(cu, createImport(aClass));
                    } else if (statement instanceof J.FieldAccess fieldAccess && fieldAccess.getType() instanceof JavaType.Class aClass) {
                        acc.addImport(cu, createImport(aClass));
                    }
                }
                return super.visitStatement(statement, ctx);
            }

            @Override
            public Expression visitExpression(Expression expression, ExecutionContext ctx) {
                if (cu != null) {
                    if (expression instanceof JavaType.Class aClass) {
                        acc.addImport(cu, createImport(aClass));
                    } else if (expression instanceof J.Identifier identifier && identifier.getType() instanceof JavaType.Class aClass) {
                        acc.addImport(cu, createImport(aClass));
                    }
                }
                return super.visitExpression(expression, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Accumulator acc) {
        return new JavaIsoVisitor<>() {

            J.CompilationUnit cu;
            final List<J.Import> usedClasses = new ArrayList<>();
            final List<J.Import> usedImports = new ArrayList<>();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                this.cu = cu;
                Collection<J.Import> imports = acc.getImports(cu);
                if (CollectionUtils.isNotEmpty(imports)) {
                    usedClasses.addAll(imports);
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                usedImports.add(_import);
                return super.visitImport(_import, ctx);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {

                if (annotation.getAnnotationType() instanceof J.FieldAccess &&
                        annotation.getType() instanceof JavaType.Class aClass &&
                        (StringUtils.isBlank(fullClassName) ||
                                Objects.equals(fullClassName, aClass.getFullyQualifiedName()))) {

                    if (aClass.getOwningClass() == null && checkClassNamesAndImports(usedClasses, aClass)) {
                        J.Annotation newAnnotation = ((J.Annotation) JavaTemplate.builder("@" + aClass.getClassName())
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
                                .imports(aClass.getFullyQualifiedName())
                                .build()
                                .apply(getCursor(), annotation.getCoordinates().replace()))
                                .withArguments(annotation.getArguments());
                        if (checkImports(usedImports, aClass)) {
                            doAfterVisit(new AddImport<>(aClass.getFullyQualifiedName(), null, false));
                            //keep track of the handled class
                            final J.Import importToAdd = createImport(aClass);
                            usedImports.add(importToAdd);
                        }
                        return newAnnotation;
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }

            private boolean checkClassNamesAndImports(List<J.Import> classes, JavaType.Class aClass) {
                int countClassNames = 0;
                for (J.Import _import : classes) {
                    if (compareClassName(aClass, _import)) {
                        countClassNames++;
                    }
                }
                int countImports = 0;
                for (J.Import _import : classes) {
                    if (compareClassName(aClass, _import) &&
                            _import.getQualid().getType() instanceof JavaType.Class importClass &&
                            Objects.equals(importClass.getKind(), JavaType.FullyQualified.Kind.Class)) {
                        countImports++;
                    }
                }
                if (countClassNames > 1 && countImports > 0) {
                    return false;
                }
                return true;
            }

            private boolean checkImports(List<J.Import> imports, JavaType.Class aClass) {
                for (J.Import _import : imports) {
                    if (compareClassName(aClass, _import)) {
                        return false;
                    }
                }
                return true;
            }

            private static boolean compareClassName(JavaType.Class aClass, J.Import _import) {
                if (Objects.equals(aClass.getClassName(), _import.getClassName())) {
                    return true;
                } else return Objects.equals(_import.getClassName(), "*") &&
                        Objects.equals(aClass.getPackageName(), _import.getPackageName());
            }
        };
    }

    private static J.@NotNull Import createImport(JavaType.Class aClass) {
        return new J.Import(randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new JLeftPadded<>(Space.SINGLE_SPACE, Boolean.FALSE, Markers.EMPTY),
                ((J.FieldAccess) TypeTree.build(aClass.getFullyQualifiedName()))
                        .withPrefix(Space.SINGLE_SPACE)
                        .withType(aClass),
                null);
    }

    public static class Accumulator {

        final Map<String, List<J.Import>> listHashMap = new HashMap<>();

        public void addImport(final J.CompilationUnit cu, final J.Import anImport) {
            List<J.Import> usedClasses = listHashMap.computeIfAbsent(cu.getSourcePath().toString(), k -> new ArrayList<>());
            if (usedClasses.stream().noneMatch(i -> compare(i, anImport))) {
                usedClasses.add(anImport);
            }
        }

        private boolean compare(final J.Import i1, final J.Import i2) {
            if (i1.getQualid().getType() instanceof JavaType.Class c1 &&
                    i2.getQualid().getType() instanceof JavaType.Class c2 &&
                    Objects.equals(c1.getFullyQualifiedName(), c2.getFullyQualifiedName())) {
                return true;
            }
            return false;
        }

        public Collection<J.Import> getImports(final J.CompilationUnit cu) {
            return listHashMap.get(cu.getSourcePath().toString());
        }
    }
}
