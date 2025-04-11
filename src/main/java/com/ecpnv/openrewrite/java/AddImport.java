package com.ecpnv.openrewrite.java;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static com.ecpnv.openrewrite.util.RewriteUtils.createImport;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddImport extends Recipe {

    @Option(displayName = "Full class extended name",
            description = "The fully qualified name of the class.",
            example = "com.ecpnv.openrewrite.java.Test")
    @NonNull
    String fullClassName;

    @Option(displayName = "Full import class name",
            description = "The fully qualified name of the import class.",
            example = "javax.persistence.Entity")
    @NonNull
    String importFullClassName;

    @JsonCreator
    public AddImport(
            @NonNull @JsonProperty("fullClassName") String fullClassName,
            @NonNull @JsonProperty("importFullClassName") String importFullClassName) {
        this.fullClassName = fullClassName;
        this.importFullClassName = importFullClassName;
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Add import forced to class when it is not used";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Forces adding an import to the class when it is not used.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (checkClass(cu, fullClassName) &&
                        checkImports(cu.getImports(), importFullClassName)) {
                    List<JRightPadded<J.Import>> imports = new ArrayList<>();
                    cu.getImports().forEach(i -> imports.add(wrapImport(i)));
                    final J.Import importToAdd = createImport(importFullClassName);
                    imports.add(wrapImport(importToAdd));
                    return cu.getPadding().withImports(imports);
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            private static JRightPadded<J.Import> wrapImport(J.Import anImport) {
                return new JRightPadded<>(anImport, Space.EMPTY, Markers.EMPTY);
            }

            private static boolean checkClass(J.CompilationUnit cu, @NonNull String fullClassName) {
                return cu.getClasses().stream().anyMatch(t -> checkName(t, fullClassName));
            }

            private static boolean checkName(J.ClassDeclaration t, @NonNull String fullClassName) {
                if (t.getType() instanceof JavaType.FullyQualified fullyQualified) {
                    return fullClassName.equals(fullyQualified.getFullyQualifiedName());
                }
                return false;
            }

            private static boolean checkImports(List<J.Import> imports, @NonNull String importFullClassName) {
                if (CollectionUtils.isEmpty(imports)) {
                    return true;
                }
                return imports.stream().noneMatch(t -> checkName(t, importFullClassName));
            }

            private static boolean checkName(J.Import t, @NonNull String importFullClassName) {
                if (t.getQualid().getType() instanceof JavaType.ShallowClass shallowClass) {
                    return importFullClassName.equals(shallowClass.getFullyQualifiedName());
                } else if (t.getQualid().getType() instanceof JavaType.FullyQualified fullyQualified) {
                    return importFullClassName.equals(fullyQualified.getFullyQualifiedName());
                }
                return false;
            }
        };
    }
}
