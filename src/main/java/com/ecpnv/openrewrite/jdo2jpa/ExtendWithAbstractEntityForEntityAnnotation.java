package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExtendWithAbstractEntityForEntityAnnotation extends Recipe {

    private static final String JAVA_LANG_OBJECT = "java.lang.Object";
    private static final String JAVAX_PERSISTENCE_ENTITY_ANNOTATION = "@javax.persistence.Entity";
    private static final String JAKARTA_PERSISTENCE_API = "jakarta.persistence-api";
    private static final String JDO_2_JPA_ABSTRACT = "jdo2jpa-abstract";

    @Option(displayName = "Full extends class name",
            description = "The fully qualified name of the extends class.",
            example = "org.estatio.base.prod.dom.EntityAbstract")
    @NonNull
    String extendsFullClassName;

    @Option(displayName = "Library name of class name",
            description = "The library name containing the extends class.",
            example = "jdo2jpa-abstract")
    @NonNull
    String libraryOfAbstractClassName;

    @Override
    public String getDisplayName() {
        return "Extend class with @Entity annotation With Abstract Entity class when it has not extensions";
    }

    @Override
    public String getDescription() {
        return "Extend class with @Entity annotation With Abstract Entity class.";
    }

    @JsonCreator
    public ExtendWithAbstractEntityForEntityAnnotation(
            @NonNull @JsonProperty("extendsFullClassName") String extendsFullClassName,
            @NonNull @JsonProperty("libraryOfAbstractClassName") String libraryOfAbstractClassName) {
        this.extendsFullClassName = extendsFullClassName;
        this.libraryOfAbstractClassName = libraryOfAbstractClassName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl,
                    ExecutionContext ctx) {

                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final String superType = Optional.ofNullable(cd.getType())
                        .map(JavaType.FullyQualified::getSupertype)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .orElse(null);

                if (JAVA_LANG_OBJECT.equals(superType) &&
                        !FindAnnotations.find(classDecl, JAVAX_PERSISTENCE_ENTITY_ANNOTATION).isEmpty()) {

                    final JavaType.ShallowClass aClass = JavaType.ShallowClass.build(extendsFullClassName);
                    final String template = """
                            public class %s extends %s {}
                            """.formatted(cd.getType().getFullyQualifiedName(), aClass.getClassName());
                    final JavaParser.Builder javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(new InMemoryExecutionContext(), JAKARTA_PERSISTENCE_API, JDO_2_JPA_ABSTRACT)
                            .logCompilationWarningsAndErrors(true);

                    J.ClassDeclaration newCd = JavaTemplate.builder(template)
                            .contextSensitive()
                            .javaParser(javaParser)
                            .imports(extendsFullClassName)
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
                    doAfterVisit(new AddImport<>(extendsFullClassName, null, false));

                    return newCd;
                }

                return cd;
            }
        };
    }
}
