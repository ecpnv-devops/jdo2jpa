package com.ecpnv.openrewrite.jdo2jpa;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ecpnv.openrewrite.java.UnresolvedEntityHierarchyException;
import com.ecpnv.openrewrite.util.EntityTypeResolver;
import com.ecpnv.openrewrite.util.JavaParserFactory;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Plans effective listener inheritance from source ASTs and resolved dependency bytecode. */
public final class AddCausewayEntityListener extends ScanningRecipe<EntityTypeResolver> {
    private static final String LISTENERS = "javax.persistence.EntityListeners";
    private static final String EXCLUDE = "javax.persistence.ExcludeSuperclassListeners";
    private static final String ENTITY = "javax.persistence.Entity";

    @Option(displayName = "Listener class", description = "Fully qualified framework entity listener class.",
            example = "org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener")
    private final String listenerClass;

    @JsonCreator
    public AddCausewayEntityListener(@JsonProperty("listenerClass") String listenerClass) {
        this.listenerClass = listenerClass;
    }

    public String getListenerClass() {
        return listenerClass;
    }

    @Override
    public String getDisplayName() {
        return "Preserve Causeway entity listener integration";
    }

    @Override
    public String getDescription() {
        return "Add a missing listener without duplicating effective superclass listeners or overriding explicit declarations.";
    }

    @Override
    public EntityTypeResolver getInitialValue(ExecutionContext ctx) {
        return new EntityTypeResolver();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(EntityTypeResolver resolver) {
        return resolver.scanner();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(EntityTypeResolver resolver) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                if (!eligible(cd) || annotation(cd, LISTENERS) != null) {
                    return super.visitClassDeclaration(cd, ctx);
                }
                J.CompilationUnit cu = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
                String entity = cd.getType() == null ? cd.getSimpleName() : cd.getType().getFullyQualifiedName();
                try {
                    if (inherited(cd, cu, resolver, ctx, new HashSet<>(), entity)) {
                        return super.visitClassDeclaration(cd, ctx);
                    }
                } catch (UnresolvedEntityHierarchyException error) {
                    ctx.getOnError().accept(error);
                    return cd;
                }
                J.ClassDeclaration changed = JavaTemplate.builder("@EntityListeners(" + listenerClass + ".class)")
                        .javaParser(JavaParserFactory.create(ctx)).imports(LISTENERS).build()
                        .apply(getCursor(), cd.getCoordinates().addAnnotation(java.util.Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport(LISTENERS);
                return super.visitClassDeclaration(changed, ctx);
            }
        };
    }

    private boolean eligible(J.ClassDeclaration cd) {
        return cd.getKind() == J.ClassDeclaration.Kind.Type.Class && !cd.hasModifier(J.Modifier.Type.Abstract)
                && annotation(cd, ENTITY) != null;
    }

    private static J.Annotation annotation(J.ClassDeclaration cd, String name) {
        return cd.getLeadingAnnotations().stream().filter(a -> TypeUtils.isOfClassType(a.getType(), name))
                .findFirst().orElse(null);
    }

    private boolean inherited(J.ClassDeclaration cd, J.CompilationUnit cu, EntityTypeResolver resolver,
                              ExecutionContext ctx, Set<String> visiting, String entity) {
        if (annotation(cd, EXCLUDE) != null || cd.getExtends() == null) {
            return false;
        }
        JavaType.FullyQualified parent = TypeUtils.asFullyQualified(cd.getExtends().getType());
        String name = parent == null ? cd.getExtends().toString().trim() : parent.getFullyQualifiedName();
        if (parent == null) {
            // Resolve source spelling via explicit imports and the compilation unit's package.
            for (J.Import imprt : cu.getImports()) {
                if (!imprt.isStatic() && imprt.getTypeName().endsWith("." + name)) {
                    name = imprt.getTypeName();
                    break;
                }
            }
            if (!name.contains(".") && cu.getPackageDeclaration() != null) {
                name = cu.getPackageDeclaration().getExpression().toString().trim() + "." + name;
            }
        }
        return effective(name, parent, cu, resolver, ctx, visiting, entity);
    }

    private boolean effective(String name, JavaType.FullyQualified known, J.CompilationUnit cu,
                              EntityTypeResolver resolver, ExecutionContext ctx, Set<String> visiting, String entity) {
        if ("java.lang.Object".equals(name)) {
            return false;
        }
        if (!visiting.add(name)) {
            throw unresolved(entity, name);
        }
        try {
            J.ClassDeclaration source = resolver.declaration(cu, name);
            if (source != null) {
                J.Annotation own = annotation(source, LISTENERS);
                if (own != null && sourceListener(own, resolver.owner(cu, name), entity)) {
                    return true;
                }
                boolean inherited = inherited(source, resolver.owner(cu, name), resolver, ctx, visiting, entity);
                // A concrete eligible ancestor will gain the listener in this same scan, independent of visit order.
                return inherited || (own == null && eligible(source));
            }
            JavaType.FullyQualified type = EntityTypeResolver.full(known) ? known : resolver.resolve(cu, name, ctx);
            if (type == null) {
                throw unresolved(entity, name);
            }
            for (JavaType.FullyQualified a : type.getAnnotations()) {
                if (LISTENERS.equals(a.getFullyQualifiedName())) {
                    if (!(a instanceof JavaType.Annotation values)) {
                        throw unresolved(entity, name);
                    }
                    // EntityListeners.value is required; even an explicit empty array has an element value.
                    if (values.getValues().isEmpty()) {
                        throw unresolved(entity, name);
                    }
                    for (JavaType.Annotation.ElementValue value : values.getValues()) {
                        if (value instanceof JavaType.Annotation.ArrayElementValue array) {
                            JavaType[] references = array.getReferenceValues();
                            if (references != null) {
                                for (JavaType reference : references) {
                                    if (reference == null || reference instanceof JavaType.Unknown) {
                                        throw unresolved(entity, name);
                                    }
                                    if (TypeUtils.isOfClassType(reference, listenerClass)) {
                                        return true;
                                    }
                                }
                            }
                        } else if (value.getValue() instanceof JavaType reference
                                && TypeUtils.isOfClassType(reference, listenerClass)) {
                            return true;
                        }
                    }
                }
            }
            if (type.getAnnotations().stream().anyMatch(a -> EXCLUDE.equals(a.getFullyQualifiedName()))) {
                return false;
            }
            JavaType.FullyQualified parent = type.getSupertype();
            if (parent == null) {
                throw unresolved(entity, name);
            }
            return effective(parent.getFullyQualifiedName(), parent, cu, resolver, ctx, visiting, entity);
        } finally {
            visiting.remove(name);
        }
    }

    private boolean sourceListener(J.Annotation annotation, J.CompilationUnit cu, String entity) {
        if (annotation.getArguments() == null) {
            return false;
        }
        for (Expression value : annotation.getArguments()) {
            if (containsListener(value, cu, entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsListener(Expression value, J.CompilationUnit cu, String entity) {
        if (value instanceof J.Assignment assignment) {
            return containsListener(assignment.getAssignment(), cu, entity);
        }
        if (value instanceof J.NewArray array) {
            return array.getInitializer() != null && array.getInitializer().stream().anyMatch(v -> containsListener(v, cu, entity));
        }
        if (value instanceof J.FieldAccess field && "class".equals(field.getSimpleName())) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(field.getTarget().getType());
            if (type != null) {
                return listenerClass.equals(type.getFullyQualifiedName());
            }
            // A class literal emitted by an earlier template may lack attribution; explicit FQNs/imports
            // still identify its listener without pretending to know that listener's hierarchy.
            String name = field.getTarget().toString().trim();
            if (listenerClass.equals(name)) {
                return true;
            }
            for (J.Import imprt : cu.getImports()) {
                if (!imprt.isStatic() && imprt.getTypeName().endsWith("." + name)) {
                    return listenerClass.equals(imprt.getTypeName());
                }
            }
            if (name.contains(".") && Character.isLowerCase(name.charAt(0))) {
                return false;
            }
            throw unresolved(entity, name);
        }
        if (value instanceof J.Empty) {
            return false;
        }
        throw unresolved(entity, value.toString());
    }

    private UnresolvedEntityHierarchyException unresolved(String entity, String parent) {
        return new UnresolvedEntityHierarchyException(entity, parent, getClass().getName());
    }
}
