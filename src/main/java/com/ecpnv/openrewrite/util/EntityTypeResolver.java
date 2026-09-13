package com.ecpnv.openrewrite.util;

import java.io.IOException;
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
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

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
        String scope = root == null ? cu.getMarkers().findFirst(JavaSourceSet.class)
                .map(s -> "source-set:" + s.getId()).orElse("<sources>") : root.toString();
        return scope + ":" + sourceSet;
    }

    private Module module(J.CompilationUnit cu) {
        return modules.computeIfAbsent(key(cu), k -> {
            Module module = new Module();
            for (J.CompilationUnit source : sources) {
                if (!k.equals(key(source))) {
                    continue;
                }
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
        if (existing != null) {
            return existing;
        }
        if (!module.attempted.add(name)) {
            return null;
        }
        Path root = pomRoot(cu);
        if (module.classpath == null) {
            module.classpath = root == null ? indexedClasspath(cu, ctx) : classpath(poms.get(root), cu, ctx);
        }
        List<Path> classpath = module.classpath;
        if (classpath.isEmpty()) {
            return null;
        }
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
        // Resolution markers carry the effective module settings (including a runner's local-repository override).
        // Do not replace an explicitly captured repository with the recipe host's unrelated JVM property.
        String override = maven.getMavenSettings() != null && maven.getMavenSettings().getLocalRepository() != null
                ? null : System.getProperty("maven.repo.local");
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
                if (type != null && !"jar".equals(type) && !"test-jar".equals(type)) {
                    continue;
                }
                String classifier = dependency.getClassifier();
                if (classifier == null && "test-jar".equals(type)) {
                    classifier = "tests";
                }
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
                if (!Files.isRegularFile(artifact)) {
                    return List.of();
                }
                result.add(artifact);
            }
        }
        return List.copyOf(result);
    }

    /** The source-set index retains selected artifact coordinates even when Maven POM parsing is disabled. */
    private static List<Path> indexedClasspath(J.CompilationUnit cu, ExecutionContext ctx) {
        JavaSourceSet index = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
        if (index == null || index.getGavToTypes().isEmpty()) {
            return List.of();
        }
        var maven = MavenExecutionContextView.view(ctx);
        String override = maven.getSettings() != null && maven.getSettings().getLocalRepository() != null
                ? null : System.getProperty("maven.repo.local");
        String uri = maven.getLocalRepository().getUri();
        Path repository = override != null ? Path.of(override)
                : uri.startsWith("file:") ? Path.of(URI.create(uri)) : Path.of(uri);
        List<Path> classpath = new ArrayList<>();
        try {
            for (var entry : index.getGavToTypes().entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                String[] gav = entry.getKey().split(":", -1);
                if (gav.length != 3) {
                    return List.of();
                }
                Path directory = repository.resolve(gav[0].replace('.', '/')).resolve(gav[1]).resolve(gav[2]);
                if (!Files.isDirectory(directory)) {
                    return List.of();
                }
                Set<String> indexedNames = entry.getValue().stream()
                        .map(t -> t.getFullyQualifiedName().replace('$', '.')).collect(Collectors.toSet());
                List<Path> matches = new ArrayList<>();
                try (var files = Files.list(directory)) {
                    for (Path jar : files.filter(f -> f.getFileName().toString().endsWith(".jar"))
                            .filter(f -> !f.getFileName().toString().endsWith("-sources.jar"))
                            .filter(f -> !f.getFileName().toString().endsWith("-javadoc.jar")).sorted().toList()) {
                        try (ZipFile zip = new ZipFile(jar.toFile())) {
                            Set<String> names = zip.stream().map(e -> e.getName()).filter(n -> n.endsWith(".class"))
                                    .map(n -> n.substring(0, n.length() - 6).replace('/', '.').replace('$', '.'))
                                    .collect(Collectors.toSet());
                            if (names.containsAll(indexedNames)) {
                                matches.add(jar);
                            }
                        }
                    }
                }
                if (matches.isEmpty()) {
                    return List.of();
                }
                // GAV alone loses classifier/timestamp identity. Never choose between different matching binaries.
                for (int i = 1; i < matches.size(); i++) {
                    if (Files.mismatch(matches.get(0), matches.get(i)) != -1) {
                        return List.of();
                    }
                }
                classpath.add(matches.get(0));
            }
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
        return List.copyOf(classpath);
    }

    private static final class Module {
        private @Nullable List<Path> classpath;
        private final Map<String, J.ClassDeclaration> declarations = new HashMap<>();
        private final Map<String, J.CompilationUnit> owners = new HashMap<>();
        private final Map<String, JavaType.FullyQualified> types = new HashMap<>();
        private final Set<String> attempted = new LinkedHashSet<>();

        private void add(@Nullable JavaType type) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (!full(fq) || types.putIfAbsent(fq.getFullyQualifiedName(), fq) != null) {
                return;
            }
            add(fq.getSupertype());
            fq.getInterfaces().forEach(this::add);
        }
    }
}
