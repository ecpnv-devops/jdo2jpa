package com.ecpnv.openrewrite.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.ToolProvider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
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

    @Disabled("OpenRewrite 8.47.3 JavaSourceSet dependency types omit modifier and annotation metadata")
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
                        .recipe(new InsertResolvedSuperclass("example.DependencyParent")),
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

    private static void assertResolvedParent(J.CompilationUnit compilationUnit) {
        assertResolvedParent(compilationUnit.getClasses().get(0));
    }

    private static void assertResolvedParent(J.ClassDeclaration child) {
        JavaType.Class childType = TypeUtils.asClass(child.getType());
        JavaType.Class parentType = TypeUtils.asClass(child.getExtends().getType());

        assertThat(childType).isNotNull();
        assertThat(parentType).isNotNull();
        assertThat(childType.getSupertype()).isEqualTo(parentType);
        assertThat(parentType.getFullyQualifiedName()).isIn(
                "example.ApplicationParent", "example.DependencyParent");
        assertThat(parentType.getFlags())
                .as("flags and annotations for %s: %s", parentType, parentType.getAnnotations())
                .contains(org.openrewrite.java.tree.Flag.Abstract);
        assertThat(parentType.getAnnotations())
                .extracting(JavaType.FullyQualified::getFullyQualifiedName)
                .contains("java.lang.Deprecated");
    }

    private static Path compileDependency(String fullyQualifiedName, String source) throws IOException {
        Path root = Files.createTempDirectory("superclass-attribution-spike");
        Path sourceFile = root.resolve("src/" + fullyQualifiedName.replace('.', '/') + ".java");
        Path classes = root.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source);

        int result = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "-d", classes.toString(), sourceFile.toString());
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

        @JsonCreator
        public InsertResolvedSuperclass(@JsonProperty("superclassName") String superclassName) {
            this.superclassName = superclassName;
        }

        public String getSuperclassName() {
            return superclassName;
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
            return new HashMap<>();
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getScanner(Map<String, JavaType.FullyQualified> types) {
            return new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                    cu.getMarkers().findFirst(JavaSourceSet.class).ifPresent(sourceSet ->
                            sourceSet.getClasspath().forEach(type -> types.put(type.getFullyQualifiedName(), type)));
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
                                assertResolvedParent(visited);
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
