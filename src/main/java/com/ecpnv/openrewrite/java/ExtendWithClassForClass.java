package com.ecpnv.openrewrite.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that extends class with an annotation pattern with optional conditions.
 * <p>
 * The 'fullClassName' is the name of the class it will be extended.
 * The 'extendsFullClassName' is the full class name of the extension class of the to be extended class.
 * <p>
 * The class only will be extended when it doesn't have any extension.
 * Already extended classes should be handled on a case by case basis.
 * Most likely already extended classes will have a parent class that will be extended by this recipe.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ExtendWithClassForClass extends Recipe {

    @Option(displayName = "Full class extended name",
            description = "The fully qualified name of the to be extending class.",
            example = "org.estatio.base.prod.dom.User")
    @NonNull
    String fullClassName;

    @Option(displayName = "Full extending class name",
            description = "The fully qualified name of the extends class.",
            example = "org.estatio.base.prod.dom.EntityAbstract")
    @NonNull
    String extendsFullClassName;

    @Override
    public String getDisplayName() {
        return "Extend class with @PersistenceCapable annotation with Abstract Entity class conditionally";
    }

    @Override
    public String getDescription() {
        return "Extend class with @PersistenceCapable annotation with an indentity type of datastore with Abstract Entity class.";
    }

    @JsonCreator
    public ExtendWithClassForClass(
            @NonNull @JsonProperty("fullClassName") String fullClassName,
            @NonNull @JsonProperty("extendsFullClassName") String extendsFullClassName) {
        this.fullClassName = fullClassName;
        this.extendsFullClassName = extendsFullClassName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl,
                    ExecutionContext ctx) {
                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() == null) {
                    if (fullClassName.equals(cd.getType().getFullyQualifiedName())) {
                        final JavaType.ShallowClass aClass = JavaType.ShallowClass.build(extendsFullClassName);

                        maybeAddImport(extendsFullClassName, null, false);
                        J.ClassDeclaration newCd = JavaTemplate.builder(aClass.getClassName())
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
                                .imports(extendsFullClassName)
                                .build()
                                .apply(getCursor(), cd.getCoordinates().replaceExtendsClause());

                        return newCd;
                    }
                }

                return cd;
            }
        };
    }
}
