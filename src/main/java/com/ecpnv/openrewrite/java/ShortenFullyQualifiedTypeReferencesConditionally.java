package com.ecpnv.openrewrite.java;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTypeSignatureBuilder;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.internal.DefaultJavaTypeSignatureBuilder;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import static org.openrewrite.java.tree.TypeUtils.isWellFormedType;

import lombok.EqualsAndHashCode;

/**
 * A recipe that shortens fully qualified type references.
 * Based on {@link org.openrewrite.java.ShortenFullyQualifiedTypeReferences} and
 * added condition for package names to exclude.
 * <p>
 * The parameter 'excludePackages' is a comma-delimited string of the package names
 * that will not be processed when the package name of the class starts with any of the
 * excluded package names.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@EqualsAndHashCode(callSuper = false)
public class ShortenFullyQualifiedTypeReferencesConditionally extends Recipe {

    @Option(displayName = "Exclude packages",
            description = "Packages to be excluded with a comma as delimiter",
            example = "java.lang")
    @NonNull
    String excludePackages;

    @JsonCreator
    public ShortenFullyQualifiedTypeReferencesConditionally(
            @NonNull @JsonProperty("excludePackages") String excludePackages) {
        this.excludePackages = excludePackages;
    }

    @Override
    public String getDisplayName() {
        return "Add imports for fully qualified references to types";
    }

    @Override
    public String getDescription() {
        return "Any fully qualified references to Java types will be replaced with corresponding simple " +
                "names and import statements, provided that it doesn't result in " +
                "any conflicts with other imports or types declared in the local compilation unit.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        // This wrapper is necessary so that the "correct" implementation is used when this recipe is used declaratively
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    return ShortenFullyQualifiedTypeReferencesConditionally.modifyOnly((J) tree, excludePackages).visit(tree, ctx);
                }
                return (J) tree;
            }
        };
    }

    /**
     * Returns a visitor which replaces all fully qualified references in the given subtree with simple names and adds
     * corresponding import statements.
     * <p>
     * For compatibility with other Java-based languages it is recommended to use this as a service via
     * {@link ImportService#shortenFullyQualifiedTypeReferencesIn(J)}, as that will dispatch to the correct
     * implementation for the language.
     *
     * @see ImportService#shortenFullyQualifiedTypeReferencesIn(J)
     * @see JavaVisitor#service(Class)
     */
    public static <J2 extends J> JavaVisitor<ExecutionContext> modifyOnly(J2 subtree, String excludePackages) {
        return getVisitor(subtree, excludePackages);
    }

    @SuppressWarnings({"java:S3776"})
    private static JavaVisitor<ExecutionContext> getVisitor(@Nullable J scope, @NonNull String excludePackages) {
        return new JavaVisitor<>() {
            final Map<String, JavaType> usedTypes = new HashMap<>();
            final JavaTypeSignatureBuilder signatureBuilder = new DefaultJavaTypeSignatureBuilder();

            boolean modify = scope == null;

            private void ensureInitialized() {
                if (!usedTypes.isEmpty()) {
                    return;
                }
                SourceFile sourceFile = getCursor().firstEnclosing(SourceFile.class);
                if (sourceFile instanceof JavaSourceFile) {
                    JavaIsoVisitor<Map<String, JavaType>> typeCollector = new JavaIsoVisitor<Map<String, JavaType>>() {
                        @Override
                        public J.Import visitImport(J.Import anImport, Map<String, JavaType> types) {
                            if (!anImport.isStatic() && isWellFormedType(anImport.getQualid().getType())) {
                                types.put(anImport.getQualid().getSimpleName(), anImport.getQualid().getType());
                            }
                            return anImport;
                        }

                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Map<String, JavaType> types) {
                            if (fieldAccess.getTarget() instanceof J.Identifier identifier) {
                                visitIdentifier(identifier, types);
                            } else if (fieldAccess.getTarget() instanceof J.FieldAccess targetFieldAccess) {
                                visitFieldAccess(targetFieldAccess, types);
                            }
                            return fieldAccess;
                        }

                        @Override
                        public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, Map<String, JavaType> types) {
                            // using `null` since we don't have access to the type here
                            types.put(((J.Identifier) typeParam.getName()).getSimpleName(), null);
                            return typeParam;
                        }

                        @Override
                        public J.Identifier visitIdentifier(J.Identifier identifier, Map<String, JavaType> types) {
                            JavaType type = identifier.getType();
                            if (type instanceof JavaType.FullyQualified && identifier.getFieldType() == null) {
                                types.put(identifier.getSimpleName(), type);
                            }
                            return identifier;
                        }
                    };
                    typeCollector.visit(sourceFile, usedTypes);
                }
            }

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                @SuppressWarnings("DataFlowIssue")
                boolean subtreeRoot = !modify && (scope.equals(tree) || scope.isScope(tree));
                if (subtreeRoot) {
                    modify = true;
                }
                try {
                    return super.visit(tree, ctx);
                } finally {
                    if (subtreeRoot) {
                        modify = false;
                    }
                }
            }

            @Override
            public J visitImport(J.Import anImport, ExecutionContext ctx) {
                // stop recursion
                return anImport;
            }

            @Override
            public Space visitSpace(Space space, Space.Location loc, ExecutionContext ctx) {
                // stop recursion into Javadoc comments
                return space;
            }

            @SuppressWarnings({"java:S3824", "java:S4449"})
            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                if (!modify) {
                    return super.visitFieldAccess(fieldAccess, ctx);
                }

                JavaType type = fieldAccess.getType();
                if (fieldAccess.getName().getFieldType() == null && type instanceof JavaType.Class aClass && aClass.getOwningClass() == null) {
                    ensureInitialized();

                    String simpleName = fieldAccess.getSimpleName();
                    JavaType usedType = usedTypes.get(simpleName);
                    String fullyQualifiedName = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                    if (!isExcluded(fullyQualifiedName)) {
                        if (type == usedType || signatureBuilder.signature(type).equals(signatureBuilder.signature(usedType))) {
                            return !fieldAccess.getPrefix().isEmpty() ? fieldAccess.getName().withPrefix(fieldAccess.getPrefix()) : fieldAccess.getName();
                        } else if (!usedTypes.containsKey(simpleName)) {
                            maybeAddImport(fullyQualifiedName);
                            usedTypes.put(simpleName, type);
                            if (CollectionUtils.isNotEmpty(fieldAccess.getName().getAnnotations())) {
                                return fieldAccess.getName().withAnnotations(ListUtils.map(fieldAccess.getName().getAnnotations(), (i, a) -> {
                                    if (i == 0) {
                                        return a.withPrefix(fieldAccess.getPrefix());
                                    }
                                    return a;
                                }));
                            }
                            return fieldAccess.getName().withPrefix(fieldAccess.getPrefix());
                        }
                    }
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            private boolean isExcluded(final String fullyQualifiedName) {
                final String[] split = excludePackages.split(",");
                final List<String> excludedPackages = Arrays.asList(ArrayUtils.add(split, "java.lang"));
                return excludedPackages.stream().anyMatch(fullyQualifiedName::startsWith);
            }
        };
    }
}
