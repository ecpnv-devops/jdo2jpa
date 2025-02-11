package com.ecpnv.openrewrite.jdo2jpa;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

import com.ecpnv.openrewrite.util.RewriteUtils;

import static com.ecpnv.openrewrite.jdo2jpa.Constants.Jdo;
import static com.ecpnv.openrewrite.jdo2jpa.Constants.Jpa;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that extends class with an {@link javax.jdo.annotations.PersistenceCapable} annotation with an empty
 * identityType or {@link javax.jdo.annotations.IdentityType#DATASTORE} with a given class name.
 * <p>
 * The 'extendsFullClassName' is the full class name of the extension class of the to be extended class.
 * The 'libraryOfAbstractClassName' can be used to host the (abstract) extending class when that class cannot be
 * found on the regular or resource classpath. The library file containing the (abstract) extending class should
 * reside in the resource classpath which by default is 'src/main/resources/META-INF/rewrite/classpath'.
 * <p>
 * The class only will be extended when it doesn't have any extension.
 * Already extended classes should be handled on a case by case basis.
 * Most likely already extended classes will have a parent class that will be extended by this recipe.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ExtendWithAbstractEntityForPersistenceCapableAnnotation extends Recipe {

    private static final String SOURCE_ANNOTATION_TYPE = "@" + Jdo.PERSISTENCE_CAPABLE_ANNOTATION_FULL;

    @Option(displayName = "Full extends class name",
            description = "The fully qualified name of the extends class.",
            example = "org.estatio.base.prod.dom.EntityAbstract")
    @NonNull
    String extendsFullClassName;

    @Option(displayName = "Library name of class name",
            description = "The library name containing the extends class.",
            example = "jdo2jpa-abstract")
    @Nullable
    String libraryOfAbstractClassName;

    @Override
    public String getDisplayName() {
        return "Extend class with @PersistenceCapable annotation with Abstract Entity class conditionally";
    }

    @Override
    public String getDescription() {
        return "Extend class with @PersistenceCapable annotation with an indentity type of datastore with Abstract Entity class.";
    }

    @JsonCreator
    public ExtendWithAbstractEntityForPersistenceCapableAnnotation(
            @NonNull @JsonProperty("extendsFullClassName") String extendsFullClassName,
            @Nullable @JsonProperty("libraryOfAbstractClassName") String libraryOfAbstractClassName) {
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

                final Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, SOURCE_ANNOTATION_TYPE);
                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() == null && !sourceAnnotations.isEmpty()) {
                    final J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();
                    final String simpleName = RewriteUtils.findArgument(sourceAnnotation, Constants.Jdo.IDENTITY_TYPE_ANNOTATION_NAME)
                            .map(J.Assignment::getAssignment)
                            .filter(J.FieldAccess.class::isInstance)
                            .map(J.FieldAccess.class::cast)
                            .map(J.FieldAccess::getSimpleName)
                            .orElse(null);

                    if (StringUtils.isBlank(simpleName) || Constants.Jdo.IDENTITY_TYPE_DATASTORE.equals(simpleName)) {
                        final JavaType.ShallowClass aClass = JavaType.ShallowClass.build(extendsFullClassName);
                        final String[] classPath = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
                        String[] resourceClasspath = new String[]{Jpa.CLASS_PATH, Jdo.CLASS_PATH};
                        if (StringUtils.isNoneBlank(libraryOfAbstractClassName)) {
                            resourceClasspath = ArrayUtils.add(resourceClasspath, libraryOfAbstractClassName);
                        }

                        final JavaParser.Builder<?, ?> javaParser = JavaParser.fromJavaVersion()
                                .classpath(classPath)
                                .classpathFromResources(new InMemoryExecutionContext(), resourceClasspath)
                                .logCompilationWarningsAndErrors(true);

                        J.ClassDeclaration newCd = JavaTemplate.builder(aClass.getClassName())
                                .contextSensitive()
                                .javaParser(javaParser)
                                .imports(extendsFullClassName)
                                .build()
                                .apply(getCursor(), cd.getCoordinates().replaceExtendsClause());

                        //This line causes the imports to have white lines between them
                        doAfterVisit(new AddImport<>(extendsFullClassName, null, false));

                        return newCd;
                    }
                }

                return cd;
            }
        };
    }
}
