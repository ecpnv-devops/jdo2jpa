package com.ecpnv.openrewrite.jdo2jpa;

import java.util.List;
import java.util.Set;

import com.ecpnv.openrewrite.util.RewriteUtils;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that looks if a class is annotated {@link javax.jdo.annotations.PersistenceCapable} with an
 * {@link javax.jdo.annotations.IdentityType#APPLICATION} and then add a to do comment.
 * <p>
 * With the {@link javax.jdo.annotations.IdentityType#APPLICATION} it implies that the entity uses a compound key
 * construction that will require a different migration then for {@link javax.jdo.annotations.IdentityType#DATASTORE}.
 * Migrating this to JPA with use of recipes is out of the scope since in our use case it only applies to 5 instances.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class AddCommentToPersistenceCapableWithIdentityTypeApplication extends Recipe {

    public static final String SOURCE_ANNOTATION_TYPE = "@" + Constants.Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL;

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add comment to persistence capable annotated with identity type application";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Add comment to persistence capable annotated with identity type application.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {

            @Override
            public @NotNull J.ClassDeclaration visitClassDeclaration(
                    @NotNull J.ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {

                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, SOURCE_ANNOTATION_TYPE);
                final List<Comment> comments = cd.getComments();
                if (!RewriteUtils.commentsContains(comments, Constants.Jpa.MIGRATION_COMMENT) && !sourceAnnotations.isEmpty()) {
                    final J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();
                    final String simpleName = RewriteUtils.findArgumentAssignment(sourceAnnotation, Constants.Jdo.IDENTITY_TYPE_ANNOTATION_NAME)
                            .map(J.Assignment::getAssignment)
                            .filter(J.FieldAccess.class::isInstance)
                            .map(J.FieldAccess.class::cast)
                            .map(J.FieldAccess::getSimpleName)
                            .orElse(null);

                    if (Constants.Jdo.IDENTITY_TYPE_APPLICATION.equals(simpleName)) {
                        return cd.withComments(List.of(new TextComment(true,
                                "%n%s%n".formatted(Constants.Jpa.MIGRATION_COMMENT),
                                "\n", Markers.EMPTY)));
                    }
                }
                return cd;
            }
        };
    }
}
