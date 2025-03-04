package com.ecpnv.openrewrite.java;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Conditional version of {@link org.openrewrite.java.ReplaceAnnotation} where the condition of replacing an annotation is
 * if the annotation to replace starts with the packageName parameter OR the annotation matches the given regular expression.
 * <p>
 * Main use of this class is to replace annotations of attributes of classes that are not basic java classes but part
 * of the Java module.
 * <p>
 * In this version the classpathResourceName does not to be used since this version uses the {@link JavaParserFactory}
 * where the classpath resources are handled.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceAnnotationConditionally extends Recipe {

    @Option(displayName = "Regular expression to match",
            description = "Only annotations that match the regular expression will be changed.",
            example = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)")
    String matchByRegularExpression;

    @Option(displayName = "Annotation template to insert",
            description = "An annotation template to add instead of original one, will be parsed with `JavaTemplate`.",
            example = "@org.jetbrains.annotations.NotNull(\"Null not permitted\")")
    String annotationTemplateToInsert;

    @Option(displayName = "Package name",
            description = "When the package name applies to the given attribute with the annotation to replace it will" +
                    "be replaced.",
            example = "com.package.entities",
            required = false)
    @Nullable
    String packageName;

    @JsonCreator
    public ReplaceAnnotationConditionally(
            @Nullable @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @NonNull @JsonProperty("annotationTemplateToInsert") String annotationTemplateToInsert,
            @Nullable @JsonProperty("packageName") String packageName) {
        this.matchByRegularExpression = matchByRegularExpression;
        this.annotationTemplateToInsert = annotationTemplateToInsert;
        this.packageName = packageName;
    }

    @Override
    public String getDisplayName() {
        return "Replace annotation";
    }

    @Override
    public String getDescription() {
        return "Replace an Annotation with another one if the annotation pattern matches. " +
                "Only fixed parameters can be set in the replacement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                JavaTemplate.Builder templateBuilder = JavaTemplate.builder(annotationTemplateToInsert).javaParser(JavaParserFactory.create(ctx));
                return new ReplaceAnnotationVisitor(matchByRegularExpression, templateBuilder.build(), packageName)
                        .visit(tree, ctx);
            }
        };
    }

    @EqualsAndHashCode(callSuper = false)
    public final class ReplaceAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final String matchByRegularExpression;
        private final JavaTemplate replacement;
        @Nullable
        private final String packageName;

        private Boolean variableStartsWithPackageName = false;

        public ReplaceAnnotationVisitor(String matchByRegularExpression, JavaTemplate replacement, @Nullable String packageName) {
            this.matchByRegularExpression = matchByRegularExpression;
            this.replacement = replacement;
            this.packageName = packageName;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            if (StringUtils.isNotBlank(packageName)) {
                variableStartsWithPackageName = Optional.ofNullable(multiVariable)
                        .map(J.VariableDeclarations::getTypeAsFullyQualified)
                        .map(JavaType.FullyQualified::getPackageName)
                        .map(name -> name.startsWith(packageName))
                        .orElse(Boolean.FALSE);
            }
            return super.visitVariableDeclarations(multiVariable, ctx);
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            if (!variableStartsWithPackageName && !a.toString().matches(matchByRegularExpression)) {
                return a;
            }

            maybeRemoveImport(TypeUtils.asFullyQualified(a.getType()));
            JavaCoordinates replaceCoordinate = a.getCoordinates().replace();
            a = ((J.Annotation) replacement.apply(getCursor(), replaceCoordinate)).withArguments(a.getArguments());
            doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(a));
            return a;
        }
    }
}
