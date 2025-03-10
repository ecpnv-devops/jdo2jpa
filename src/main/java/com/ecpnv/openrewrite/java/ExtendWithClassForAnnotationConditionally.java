package com.ecpnv.openrewrite.java;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that extends class with an annotation pattern with optional conditions.
 * <p>
 * The 'annotationPattern' is the full class name of the annotation class it looks for.
 * The 'annotationCondition' is the regex value that checks if any of the annotation arguments match with. It cannot
 * handle default values of annotation arguments since openrewrite doesn't include these in the LST.
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
public class ExtendWithClassForAnnotationConditionally extends Recipe {

    @Option(displayName = "Annotation pattern",
            description = "An annotation pattern, expressed as a method pattern.",
            example = "@javax.jdo.annotations.PersistenceCapable")
    @NonNull
    String annotationPattern;

    @Option(displayName = "Annotation condition",
            description = "An annotation condition, expressed as regex",
            example = "identityType = IdentityType.DATASTORE")
    @Nullable
    String annotationCondition;

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
    public ExtendWithClassForAnnotationConditionally(
            @NonNull @JsonProperty("annotationPattern") String annotationPattern,
            @Nullable @JsonProperty("annotationCondition") String annotationCondition,
            @NonNull @JsonProperty("extendsFullClassName") String extendsFullClassName,
            @Nullable @JsonProperty("libraryOfAbstractClassName") String libraryOfAbstractClassName) {
        this.annotationPattern = annotationPattern;
        this.annotationCondition = annotationCondition;
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
                final Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, annotationPattern);
                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() == null && !sourceAnnotations.isEmpty()) {
                    final J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();
                    if (checkAnnotationForCondition(sourceAnnotation, annotationCondition)) {
                        final JavaType.ShallowClass aClass = JavaType.ShallowClass.build(extendsFullClassName);

                        J.ClassDeclaration newCd = JavaTemplate.builder(aClass.getClassName())
                                .contextSensitive()
                                .javaParser(JavaParserFactory.create(ctx))
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

            private boolean checkAnnotationForCondition(J.Annotation annotation, String annotationCondition) {
                if (StringUtils.isBlank(annotationCondition) || CollectionUtils.isEmpty(annotation.getArguments())) {
                    return true;
                }

                Pattern pattern = Pattern.compile(annotationCondition);
                return annotation.getArguments().stream().anyMatch(argument -> pattern.matcher(argument.toString()).matches());
            }
        };


    }
}
