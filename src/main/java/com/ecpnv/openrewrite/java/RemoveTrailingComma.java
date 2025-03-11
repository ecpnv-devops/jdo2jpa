package com.ecpnv.openrewrite.java;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Recipe to remove trailing commas in Java array declarations.
 * This class provides functionality to detect and remove trailing commas
 * in arrays, ensuring that array declarations conform to Java's style and syntax rules.
 * It uses a visitor pattern to traverse and modify the Java syntax tree accordingly.
 * <p>
 * Note: this class is inspired on the org.openrewrite.kotlin.cleanup.RemoveTrailingComma recipe.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveTrailingComma extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove trailing comma in Java";
    }

    @Override
    public String getDescription() {
        return "Remove trailing commas in array declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TrailingCommaVisitor(false);
    }

    @AllArgsConstructor
    public static class TrailingCommaVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final boolean useTrailingComma;

        @Override
        public J.NewArray visitNewArray(J.NewArray newArray, ExecutionContext executionContext) {
            J.NewArray na = super.visitNewArray(newArray, executionContext);
            na = na.getPadding().withInitializer(handleTrailingComma(na.getPadding().getInitializer()));
            return na;
        }

        private <T extends J> JContainer<T> handleTrailingComma(JContainer<T> container) {
            if (container != null && container.getPadding() != null) {
                List<JRightPadded<T>> rps = container.getPadding().getElements();

                if (!rps.isEmpty()) {
                    JRightPadded<T> last = rps.get(rps.size() - 1);
                    JRightPadded<T> updated = last;
                    Markers markers = last.getMarkers();
                    Optional<TrailingComma> maybeTrailingComma = markers.findFirst(TrailingComma.class);

                    if (!useTrailingComma && maybeTrailingComma.isPresent()) {
                        markers = markers.removeByType(TrailingComma.class);
                        updated = last.withMarkers(markers).withAfter(merge(last.getAfter(), maybeTrailingComma.get().getSuffix()));
                    }

                    if (useTrailingComma && !maybeTrailingComma.isPresent()) {
                        markers = markers.add(new TrailingComma(UUID.randomUUID(), last.getAfter()));
                        updated = last.withMarkers(markers).withAfter(Space.EMPTY);
                    }

                    if (updated != last) {
                        JRightPadded<T> finalUpdated = updated;
                        rps = ListUtils.mapLast(rps, x -> finalUpdated);
                        container = container.getPadding().withElements(rps);
                    }
                }
            }

            return container;
        }

        public static Space merge(@Nullable Space s1, @Nullable Space s2) {
            if (s1 == null || s1.isEmpty()) {
                return s2 != null ? s2 : Space.EMPTY;
            } else if (s2 == null || s2.isEmpty()) {
                return s1;
            }

            if (s1.getComments().isEmpty()) {
                return Space.build(s1.getWhitespace() + s2.getWhitespace(), s2.getComments());
            } else {
                List<Comment> newComments = ListUtils.mapLast(s1.getComments(), c ->
                        c.withSuffix(c.getSuffix() + s2.getWhitespace()));
                newComments.addAll(s2.getComments());
                return Space.build(s1.getWhitespace(), newComments);
            }
        }
    }
}