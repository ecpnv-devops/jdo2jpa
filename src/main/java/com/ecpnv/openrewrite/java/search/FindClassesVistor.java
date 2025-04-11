package com.ecpnv.openrewrite.java.search;

import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

public class FindClassesVistor extends JavaIsoVisitor<ExecutionContext> {
    final String fullyQualifiedTypeName;
    final Pattern fullyQualifiedType;

    public FindClassesVistor(@NonNull String fullyQualifiedTypeName) {
        this.fullyQualifiedTypeName = fullyQualifiedTypeName;
        fullyQualifiedType = Pattern.compile(StringUtils.aspectjNameToPattern(fullyQualifiedTypeName));
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
        if (cd.getType().isAssignableFrom(fullyQualifiedType)) {
            return SearchResult.found(cd);
        }
        return super.visitClassDeclaration(cd, ctx);
    }
}
