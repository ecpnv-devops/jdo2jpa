package com.ecpnv.openrewrite.jdo2jpa;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddSortedMethodToStreamMethods extends Recipe {

    public static final Pattern STREAM = Pattern.compile("java.util.stream.Stream");

    @Option(displayName = "Annotation type",
            description = "The fully qualified name of the annotation to filter on.",
            example = "javax.persistence.OneToMany")
    @NonNull
    String annotationType;

    @JsonCreator
    public AddSortedMethodToStreamMethods(@NonNull @JsonProperty("annotationType") String annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public @NlsRewrite.DisplayName @NotNull String getDisplayName() {
        return "Add sorted method to stream methods with given annotation";
    }

    @Override
    public @NlsRewrite.Description @NotNull String getDescription() {
        return "Add sorted method to stream methods with given annotation.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(annotationType, false), new JavaIsoVisitor<>() {

            @Override
            public J.@NotNull MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getMethodType() != null && method.getBody() != null &&
                        method.getMethodType().getReturnType().isAssignableFrom(STREAM) &&
                        method.getBody().getStatements().getFirst() instanceof J.Return oldReturn &&
                        oldReturn.getExpression() instanceof J.MethodInvocation oldMethodInvocation &&
                        !oldMethodInvocation.getName().getSimpleName().equals("sorted")) {
                    /*
                        Uses a template to create a new J.MethodInvocation instance that can be placed into the LST hierarchy.
                     */
                    final String template = oldReturn.print(getCursor()) + ".sorted()";
                    J.Return newReturn = JavaTemplate.builder(template)
                            .build()
                            .apply(new Cursor(getCursor(), oldReturn), oldReturn.getCoordinates().replace());
                    if (newReturn.getExpression() instanceof J.MethodInvocation addedMethodInvocation &&
                            oldMethodInvocation.getMethodType() != null) {
                        newReturn = newReturn.withExpression(addedMethodInvocation
                                .withSelect(oldMethodInvocation.withPrefix(Space.EMPTY))
                                .withMethodType(oldMethodInvocation.getMethodType()
                                        .withName(addedMethodInvocation.getSimpleName())));
                    }
                    return autoFormat(method.withBody(method.getBody().withStatements(List.of(newReturn))), ctx);
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        });
    }
}
