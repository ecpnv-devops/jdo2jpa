package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.java.tree.J;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnresolvedHierarchyErrorContractSpikeTest {

    @Test
    void returningHandlerRecordsTypedErrorAndFailingRecipeLeavesClassUnchanged() {
        List<Throwable> errors = new ArrayList<>();
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(errors::add);
        List<SourceFile> sources = parse(ctx, "package example; class Entity {}");

        RecipeRun run = new UnresolvedHierarchySpikeRecipe().run(
                new InMemoryLargeSourceSet(sources), ctx);

        assertThat(run.getChangeset().size()).isZero();
        assertThat(errors).singleElement().isInstanceOfSatisfying(
                UnresolvedEntityHierarchyException.class, error -> {
                    assertThat(error.getEntityName()).isEqualTo("example.Entity");
                    assertThat(error.getParentName()).isEqualTo("example.MissingParent");
                    assertThat(error.getRecipeName())
                            .isEqualTo(UnresolvedHierarchySpikeRecipe.class.getName());
                    assertThat(error.getRemediation())
                            .contains("application dependency classpath");
                });
    }

    @Test
    void throwingHandlerPropagatesNormally() {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(error -> {
            throw new IllegalStateException("runner rejected hierarchy error", error);
        });
        List<SourceFile> sources = parse(ctx, "package example; class Entity {}");

        assertThatThrownBy(() -> new UnresolvedHierarchySpikeRecipe().run(
                new InMemoryLargeSourceSet(sources), ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("runner rejected hierarchy error")
                .hasRootCauseInstanceOf(UnresolvedEntityHierarchyException.class);
    }

    @Test
    void acceptanceRunnerRejectsPartialOutputFromEarlierRecipe() {
        InMemoryExecutionContext parseContext = new InMemoryExecutionContext();
        List<SourceFile> sources = parse(parseContext, "package example; class Entity {}");
        Recipe sequence = new Recipe() {
            @Override
            public String getDisplayName() {
                return "Spike partial-output sequence";
            }

            @Override
            public String getDescription() {
                return "Apply an earlier edit before reporting an unresolved hierarchy.";
            }

            @Override
            public List<Recipe> getRecipeList() {
                return List.of(new MarkEntityRecipe(), new UnresolvedHierarchySpikeRecipe());
            }
        };

        AcceptanceRun result = runForAcceptance(sequence, sources);

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.partialChangeCount()).isEqualTo(1);
        assertThat(result.publishedSources()).isEmpty();
        assertThat(result.errors()).singleElement()
                .isInstanceOf(UnresolvedEntityHierarchyException.class);
    }

    private static List<SourceFile> parse(ExecutionContext ctx, String source) {
        return JavaParser.fromJavaVersion().build().parse(ctx, source).toList();
    }

    private static AcceptanceRun runForAcceptance(Recipe recipe, List<SourceFile> sources) {
        List<Throwable> errors = new ArrayList<>();
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(errors::add);
        RecipeRun run = recipe.run(new InMemoryLargeSourceSet(sources), ctx);
        int partialChanges = run.getChangeset().size();
        if (!errors.isEmpty()) {
            return new AcceptanceRun(1, List.of(), partialChanges, List.copyOf(errors));
        }
        List<SourceFile> published = run.getChangeset().getAllResults().stream()
                .map(result -> result.getAfter() == null ? result.getBefore() : result.getAfter())
                .toList();
        return new AcceptanceRun(0, published, partialChanges, List.of());
    }

    private record AcceptanceRun(
            int exitCode,
            List<SourceFile> publishedSources,
            int partialChangeCount,
            List<Throwable> errors) {
    }

    private static final class MarkEntityRecipe extends Recipe {
        @Override
        public String getDisplayName() {
            return "Spike earlier entity edit";
        }

        @Override
        public String getDescription() {
            return "Mark an entity before hierarchy resolution fails.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    return SearchResult.found(super.visitClassDeclaration(classDecl, ctx), "earlier edit");
                }
            };
        }
    }

    private static final class UnresolvedHierarchySpikeRecipe extends Recipe {
        @Override
        public String getDisplayName() {
            return "Spike unresolved hierarchy reporting";
        }

        @Override
        public String getDescription() {
            return "Report a typed unresolved hierarchy through the execution context.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                    ctx.getOnError().accept(new UnresolvedEntityHierarchyException(
                            "example.Entity",
                            "example.MissingParent",
                            UnresolvedHierarchySpikeRecipe.class.getName(),
                            "Add the missing parent and listener dependencies to the application dependency classpath."));
                    return cd;
                }
            };
        }
    }

    private static final class UnresolvedEntityHierarchyException extends RuntimeException {
        private final String entityName;
        private final String parentName;
        private final String recipeName;
        private final String remediation;

        private UnresolvedEntityHierarchyException(
                String entityName, String parentName, String recipeName, String remediation) {
            super("Cannot resolve " + parentName + " while processing " + entityName + " in " + recipeName
                    + ". " + remediation);
            this.entityName = entityName;
            this.parentName = parentName;
            this.recipeName = recipeName;
            this.remediation = remediation;
        }

        private String getEntityName() {
            return entityName;
        }

        private String getParentName() {
            return parentName;
        }

        private String getRecipeName() {
            return recipeName;
        }

        private String getRemediation() {
            return remediation;
        }
    }
}
