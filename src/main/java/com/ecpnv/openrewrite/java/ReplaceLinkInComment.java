package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * If a java doc link contains a certain class replace it with another class.
 * if the full class name was used it is shortened and the full class name is imported.
 * <p>
 * Parameter 'fullClassNameToReplace' is the full class name to be replaced.
 * Parameter 'fullClassNameToInsert' is the full class name to be inserted.
 * <p>
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceLinkInComment extends Recipe {

    @Option(displayName = "Full class name to replace",
            description = "An annotation matcher, expressed as a method pattern to replace.",
            example = "")
    String fullClassNameToReplace;

    @Option(displayName = "Full class name to insert",
            description = "An annotation template to add instead of original one, will be parsed with `JavaTemplate`.",
            example = "@org.jetbrains.annotations.NotNull(\"Null not permitted\")")
    String fullClassNameToInsert;

    @Override
    public String getDisplayName() {
        return "Replace annotation";
    }

    @Override
    public String getDescription() {
        return "Replace an Annotation with another one if the annotation pattern matches. " +
                "Only fixed parameters can be set in the replacement.";
    }

    @JsonCreator
    public ReplaceLinkInComment(
            @NonNull @JsonProperty("fullClassNameToReplace") String fullClassNameToReplace,
            @NonNull @JsonProperty("fullClassNameToInsert") String fullClassNameToInsert) {
        this.fullClassNameToReplace = fullClassNameToReplace;
        this.fullClassNameToInsert = fullClassNameToInsert;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public Expression visitExpression(Expression expression, ExecutionContext ctx) {
                if (expression instanceof J tree) {
                    if (CollectionUtils.isNotEmpty(tree.getComments()) && scanComments(tree.getComments(), fullClassNameToReplace)) {
                        maybeAddImport(fullClassNameToInsert, null, false);
                        maybeAddImport(fullClassNameToReplace, true);
                        return JavaTemplate.builder(expression.print(getCursor()))
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
                                .build()
                                .apply(getCursor(), expression.getCoordinates().replace())
                                .withPrefix(expression.getPrefix()
                                        .withWhitespace("\t")
                                        .withComments(replaceComment(expression.getComments(), fullClassNameToReplace, fullClassNameToInsert)));
                    }
                }
                return super.visitExpression(expression, ctx);
            }

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                if (statement instanceof J tree) {
                    if (CollectionUtils.isNotEmpty(tree.getComments()) && scanComments(tree.getComments(), fullClassNameToReplace)) {
                        doAfterVisit(new AddImport<>(fullClassNameToInsert, null, false));
                        doAfterVisit(new RemoveImport<>(fullClassNameToReplace, true));
                        return JavaTemplate.builder(statement.print(getCursor()))
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
                                .build()
                                .apply(getCursor(), statement.getCoordinates().replace())
                                .withPrefix(statement.getPrefix()
                                        .withWhitespace(statement.getPrefix().getWhitespace())
                                        .withComments(replaceComment(statement.getComments(), fullClassNameToReplace, fullClassNameToInsert)));
                    }
                }
                return super.visitStatement(statement, ctx);
            }

            private List<Comment> replaceComment(final List<Comment> comments,
                                                 final String annotationPatternToReplace,
                                                 final String annotationTemplateToInsert) {
                final List<Comment> newComments = new ArrayList<>();
                for (Comment comment : comments) {
                    Comment newComment = comment;
                    if (comment instanceof Javadoc.DocComment docComment) {
                        List<Javadoc> newBody = new ArrayList<>();
                        for (Javadoc javaDoc : docComment.getBody()) {
                            if (javaDoc instanceof Javadoc.Link javadocLink) {
                                if (javadocLink.getTreeReference() instanceof Javadoc.Reference reference) {
                                    if ((reference.getTree() instanceof J.Identifier identifier &&
                                            identifier.getSimpleName().equals(JavaType.ShallowClass.build(annotationPatternToReplace).getClassName())) ||
                                            (reference.getTree() instanceof J.FieldAccess fieldAccess &&
                                                    fieldAccess.getSimpleName().equals(JavaType.ShallowClass.build(annotationPatternToReplace).getClassName()))) {
                                        String simpleName = JavaType.ShallowClass.build(annotationTemplateToInsert).getClassName();
                                        J.Identifier newIdentifier = new J.Identifier(UUID.randomUUID(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), simpleName, JavaType.Unknown.getInstance(), null);
                                        Javadoc.Reference javadocReference = new Javadoc.Reference(UUID.randomUUID(), reference.getMarkers(), newIdentifier, reference.getLineBreaks());
                                        newBody.add(new Javadoc.Link(UUID.randomUUID(), javadocLink.getMarkers(), javadocLink.isPlain(), javadocLink.getSpaceBeforeTree(), javadocLink.getTree(), javadocReference, javadocLink.getLabel(), javadocLink.getEndBrace()));
                                    }
                                }
                            } else {
                                newBody.add(javaDoc);
                            }
                        }
                        newComments.add(new Javadoc.DocComment(UUID.randomUUID(), Markers.EMPTY, newBody, ""));
                    } else {
                        newComments.add(newComment);
                    }
                }
                return newComments;
            }

            private static boolean scanComments(final List<Comment> comments, final String annotationPatternToReplace) {
                for (Comment comment : comments) {
                    if (comment instanceof Javadoc.DocComment docComment) {
                        for (Javadoc javaDoc : docComment.getBody()) {
                            if (javaDoc instanceof Javadoc.Link javadocLink &&
                                    javadocLink.getTreeReference() instanceof Javadoc.Reference reference) {
                                if (reference.getTree() instanceof J.Identifier identifier) {
                                    if (identifier.getSimpleName().equals(JavaType.ShallowClass.build(annotationPatternToReplace).getClassName())) {
                                        return true;
                                    }
                                } else if (reference.getTree() instanceof J.FieldAccess fieldAccess) {
                                    if (fieldAccess.getSimpleName().equals(JavaType.ShallowClass.build(annotationPatternToReplace).getClassName())) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            }
        };
    }
}
