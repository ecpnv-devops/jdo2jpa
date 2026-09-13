package com.ecpnv.openrewrite.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.ToolProvider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import com.ecpnv.openrewrite.util.JavaParserFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class SuperclassAttributionSpikeTest extends BaseRewriteTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesSourceDefinedSuperclassForTheNextVisitor() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new InsertResolvedSuperclass("example.ApplicationParent")),
                java(
                        """
                                package example;

                                @Deprecated
                                public abstract class ApplicationParent {}
                                """),
                java(
                        """
                                package example;

                                public class Child {}
                                """,
                        """
                                package example;

                                public class Child extends ApplicationParent {}
                                """,
                        source -> source.afterRecipe(SuperclassAttributionSpikeTest::assertResolvedParent)));
    }

    @Test
    void resolvesDependencyDefinedSuperclassOutsideTheTemplateClasspath() throws IOException {
        Path dependency = compileDependency(
                "example.DependencyParent",
                """
                        package example;

                        @Deprecated
                        public abstract class DependencyParent {}
                        """);
        JavaSourceSet sourceSet = JavaSourceSet.build("dependency-spike", List.of(dependency));

        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new InsertResolvedSuperclass("example.DependencyParent", List.of(dependency.toString()))),
                java(
                        """
                                package example;

                                public class Child {}
                                """,
                        """
                                package example;

                                public class Child extends DependencyParent {}
                                """,
                        source -> source.markers(sourceSet)
                                .afterRecipe(SuperclassAttributionSpikeTest::assertResolvedParent)));
    }

    @Test
    void preservesDependencyListenerClassArrayValues() throws IOException {
        Path persistenceApi = Path.of("src/main/resources/META-INF/rewrite/classpath/jakarta.persistence-api-2.2.3.jar")
                .toAbsolutePath();
        Path dependency = compileDependency(
                "example.DependencyParent",
                """
                        package example;

                        @Deprecated
                        @javax.persistence.EntityListeners(DependencyParent.Callback.class)
                        public abstract class DependencyParent {
                            public static class Callback {}
                        }
                        """,
                List.of(persistenceApi));
        JavaSourceSet sourceSet = JavaSourceSet.build("listener-dependency-spike", List.of(dependency, persistenceApi));

        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new InsertResolvedSuperclass("example.DependencyParent",
                                List.of(dependency.toString(), persistenceApi.toString()))),
                java(
                        """
                                package example;

                                public class Child {}
                                """,
                        """
                                package example;

                                public class Child extends DependencyParent {}
                                """,
                        source -> source.markers(sourceSet).afterRecipe(cu -> {
                            assertResolvedParent(cu);
                            JavaType.FullyQualified parent = TypeUtils.asFullyQualified(cu.getClasses().get(0)
                                    .getExtends().getType());
                            JavaType.Annotation listeners = (JavaType.Annotation) parent.getAnnotations().stream()
                                    .filter(a -> "javax.persistence.EntityListeners".equals(a.getFullyQualifiedName()))
                                    .findFirst().orElseThrow();
                            assertThat(listeners.getValues()).hasSize(1);
                            JavaType.Annotation.ArrayElementValue value =
                                    (JavaType.Annotation.ArrayElementValue) listeners.getValues().get(0);
                            assertThat(value.getReferenceValues()).hasSize(1);
                            assertThat(TypeUtils.asFullyQualified(value.getReferenceValues()[0]).getFullyQualifiedName())
                                    .isEqualTo("example.DependencyParent$Callback");
                        })));
    }

    @Test
    void separateChildInvocationReadsListenerFromMigratedParentArtifact() throws IOException {
        Path persistenceApi = persistenceApi();
        Path listenerDependency = compileDependency(
                "org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener",
                """
                        package org.apache.isis.persistence.jpa.applib.integration;

                        public class IsisEntityListener {}
                        """);
        String migratedParent = """
                package example;

                import javax.persistence.Entity;
                import javax.persistence.EntityListeners;

                @Entity
                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                public class DependencyParent {}
                """;
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                java(
                        """
                                package example;

                                import javax.persistence.Entity;

                                @Entity
                                public class DependencyParent {}
                                """,
                        migratedParent));

        List<Path> applicationClasspath = List.of(persistenceApi, listenerDependency);
        Path dependency = compileDependency("example.DependencyParent", migratedParent, applicationClasspath);
        JavaSourceSet sourceSet = JavaSourceSet.build(
                "migrated-parent-module", List.of(dependency, persistenceApi, listenerDependency));
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new InsertResolvedSuperclass("example.DependencyParent",
                                List.of(dependency.toString(), persistenceApi.toString(),
                                        listenerDependency.toString()))),
                java(
                        """
                                package example;

                                public class Child {}
                                """,
                        """
                                package example;

                                public class Child extends DependencyParent {}
                                """,
                        source -> source.markers(sourceSet).afterRecipe(cu -> {
                            assertConsistentParent(cu.getClasses().get(0));
                            assertListenerIdentity(cu,
                                    "org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener", true);
                        })));
    }

    @Test
    void separateChildInvocationDistinguishesParentWithoutConfiguredListener() throws IOException {
        Path persistenceApi = persistenceApi();
        String migratedParent = """
                package example;

                import javax.persistence.Entity;
                import javax.persistence.EntityListeners;

                @Entity
                @EntityListeners(DependencyParent.OtherListener.class)
                public class DependencyParent {
                    public static class OtherListener {}
                }
                """;
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                java(migratedParent));

        Path dependency = compileDependency("example.DependencyParent", migratedParent, List.of(persistenceApi));
        JavaSourceSet sourceSet = JavaSourceSet.build(
                "custom-parent-module", List.of(dependency, persistenceApi));
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new InsertResolvedSuperclass("example.DependencyParent",
                                List.of(dependency.toString(), persistenceApi.toString()))),
                java(
                        """
                                package example;

                                public class Child {}
                                """,
                        """
                                package example;

                                public class Child extends DependencyParent {}
                                """,
                        source -> source.markers(sourceSet).afterRecipe(cu -> {
                            assertConsistentParent(cu.getClasses().get(0));
                            assertListenerIdentity(cu, "example.DependencyParent$Callback", false);
                            assertListenerIdentity(cu, "example.DependencyParent$OtherListener", true);
                        })));
    }

    @Test
    void sourceSetIndexIsShallowRatherThanEvidenceOfAnUnannotatedConcreteClass() throws IOException {
        Path dependency = compileDependency("example.DependencyParent", """
                package example;
                @Deprecated
                public abstract class DependencyParent {}
                """);
        JavaType.FullyQualified indexed = JavaSourceSet.build("shallow-control", List.of(dependency))
                .getClasspath().stream()
                .filter(t -> "example.DependencyParent".equals(t.getFullyQualifiedName()))
                .findFirst().orElseThrow();
        assertThat(indexed).isInstanceOf(JavaType.ShallowClass.class);
        assertThat(indexed.getFlags()).doesNotContain(org.openrewrite.java.tree.Flag.Abstract);
        assertThat(indexed.getAnnotations()).isEmpty();
    }

    private static void assertResolvedParent(J.CompilationUnit compilationUnit) {
        assertResolvedParent(compilationUnit.getClasses().get(0));
    }

    private static void assertResolvedParent(J.ClassDeclaration child) {
        JavaType.Class parentType = assertConsistentParent(child);

        assertThat(parentType.getFullyQualifiedName()).isIn(
                "example.ApplicationParent", "example.DependencyParent");
        assertThat(parentType.getFlags())
                .as("flags and annotations for %s: %s", parentType, parentType.getAnnotations())
                .contains(org.openrewrite.java.tree.Flag.Abstract);
        assertThat(parentType.getAnnotations())
                .extracting(JavaType.FullyQualified::getFullyQualifiedName)
                .contains("java.lang.Deprecated");
    }

    private static JavaType.Class assertConsistentParent(J.ClassDeclaration child) {
        JavaType.Class childType = TypeUtils.asClass(child.getType());
        JavaType.Class parentType = TypeUtils.asClass(child.getExtends().getType());

        assertThat(childType).isNotNull();
        assertThat(parentType).isNotNull();
        assertThat(childType.getSupertype()).isEqualTo(parentType);
        return parentType;
    }

    private static void assertListenerIdentity(
            J.CompilationUnit compilationUnit, String listenerName, boolean expected) {
        JavaType.FullyQualified parent = TypeUtils.asFullyQualified(compilationUnit.getClasses().get(0)
                .getExtends().getType());
        boolean found = parent.getAnnotations().stream()
                .filter(JavaType.Annotation.class::isInstance)
                .map(JavaType.Annotation.class::cast)
                .filter(annotation -> "javax.persistence.EntityListeners".equals(annotation.getFullyQualifiedName()))
                .flatMap(annotation -> annotation.getValues().stream())
                .filter(JavaType.Annotation.ArrayElementValue.class::isInstance)
                .map(JavaType.Annotation.ArrayElementValue.class::cast)
                .flatMap(value -> java.util.Arrays.stream(value.getReferenceValues()))
                .map(TypeUtils::asFullyQualified)
                .anyMatch(listener -> listener != null && listenerName.equals(listener.getFullyQualifiedName()));
        assertThat(found).isEqualTo(expected);
    }

    private static Path persistenceApi() {
        return Path.of("src/main/resources/META-INF/rewrite/classpath/jakarta.persistence-api-2.2.3.jar")
                .toAbsolutePath();
    }

    private Path compileDependency(String fullyQualifiedName, String source) throws IOException {
        return compileDependency(fullyQualifiedName, source, List.of());
    }

    private Path compileDependency(String fullyQualifiedName, String source, List<Path> classpath) throws IOException {
        Path root = Files.createTempDirectory(temporaryDirectory, "dependency");
        Path sourceFile = root.resolve("src/" + fullyQualifiedName.replace('.', '/') + ".java");
        Path classes = root.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source);

        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString()));
        if (!classpath.isEmpty()) {
            arguments.addAll(List.of("-classpath", classpath.stream().map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator))));
        }
        arguments.add(sourceFile.toString());
        int result = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertThat(result).isZero();

        Path jar = root.resolve("dependency.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
             var paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString()));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    public static final class InsertResolvedSuperclass
            extends ScanningRecipe<Map<String, JavaType.FullyQualified>> {

        private final String superclassName;
        private final List<String> applicationClasspath;

        public InsertResolvedSuperclass(String superclassName) {
            this(superclassName, List.of());
        }

        @JsonCreator
        public InsertResolvedSuperclass(@JsonProperty("superclassName") String superclassName,
                                        @JsonProperty("applicationClasspath") List<String> applicationClasspath) {
            this.superclassName = superclassName;
            this.applicationClasspath = applicationClasspath;
        }

        public String getSuperclassName() {
            return superclassName;
        }

        public List<String> getApplicationClasspath() {
            return applicationClasspath;
        }

        @Override
        public String getDisplayName() {
            return "Spike resolved superclass insertion";
        }

        @Override
        public String getDescription() {
            return "Prove that source and dependency superclass metadata can be retained after insertion.";
        }

        @Override
        public Map<String, JavaType.FullyQualified> getInitialValue(ExecutionContext ctx) {
            Map<String, JavaType.FullyQualified> types = new HashMap<>();
            if (!applicationClasspath.isEmpty()) {
                // A private parser reads actual application bytecode; this source is never emitted.
                // JavaSourceSet is a name index, not a source of flags or annotation metadata.
                J.CompilationUnit probe = (J.CompilationUnit) JavaParser.fromJavaVersion()
                        .classpath(applicationClasspath.stream().map(Path::of).toList())
                        .build()
                        .parse(ctx, "class __SuperclassMetadataProbe { " + superclassName + " parent; }")
                        .findFirst().orElseThrow();
                J.VariableDeclarations field = (J.VariableDeclarations) probe.getClasses().get(0)
                        .getBody().getStatements().get(0);
                JavaType.FullyQualified parent = TypeUtils.asFullyQualified(field.getTypeExpression().getType());
                assertThat(parent).isNotNull().isNotInstanceOf(JavaType.ShallowClass.class);
                types.put(parent.getFullyQualifiedName(), parent);
            }
            return types;
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getScanner(Map<String, JavaType.FullyQualified> types) {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                    cu.getMarkers().findFirst(JavaSourceSet.class).ifPresent(sourceSet ->
                            sourceSet.getClasspath().forEach(type -> types.putIfAbsent(type.getFullyQualifiedName(), type)));
                    return super.visitCompilationUnit(cu, ctx);
                }

                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(classDecl.getType());
                    if (type != null) {
                        types.put(type.getFullyQualifiedName(), type);
                    }
                    return super.visitClassDeclaration(classDecl, ctx);
                }
            };
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, JavaType.FullyQualified> types) {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                    J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                    if (!"Child".equals(cd.getSimpleName()) || cd.getExtends() != null) {
                        return cd;
                    }

                    JavaType.FullyQualified parentType = types.get(superclassName);
                    if (parentType == null) {
                        return cd;
                    }

                    maybeAddImport(superclassName);
                    J.ClassDeclaration extended = JavaTemplate.builder(parentType.getClassName())
                            .contextSensitive()
                            .javaParser(JavaParserFactory.create(ctx))
                            .imports(superclassName)
                            .build()
                            .apply(getCursor(), cd.getCoordinates().replaceExtendsClause());
                    J.Identifier parent = (J.Identifier) extended.getExtends();
                    JavaType.Class childType = TypeUtils.asClass(extended.getType());
                    J.ClassDeclaration resolved = extended
                            .withExtends(parent.withType(parentType))
                            .withType(childType.withSupertype(parentType));
                    doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.ClassDeclaration visitClassDeclaration(
                                J.ClassDeclaration classDeclaration, ExecutionContext executionContext) {
                            J.ClassDeclaration visited = super.visitClassDeclaration(classDeclaration, executionContext);
                            if ("Child".equals(visited.getSimpleName())) {
                                assertConsistentParent(visited);
                            }
                            return visited;
                        }
                    });
                    return resolved;
                }
            };
        }
    }
}
