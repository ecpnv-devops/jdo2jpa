package com.ecpnv.openrewrite.util;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;

/** Per-scan, per-module metadata. Never treats JavaSourceSet's shallow index as a resolved type. */
public final class EntityTypeResolver {
    private final List<J.CompilationUnit> sources = new ArrayList<>();
    private final Map<Path, MavenResolutionResult> poms = new LinkedHashMap<>();
    private final Map<String, Module> modules = new HashMap<>();

    public TreeVisitor<?, ExecutionContext> scanner() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source) {
                    source.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(m ->
                            poms.put(directory(source.getSourcePath()), m));
                    if (source instanceof J.CompilationUnit cu) {
                        sources.add(cu);
                    }
                }
                return tree;
            }
        };
    }

    private static Path directory(Path path) {
        return path.getParent() == null ? Path.of("") : path.getParent().normalize();
    }

    private @Nullable Path pomRoot(J.CompilationUnit cu) {
        return poms.keySet().stream()
                .filter(p -> p.toString().isEmpty() || cu.getSourcePath().normalize().startsWith(p))
                .max(java.util.Comparator.comparingInt(p -> p.toString().length())).orElse(null);
    }

    private String key(J.CompilationUnit cu) {
        Path root = pomRoot(cu);
        String sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).map(JavaSourceSet::getName).orElse("main");
        return (root == null ? "<sources>" : root.toString()) + ":" + sourceSet;
    }

    private Module module(J.CompilationUnit cu) {
        return modules.computeIfAbsent(key(cu), k -> {
            Module module = new Module();
            for (J.CompilationUnit source : sources) {
                if (!k.equals(key(source))) { continue; }
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, Integer unused) {
                        if (cd.getType() != null) {
                            module.declarations.put(cd.getType().getFullyQualifiedName(), cd);
                            module.owners.put(cd.getType().getFullyQualifiedName(), source);
                            module.add(cd.getType());
                        }
                        return super.visitClassDeclaration(cd, unused);
                    }
                }.visit(source, 0);
                source.getTypesInUse().getTypesInUse().forEach(module::add);
            }
            return module;
        });
    }

    public J.@Nullable ClassDeclaration declaration(J.CompilationUnit cu, String name) {
        return module(cu).declarations.get(name);
    }

    public J.CompilationUnit owner(J.CompilationUnit cu, String name) {
        return module(cu).owners.getOrDefault(name, cu);
    }

    public JavaType.@Nullable FullyQualified resolve(J.CompilationUnit cu, String name, ExecutionContext ctx) {
        Module module = module(cu);
        JavaType.FullyQualified existing = module.types.get(name);
        if (existing != null) { return existing; }
        if (!module.attempted.add(name)) { return null; }
        Path root = pomRoot(cu);
        if (root == null) { return null; }
        List<Path> classpath = classpath(poms.get(root), cu, ctx);
        if (classpath.isEmpty()) { return null; }
        // A fresh parser/type cache prevents different modules or artifact versions sharing metadata.
        List<Throwable> errors = new ArrayList<>();
        ExecutionContext parsing = new InMemoryExecutionContext(errors::add);
        try {
            var parsed = JavaParser.fromJavaVersion().classpath(classpath).build()
                    .parse(parsing, "class __EntityMetadataProbe { " + name.replace('$', '.') + " parent; }")
                    .findFirst().orElse(null);
            if (parsed instanceof J.CompilationUnit probe && errors.isEmpty()) {
                J.VariableDeclarations field = (J.VariableDeclarations) probe.getClasses().get(0)
                        .getBody().getStatements().get(0);
                module.add(field.getTypeExpression().getType());
            }
        } catch (RuntimeException ignored) {
            // The caller reports a typed, entity-specific error; parser recovery is not success.
        }
        return module.types.get(name);
    }

    public static boolean full(@Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && !(fq instanceof JavaType.ShallowClass);
    }

    /** Uses resolved Maven coordinates, not FQN-to-path guesses or the plugin JVM classpath. */
    private static List<Path> classpath(MavenResolutionResult maven, J.CompilationUnit cu, ExecutionContext ctx) {
        String override = System.getProperty("maven.repo.local");
        String uri = maven.getMavenSettings() == null
                ? MavenExecutionContextView.view(ctx).getLocalRepository().getUri()
                : maven.getMavenSettings().getMavenLocal().getUri();
        Path repository = override != null ? Path.of(override)
                : uri.startsWith("file:") ? Path.of(URI.create(uri)) : Path.of(uri);
        Set<Path> result = new LinkedHashSet<>();
        boolean test = cu.getMarkers().findFirst(JavaSourceSet.class)
                .map(s -> "test".equals(s.getName())).orElse(false);
        List<Scope> scopes = test ? List.of(Scope.Test) : List.of(Scope.Compile, Scope.Provided);
        for (Scope scope : scopes) {
            for (ResolvedDependency dependency : maven.getDependencies().getOrDefault(scope, List.of())) {
                String type = dependency.getType();
                if (type != null && !"jar".equals(type) && !"test-jar".equals(type)) { continue; }
                String classifier = dependency.getClassifier();
                if (classifier == null && "test-jar".equals(type)) { classifier = "tests"; }
                String suffix = classifier == null ? "" : "-" + classifier;
                Path directory = repository.resolve(dependency.getGroupId().replace('.', '/'))
                        .resolve(dependency.getArtifactId()).resolve(dependency.getVersion());
                String version = dependency.getDatedSnapshotVersion() == null ? dependency.getVersion()
                        : dependency.getDatedSnapshotVersion();
                Path artifact = directory.resolve(dependency.getArtifactId() + "-" + version + suffix + ".jar");
                if (!Files.isRegularFile(artifact)) {
                    artifact = directory.resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + suffix + ".jar");
                }
                // Missing transitive annotation bytecode must not masquerade as an unannotated parent.
                if (!Files.isRegularFile(artifact)) { return List.of(); }
                result.add(artifact);
            }
        }
        return List.copyOf(result);
    }

    private static final class Module {
        private final Map<String, J.ClassDeclaration> declarations = new HashMap<>();
        private final Map<String, J.CompilationUnit> owners = new HashMap<>();
        private final Map<String, JavaType.FullyQualified> types = new HashMap<>();
        private final Set<String> attempted = new LinkedHashSet<>();

        private void add(@Nullable JavaType type) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (!full(fq) || types.putIfAbsent(fq.getFullyQualifiedName(), fq) != null) { return; }
            add(fq.getSupertype());
            fq.getInterfaces().forEach(this::add);
        }
    }
}
