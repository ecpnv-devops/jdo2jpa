package com.ecpnv.openrewrite.jdo2jpa;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Optional;

@EqualsAndHashCode(callSuper = false)
@Value
public class ExtendWithAbstractEntityForEntityAnnotation extends Recipe {

    private static final String JAVA_LANG_OBJECT = "java.lang.Object";
    private static final String EXTENDS_FULL_CLASSNAME = "org.estatio.base.prod.dom.EntityAbstract";
    private static final String JAVAX_PERSISTENCE_ENTITY_ANNOTATION = "@javax.persistence.Entity";
    private static final String JAKARTA_PERSISTENCE_API = "jakarta.persistence-api";
    private static final String JDO_2_JPA_ABSTRACT = "jdo2jpa-abstract";

    @Override
    public @NotNull String getDisplayName() {
        return "Extend class with @Entity annotation With Abstract Entity class when it has not extensions";
    }

    @Override
    public @NotNull String getDescription() {
        return "Extend class with @Entity annotation With Abstract Entity class.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {
            @Override
            public @NotNull J.ClassDeclaration visitClassDeclaration(
                    @NotNull J.ClassDeclaration classDecl,
                    @NotNull ExecutionContext ctx) {

                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final String superType = Optional.ofNullable(cd.getType())
                        .map(JavaType.FullyQualified::getSupertype)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .orElse(null);

                if (JAVA_LANG_OBJECT.equals(superType) &&
                        !FindAnnotations.find(classDecl, JAVAX_PERSISTENCE_ENTITY_ANNOTATION).isEmpty()) {

                    final JavaType.ShallowClass aClass = JavaType.ShallowClass.build(EXTENDS_FULL_CLASSNAME);
                    final String template = """
                            public class %s extends %s {}
                            """.formatted(cd.getType().getFullyQualifiedName(), aClass.getClassName());
                    final JavaParser.Builder javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(new InMemoryExecutionContext(), JAKARTA_PERSISTENCE_API, JDO_2_JPA_ABSTRACT)
                            .logCompilationWarningsAndErrors(true);

                    J.ClassDeclaration newCd = JavaTemplate.builder(template)
                            .contextSensitive()
                            .javaParser(javaParser)
                            .imports(EXTENDS_FULL_CLASSNAME)
                            .build()
                            .apply(getCursor(), cd.getCoordinates().replace());

                    newCd = newCd.withBody(cd.getBody());
                    newCd = newCd.withImplements(cd.getPermits());
                    newCd = newCd.withPermits(cd.getPermits());
                    newCd = newCd.withPrefix(cd.getPrefix());
                    newCd = newCd.withPrimaryConstructor(cd.getPrimaryConstructor());
                    newCd = newCd.withComments(cd.getComments());
                    newCd = newCd.withKind(cd.getKind());
                    newCd = newCd.withModifiers(cd.getModifiers());
                    newCd = newCd.withLeadingAnnotations(cd.getLeadingAnnotations());
                    newCd = newCd.withId(cd.getId());
                    newCd = newCd.withMarkers(cd.getMarkers());
                    newCd = newCd.withTypeParameters(cd.getTypeParameters());
                    newCd = newCd.withName(cd.getName());

                    //This line causes the imports to have white lines between them
                    doAfterVisit(new AddImport<>(EXTENDS_FULL_CLASSNAME, null, false));

                    return newCd;
                }

                return cd;
            }
        };
    }
}
