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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import com.ecpnv.openrewrite.util.EntityTypeResolver;
import com.ecpnv.openrewrite.util.SuperclassInsertion;

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
public class ExtendWithClassForAnnotationConditionally extends ScanningRecipe<EntityTypeResolver> {

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

    @Option(displayName = "Annotation attribute name",
            description = "Optional annotation attribute to match structurally.",
            required = false,
            example = "identityType")
    @Nullable
    String annotationAttributeName;

    @Option(displayName = "Annotation attribute value",
            description = "Optional fully qualified enum constant required for the structural attribute match.",
            required = false,
            example = "javax.jdo.annotations.IdentityType.DATASTORE")
    @Nullable
    String annotationAttributeValue;

    @Override
    public String getDisplayName() {
        return "Extend class with @PersistenceCapable annotation with Abstract Entity class conditionally";
    }

    @Override
    public String getDescription() {
        return "Extend class with @PersistenceCapable annotation with an indentity type of datastore with Abstract Entity class.";
    }

    public ExtendWithClassForAnnotationConditionally(
            @NonNull String annotationPattern,
            @Nullable String annotationCondition,
            @NonNull String extendsFullClassName) {
        this(annotationPattern, annotationCondition, extendsFullClassName, null, null);
    }

    @JsonCreator
    public ExtendWithClassForAnnotationConditionally(
            @NonNull @JsonProperty("annotationPattern") String annotationPattern,
            @Nullable @JsonProperty("annotationCondition") String annotationCondition,
            @NonNull @JsonProperty("extendsFullClassName") String extendsFullClassName,
            @Nullable @JsonProperty("annotationAttributeName") String annotationAttributeName,
            @Nullable @JsonProperty("annotationAttributeValue") String annotationAttributeValue) {
        this.annotationPattern = annotationPattern;
        this.annotationCondition = annotationCondition;
        this.extendsFullClassName = extendsFullClassName;
        this.annotationAttributeName = annotationAttributeName;
        this.annotationAttributeValue = annotationAttributeValue;
    }

    @Override
    public Validated<Object> validate() {
        return validateCondition(super.validate());
    }

    @Override
    public Validated<Object> validate(ExecutionContext ctx) {
        return validateCondition(super.validate(ctx));
    }

    private Validated<Object> validateCondition(Validated<Object> validated) {
        boolean regex = StringUtils.isNotBlank(annotationCondition);
        boolean attribute = StringUtils.isNotBlank(annotationAttributeName);
        boolean value = StringUtils.isNotBlank(annotationAttributeValue);
        if (regex && (attribute || value)) {
            return validated.and(Validated.invalid("condition", annotationCondition,
                    "annotationCondition cannot be combined with structural condition options"));
        }
        if (attribute != value) {
            return validated.and(Validated.invalid("structuralCondition", annotationAttributeName,
                    "annotationAttributeName and annotationAttributeValue must be supplied together"));
        }
        if (value && annotationAttributeValue.lastIndexOf('.') <= 0) {
            return validated.and(Validated.invalid("annotationAttributeValue", annotationAttributeValue,
                    "annotationAttributeValue must be a fully qualified enum constant"));
        }
        return validated;
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

        return new JavaIsoVisitor<>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl,
                    ExecutionContext ctx) {
                final Set<J.Annotation> sourceAnnotations = FindAnnotations.find(classDecl, annotationPattern);
                final J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() == null && !sourceAnnotations.isEmpty()) {
                    final J.Annotation sourceAnnotation = sourceAnnotations.iterator().next();
                    if (checkAnnotationForCondition(sourceAnnotation)) {
                        J.ClassDeclaration extended = SuperclassInsertion.insert(cd, getCursor(), extendsFullClassName,
                                ExtendWithClassForAnnotationConditionally.class.getName(), resolver, ctx);
                        if (extended != cd) {
                            maybeAddImport(extendsFullClassName, null, false);
                        }
                        return extended;
                    }
                }

                return cd;
            }

            private boolean checkAnnotationForCondition(J.Annotation annotation) {
                if (StringUtils.isNotBlank(annotationAttributeName)) {
                    return structurallyMatches(annotation);
                }
                if (StringUtils.isBlank(annotationCondition) || CollectionUtils.isEmpty(annotation.getArguments())) {
                    return true;
                }

                Pattern pattern = Pattern.compile(annotationCondition);
                return annotation.getArguments().stream()
                        .anyMatch(argument -> pattern.matcher(argument.toString()).matches());
            }

            private boolean structurallyMatches(J.Annotation annotation) {
                if (CollectionUtils.isEmpty(annotation.getArguments())) {
                    return false;
                }
                int separator = annotationAttributeValue.lastIndexOf('.');
                String enumType = annotationAttributeValue.substring(0, separator);
                String enumConstant = annotationAttributeValue.substring(separator + 1);
                return annotation.getArguments().stream()
                        .filter(J.Assignment.class::isInstance)
                        .map(J.Assignment.class::cast)
                        .filter(assignment -> assignment.getVariable() instanceof J.Identifier identifier
                                && annotationAttributeName.equals(identifier.getSimpleName()))
                        .map(J.Assignment::getAssignment)
                        .anyMatch(value -> enumConstant.equals(enumConstantName(value))
                                && enumType.equals(enumOwnerName(value)));
            }

            private String enumConstantName(Expression expression) {
                if (expression instanceof J.FieldAccess fieldAccess) {
                    return fieldAccess.getSimpleName();
                }
                if (expression instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                return null;
            }

            private String enumOwnerName(Expression expression) {
                if (expression instanceof J.FieldAccess fieldAccess) {
                    JavaType.FullyQualified owner = TypeUtils.asFullyQualified(fieldAccess.getTarget().getType());
                    return owner == null ? null : owner.getFullyQualifiedName();
                }
                if (expression instanceof J.Identifier identifier && identifier.getFieldType() != null) {
                    JavaType.FullyQualified owner = TypeUtils.asFullyQualified(identifier.getFieldType().getOwner());
                    return owner == null ? null : owner.getFullyQualifiedName();
                }
                return null;
            }
        };


    }
}
