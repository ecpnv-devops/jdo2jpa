package com.ecpnv.openrewrite.jdo2jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import com.ecpnv.openrewrite.java.ExtendWithClassForClass;
import com.ecpnv.openrewrite.java.UnresolvedEntityHierarchyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.MavenSettings;

import static org.assertj.core.api.Assertions.assertThat;

class EntityHierarchyMigrationTest extends BaseRewriteTest {
    private static final String LISTENER = "org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener";
    private static final Recipe LISTENERS = new AddCausewayEntityListener(LISTENER);
    @TempDir
    Path temporary;

    @Test
    void combinedConfiguredMigrationRepairsBothTurnoverShapesAndIsStable() {
        Recipe recipe = Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                "com.ecpnv.openrewrite.jdo2jpa.v2x.PersistenceCapable", "com.ecpnv.openrewrite.jdo2jpa.v2x.causeway");
        for (String spelling : List.of("identityType = IdentityType.DATASTORE", "identityType=IdentityType.DATASTORE")) {
            for (String name : List.of("TurnoverRollupRun", "TurnoverRollupRunOrchItem")) {
                List<SourceFile> input = parse("""
                        package example;
                        import javax.jdo.annotations.PersistenceCapable;
                        import javax.jdo.annotations.IdentityType;
                        @PersistenceCapable(schema = %s.SCHEMA, table = %s.TABLE_NAME, %s)
                        public class %s {
                            public static final String SCHEMA = "dbo";
                            public static final String TABLE_NAME = "%s";
                        }
                        """.formatted(name, name, spelling, name, name));
                List<SourceFile> output = run(recipe, input);
                J.ClassDeclaration entity = ((J.CompilationUnit) output.get(0)).getClasses().get(0);
                assertThat(org.openrewrite.java.search.FindMissingTypes.findMissingTypes((J.CompilationUnit) output.get(0)))
                        .as("normal type validation for %s", name).isEmpty();
                assertThat(entity.getExtends()).isNotNull();
                assertThat(entity.getType().getSupertype()).isEqualTo(entity.getExtends().getType());
                assertThat(TypeUtils.asFullyQualified(entity.getExtends().getType()).getFlags()).contains(Flag.Abstract);
                assertThat(output.get(0).printAll()).contains("@EntityListeners(" + LISTENER + ".class)");
                assertStable(recipe, output);
            }
        }
    }

    @Test
    void plansAncestorAdditionsIndependentOfFileOrder() {
        List<SourceFile> sources = parse(
                "package example; @javax.persistence.Entity public class Parent {}",
                "package example; @javax.persistence.Entity public class Child extends Parent {}");
        for (List<SourceFile> input : List.of(sources, List.of(sources.get(1), sources.get(0)))) {
            List<SourceFile> output = run(LISTENERS, input);
            assertThat(text(output, "Parent.java")).contains("@EntityListeners");
            assertThat(text(output, "Child.java")).doesNotContain("@EntityListeners");
            assertStable(LISTENERS, output);
        }
    }

    @Test
    void respectsCustomEmptyAbstractAndExcludedListeners() {
        List<SourceFile> output = run(LISTENERS, parse("""
                package example;
                @javax.persistence.Entity
                @javax.persistence.EntityListeners({})
                class EmptyOverride {}
                @javax.persistence.Entity
                @javax.persistence.EntityListeners({Thread.class, Object.class})
                class CustomOverride {}
                @javax.persistence.Entity
                abstract class AbstractWithoutListener {}
                @javax.persistence.EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                abstract class AbstractWithListener {}
                @javax.persistence.Entity
                class Inherits extends AbstractWithListener {}
                @javax.persistence.Entity
                @javax.persistence.ExcludeSuperclassListeners
                class Excludes extends AbstractWithListener {}
                @javax.persistence.Entity
                class NeedsListener extends AbstractWithoutListener {}
                class ConcreteWithoutListener {}
                @javax.persistence.Entity
                class ConcreteChild extends ConcreteWithoutListener {}
                """));
        J.CompilationUnit cu = (J.CompilationUnit) output.get(0);
        for (J.ClassDeclaration cd : cu.getClasses()) {
            long listeners = cd.getLeadingAnnotations().stream()
                    .filter(a -> TypeUtils.isOfClassType(a.getType(), "javax.persistence.EntityListeners")).count();
            assertThat(listeners).as(cd.getSimpleName()).isEqualTo(
                    List.of("AbstractWithoutListener", "Inherits", "ConcreteWithoutListener").contains(cd.getSimpleName()) ? 0 : 1);
        }
        assertThat(cu.printAll()).contains("@javax.persistence.EntityListeners({})")
                .contains("@javax.persistence.EntityListeners({Thread.class, Object.class})");
        assertStable(LISTENERS, output);
    }

    @Test
    void resolvesMavenDependenciesFromNearestPomWithoutPathsOnTheRecipe() throws Exception {
        Path repository = temporary.resolve("repository");
        installParent(repository, "1", "@Deprecated public abstract class Parent {}");
        installParent(repository, "2", "public class Parent {}");
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(e -> {
            throw new AssertionError(e);
        });
        MavenExecutionContextView.view(ctx).setMavenSettings(new MavenSettings(repository.toString(), null, null, null, null));
        SourceFile pom1 = pom(ctx, "one", "1");
        SourceFile pom2 = pom(ctx, "two", "2");
        SourceFile child1 = JavaParser.fromJavaVersion().build().parse(ctx, "package example; public class Child {}")
                .findFirst().orElseThrow().withSourcePath(Path.of("one/src/main/java/example/Child.java"));
        SourceFile child2 = JavaParser.fromJavaVersion().build().parse(ctx, "package example; public class Child {}")
                .findFirst().orElseThrow().withSourcePath(Path.of("two/src/main/java/example/Child.java"));
        List<SourceFile> output = run(new ExtendWithClassForClass("example.Child", "example.Parent"),
                List.of(pom1, child1, pom2, child2));
        J.ClassDeclaration first = ((J.CompilationUnit) output.get(1)).getClasses().get(0);
        J.ClassDeclaration second = ((J.CompilationUnit) output.get(3)).getClasses().get(0);
        assertThat(first.getType().getSupertype()).isEqualTo(first.getExtends().getType());
        assertThat(second.getType().getSupertype()).isEqualTo(second.getExtends().getType());
        assertThat(first.getType().getSupertype().getFlags()).contains(Flag.Abstract);
        assertThat(first.getType().getSupertype().getAnnotations()).extracting(JavaType.FullyQualified::getFullyQualifiedName)
                .contains("java.lang.Deprecated");
        assertThat(second.getType().getSupertype().getFlags()).doesNotContain(Flag.Abstract);
        assertThat(second.getType().getSupertype().getAnnotations()).isEmpty();
    }

    @Test
    void mainRecipePreconditionsStillAllowMavenMetadataScanning() throws Exception {
        Path repository = temporary.resolve("repository");
        installParent(repository, "1", "@Deprecated public abstract class EntityAbstract {}",
                "org.estatio.base.prod.dom.EntityAbstract");
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(e -> {
            throw new AssertionError(e);
        });
        MavenExecutionContextView.view(ctx).setMavenSettings(new MavenSettings(repository.toString(), null, null, null, null));
        SourceFile pom = pom(ctx, "child", "1");
        SourceFile child = parse("""
                package example;
                @javax.jdo.annotations.PersistenceCapable(identityType = javax.jdo.annotations.IdentityType.DATASTORE)
                public class Child {}
                """).get(0).withSourcePath(Path.of("child/src/main/java/example/Child.java"));
        Recipe recipe = Environment.builder().scanRuntimeClasspath().build().activateRecipes("com.ecpnv.openrewrite.jdo2jpa.v2x");
        List<SourceFile> output = run(recipe, List.of(pom, child));
        JavaType.FullyQualified parent = ((J.CompilationUnit) output.get(1)).getClasses().get(0).getType().getSupertype();
        assertThat(parent.getAnnotations()).extracting(JavaType.FullyQualified::getFullyQualifiedName)
                .contains("java.lang.Deprecated");
    }

    @Test
    void moduleDependencyListenersUseTransitiveAnnotationBytecodeAndRespectExclusions() throws Exception {
        Path repository = temporary.resolve("repository");
        installParent(repository, "1", "@javax.persistence.EntityListeners(Thread.class) public abstract class Parent {}");
        installParent(repository, "2", "@javax.persistence.EntityListeners(Object.class) public abstract class Parent {}");
        installParent(repository, "3", "public abstract class Parent {}");
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(e -> {
            throw new AssertionError(e);
        });
        MavenExecutionContextView.view(ctx).setMavenSettings(new MavenSettings(repository.toString(), null, null, null, null));
        Recipe sequence = sequence(new ExtendWithClassForClass("example.Child", "example.Parent"),
                new AddCausewayEntityListener("java.lang.Thread"));
        for (String version : List.of("1", "2", "3")) {
            for (boolean exclude : List.of(false, true)) {
                SourceFile pom = pom(ctx, "child", version);
                SourceFile child = parse("package example; @javax.persistence.Entity "
                        + (exclude ? "@javax.persistence.ExcludeSuperclassListeners " : "")
                        + "public class Child {}").get(0).withSourcePath(Path.of("child/src/main/java/example/Child.java"));
                List<SourceFile> output = run(sequence, List.of(pom, child));
                boolean own = text(output, "Child.java").contains("@EntityListeners(java.lang.Thread.class)");
                assertThat(own).as("parent %s, exclude %s", version, exclude).isEqualTo(exclude || !"1".equals(version));
                assertStable(sequence, output);
            }
        }
    }

    @Test
    void missingListenerHierarchyIsUnchangedAndReturningHandlerReceivesTypedError() {
        List<Throwable> errors = new ArrayList<>();
        List<SourceFile> input = parse("package example; @javax.persistence.Entity class Child extends missing.Parent {}");
        var result = LISTENERS.run(new InMemoryLargeSourceSet(input), new InMemoryExecutionContext(errors::add));
        assertThat(result.getChangeset().size()).isZero();
        assertThat(errors).isNotEmpty().allSatisfy(e -> assertThat(e).isInstanceOf(UnresolvedEntityHierarchyException.class));
    }

    @Test
    void recoversUnknownExtendsThroughSourceImports() {
        List<SourceFile> sources = new ArrayList<>(parse(
                "package base; public abstract class Parent {}",
                "package example; import base.Parent; @javax.persistence.Entity public class Child extends Parent {}"));
        J.CompilationUnit child = (J.CompilationUnit) sources.get(1);
        J.ClassDeclaration cd = child.getClasses().get(0);
        sources.set(1, child.withClasses(List.of(cd.withExtends(cd.getExtends().withType(JavaType.Unknown.getInstance())))));
        List<SourceFile> output = run(LISTENERS, sources);
        assertThat(text(output, "Child.java")).contains("@EntityListeners(" + LISTENER + ".class)");
    }

    @Test
    void productionErrorPropagatesAndDoesNotRollBackEarlierRecipeOutput() {
        List<SourceFile> sources = parse("package example; @javax.persistence.Entity class Child extends missing.Parent {}");
        Recipe earlier = new Recipe() {
            @Override
            public String getDisplayName() {
                return "Earlier edit";
            }

            @Override
            public String getDescription() {
                return "Prove the failure does not claim transactional rollback.";
            }

            @Override
            public org.openrewrite.TreeVisitor<?, org.openrewrite.ExecutionContext> getVisitor() {
                return new org.openrewrite.java.JavaIsoVisitor<org.openrewrite.ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, org.openrewrite.ExecutionContext ctx) {
                        return org.openrewrite.marker.SearchResult.found(cd, "earlier edit");
                    }
                };
            }
        };
        List<Throwable> errors = new ArrayList<>();
        var run = sequence(earlier, LISTENERS).run(new InMemoryLargeSourceSet(sources), new InMemoryExecutionContext(errors::add));
        assertThat(errors).isNotEmpty().allSatisfy(e -> assertThat(e).isInstanceOf(UnresolvedEntityHierarchyException.class));
        assertThat(run.getChangeset().getAllResults()).singleElement().satisfies(r ->
                assertThat(r.getAfter().printAll()).contains("earlier edit").doesNotContain("@EntityListeners"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LISTENERS.run(new InMemoryLargeSourceSet(sources),
                new InMemoryExecutionContext(e -> {
                    throw new IllegalStateException("rejected", e);
                })))
                .isInstanceOf(IllegalStateException.class).hasRootCauseInstanceOf(UnresolvedEntityHierarchyException.class);
    }

    private Recipe sequence(Recipe... recipes) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return "Test entity composition";
            }

            @Override
            public String getDescription() {
                return "Compose actual production superclass and listener recipes.";
            }

            @Override
            public List<Recipe> getRecipeList() {
                return List.of(recipes);
            }
        };
    }

    @Test
    void unavailableParentReportsTypedErrorWithoutPartialSuperclassOrImports() {
        List<Throwable> errors = new ArrayList<>();
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(errors::add);
        List<SourceFile> input = parse("package example; public class Child {}");
        var result = new ExtendWithClassForClass("example.Child", "missing.Parent")
                .run(new InMemoryLargeSourceSet(input), ctx);
        assertThat(result.getChangeset().size()).isZero();
        assertThat(errors).singleElement().isInstanceOfSatisfying(UnresolvedEntityHierarchyException.class, e -> {
            assertThat(e.getEntityName()).isEqualTo("example.Child");
            assertThat(e.getParentName()).isEqualTo("missing.Parent");
            assertThat(e.getRecipeName()).isEqualTo(ExtendWithClassForClass.class.getName());
            assertThat(e.getRemediation()).contains("application dependency classpath");
        });
    }

    private SourceFile pom(InMemoryExecutionContext ctx, String module, String version) {
        return MavenParser.builder().build().parse(ctx, """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>%s</artifactId><version>1</version>
                <dependencies><dependency><groupId>example</groupId><artifactId>parent</artifactId><version>%s</version></dependency></dependencies>
                </project>
                """.formatted(module, version)).findFirst().orElseThrow().withSourcePath(Path.of(module + "/pom.xml"));
    }

    private void installParent(Path repository, String version, String declaration) throws Exception {
        installParent(repository, version, declaration, "example.Parent");
    }

    private void installParent(Path repository, String version, String declaration, String name) throws Exception {
        Path root = temporary.resolve("compile-" + version);
        Path source = root.resolve("src/" + name.replace('.', '/') + ".java");
        Path classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, "package " + name.substring(0, name.lastIndexOf('.')) + "; " + declaration);
        Path api = Path.of("src/main/resources/META-INF/rewrite/classpath/jakarta.persistence-api-2.2.3.jar").toAbsolutePath();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-classpath", api.toString(), "-d", classes.toString(), source.toString())).isZero();
        Path annotations = repository.resolve("example/annotations/1/annotations-1");
        Files.createDirectories(annotations.getParent());
        Files.copy(api, Path.of(annotations + ".jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(Path.of(annotations + ".pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>annotations</artifactId><version>1</version></project>");
        Path artifact = repository.resolve("example/parent/" + version + "/parent-" + version);
        Files.createDirectories(artifact.getParent());
        Files.writeString(Path.of(artifact + ".pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>parent</artifactId><version>" + version + "</version><dependencies><dependency><groupId>example</groupId><artifactId>annotations</artifactId><version>1</version></dependency></dependencies></project>");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(Path.of(artifact + ".jar")));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(file).toString()));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
    }

    private List<SourceFile> parse(String... sources) {
        return PARSER.clone().build().parse(new InMemoryExecutionContext(e -> {
            throw new AssertionError(e);
        }), sources).toList();
    }

    private List<SourceFile> run(Recipe recipe, List<SourceFile> sources) {
        List<Throwable> errors = new ArrayList<>();
        var run = recipe.run(new InMemoryLargeSourceSet(sources), new InMemoryExecutionContext(errors::add));
        assertThat(errors).isEmpty();
        Map<Path, SourceFile> output = new LinkedHashMap<>();
        sources.forEach(s -> output.put(s.getSourcePath(), s));
        run.getChangeset().getAllResults().forEach(r -> output.put(r.getAfter().getSourcePath(), r.getAfter()));
        return List.copyOf(output.values());
    }

    private void assertStable(Recipe recipe, List<SourceFile> output) {
        var run = recipe.run(new InMemoryLargeSourceSet(output), new InMemoryExecutionContext(e -> {
            throw new AssertionError(e);
        }));
        assertThat(run.getChangeset().size()).isZero();
    }

    private String text(List<SourceFile> sources, String suffix) {
        return sources.stream().filter(s -> s.getSourcePath().toString().endsWith(suffix)).findFirst().orElseThrow().printAll();
    }
}
