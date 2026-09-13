package com.ecpnv.openrewrite.util;

import com.ecpnv.openrewrite.java.UnresolvedEntityHierarchyException;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public final class SuperclassInsertion {
    private SuperclassInsertion() {
    }

    public static J.ClassDeclaration insert(J.ClassDeclaration cd, Cursor cursor, String name,
                                            String recipe, EntityTypeResolver resolver, ExecutionContext ctx) {
        J.CompilationUnit cu = cursor.firstEnclosingOrThrow(J.CompilationUnit.class);
        JavaType.FullyQualified parent = resolver.resolve(cu, name, ctx);
        J.ClassDeclaration extended = JavaTemplate.builder(name.substring(name.lastIndexOf('.') + 1))
                .contextSensitive().javaParser(JavaParserFactory.create(ctx)).imports(name).build()
                .apply(cursor, cd.getCoordinates().replaceExtendsClause());
        // Preserve a genuinely attributed template type (e.g. a bundled supporting library), never Unknown.
        if (parent == null && EntityTypeResolver.full(extended.getExtends().getType())) {
            parent = TypeUtils.asFullyQualified(extended.getExtends().getType());
        }
        JavaType.Parameterized parameterized = cd.getType() instanceof JavaType.Parameterized p ? p : null;
        JavaType.Class child = TypeUtils.asClass(parameterized == null ? cd.getType() : parameterized.getType());
        if (parent == null || !name.equals(parent.getFullyQualifiedName()) || child == null) {
            ctx.getOnError().accept(new UnresolvedEntityHierarchyException(
                    cd.getType() == null ? cd.getSimpleName() : cd.getType().getFullyQualifiedName(), name, recipe));
            return cd;
        }
        JavaType.Class updated = child.withSupertype(parent);
        return extended.withExtends(extended.getExtends().withType(parent))
                .withType(parameterized == null ? updated : parameterized.withType(updated));
    }
}
