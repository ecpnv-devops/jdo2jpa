package com.ecpnv.openrewrite.java;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

/**
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
class ExtendWithClassForAnnotationConditionallyTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithClassForAnnotationConditionally(
                                "@javax.jdo.annotations.PersistenceCapable",
                                "identityType = IdentityType.DATASTORE",
                                "org.estatio.base.prod.dom.EntityAbstract"
                        )),
                // language=java
                java(
                        """
                                import java.util.List;
                                
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.DATASTORE)
                                public class SomeEntity implements Comparable<SomeEntity> {
                                    private List<String> listOfStrings;
                                
                                    @Override
                                    public int compareTo(SomeEntity o) {
                                        return 0;
                                    }
                                }
                                """,
                        """
                                import java.util.List;
                                
                                import javax.jdo.annotations.PersistenceCapable;
                                
                                import javax.jdo.annotations.IdentityType;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.DATASTORE)
                                public class SomeEntity extends EntityAbstract implements Comparable<SomeEntity> {
                                    private List<String> listOfStrings;
                                
                                    @Override
                                    public int compareTo(SomeEntity o) {
                                        return 0;
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationUnhappyPathForApplication() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithClassForAnnotationConditionally(
                                "@javax.jdo.annotations.PersistenceCapable",
                                "identityType = IdentityType.DATASTORE",
                                "org.estatio.base.prod.dom.EntityAbstract"
                        )),
                // language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.APPLICATION)
                                public class AnotherEntity extends String {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationUnhappyPathForApplication1() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithClassForAnnotationConditionally(
                                "@javax.jdo.annotations.PersistenceCapable",
                                "identityType = IdentityType.DATASTORE",
                                "org.estatio.base.prod.dom.EntityAbstract"
                        )),
                // language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.APPLICATION)
                                public class AnotherEntity {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationUnhappyPathForApplication2() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithClassForAnnotationConditionally(
                                "@javax.jdo.annotations.PersistenceCapable",
                                "identityType = IdentityType.DATASTORE",
                                "org.estatio.base.prod.dom.EntityAbstract"
                        )),
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName")
                                public class AnotherEntity {
                                }
                                """
                )
        );
    }

    @Test
    void structuralConditionIgnoresWhitespaceCommentsAndLineBreaks() {
        rewriteRun(spec -> spec.parser(PARSER).recipe(structuralRecipe()),
                java(
                        """
                                import javax.jdo.annotations.IdentityType;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable(identityType/* selector */=/* value */
                                        IdentityType.DATASTORE)
                                class CompactEntity {}
                                """,
                        """
                                import org.estatio.base.prod.dom.EntityAbstract;

                                import javax.jdo.annotations.IdentityType;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable(identityType/* selector */=/* value */
                                        IdentityType.DATASTORE)
                                class CompactEntity extends EntityAbstract {}
                                """));
    }

    @Test
    void structuralConditionSupportsQualifiedAndStaticallyImportedConstants() {
        rewriteRun(spec -> spec.parser(PARSER).recipe(structuralRecipe()),
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable(identityType = javax.jdo.annotations.IdentityType.DATASTORE)
                                class QualifiedEntity {}
                                """,
                        """
                                import org.estatio.base.prod.dom.EntityAbstract;

                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable(identityType = javax.jdo.annotations.IdentityType.DATASTORE)
                                class QualifiedEntity extends EntityAbstract {}
                                """),
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;

                                import static javax.jdo.annotations.IdentityType.DATASTORE;

                                @PersistenceCapable(identityType = DATASTORE)
                                class StaticImportEntity {}
                                """,
                        """
                                import org.estatio.base.prod.dom.EntityAbstract;

                                import javax.jdo.annotations.PersistenceCapable;

                                import static javax.jdo.annotations.IdentityType.DATASTORE;

                                @PersistenceCapable(identityType = DATASTORE)
                                class StaticImportEntity extends EntityAbstract {}
                                """));
    }

    @Test
    void structuralConditionRejectsApplicationAndOmittedIdentity() {
        rewriteRun(spec -> spec.parser(PARSER).recipe(structuralRecipe()),
                java(
                        """
                                import javax.jdo.annotations.IdentityType;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable(identityType = IdentityType.APPLICATION)
                                class ApplicationEntity {}

                                @PersistenceCapable
                                class DefaultIdentityEntity {}
                                """));
    }

    @Test
    void validatesStructuralConfigurationAndExposesOptionalDescriptorFields() {
        assertThat(structuralRecipe().validate().isValid()).isTrue();
        assertThat(new ExtendWithClassForAnnotationConditionally(
                "@javax.jdo.annotations.PersistenceCapable",
                "identityType = IdentityType.DATASTORE",
                "example.Parent",
                "identityType",
                "javax.jdo.annotations.IdentityType.DATASTORE").validate().isInvalid()).isTrue();
        assertThat(new ExtendWithClassForAnnotationConditionally(
                "@javax.jdo.annotations.PersistenceCapable",
                null,
                "example.Parent",
                "identityType",
                null).validate().isInvalid()).isTrue();
        assertThat(structuralRecipe().getDescriptor().getOptions())
                .extracting(option -> option.getName())
                .contains("annotationAttributeName", "annotationAttributeValue");
    }

    @Test
    void legacyAndStructuralYamlConfigurationsLoadWithOriginalPropertyNames() {
        ExtendWithClassForAnnotationConditionally legacy = loadRecipe("""
                annotationCondition: identityType = IdentityType.DATASTORE
                """);
        assertThat(legacy.getAnnotationCondition()).isEqualTo("identityType = IdentityType.DATASTORE");
        assertThat(legacy.getAnnotationAttributeName()).isNull();
        assertThat(legacy.validate().isValid()).isTrue();

        ExtendWithClassForAnnotationConditionally structural = loadRecipe("""
                annotationAttributeName: identityType
                annotationAttributeValue: javax.jdo.annotations.IdentityType.DATASTORE
                """);
        assertThat(structural.getAnnotationCondition()).isNull();
        assertThat(structural.getAnnotationAttributeName()).isEqualTo("identityType");
        assertThat(structural.getAnnotationAttributeValue())
                .isEqualTo("javax.jdo.annotations.IdentityType.DATASTORE");
        assertThat(structural.validate().isValid()).isTrue();

        assertThat(loadRecipe("""
                annotationCondition: identityType = IdentityType.DATASTORE
                annotationAttributeName: identityType
                annotationAttributeValue: javax.jdo.annotations.IdentityType.DATASTORE
                """).validate().isInvalid()).isTrue();
        assertThat(loadRecipe("""
                annotationAttributeName: identityType
                """).validate().isInvalid()).isTrue();
    }

    private static ExtendWithClassForAnnotationConditionally loadRecipe(String condition) {
        String yaml = """
                type: specs.openrewrite.org/v1beta/recipe
                name: example.SuperclassRecipe
                displayName: Test superclass recipe
                description: Test superclass recipe configuration.
                recipeList:
                  - com.ecpnv.openrewrite.java.ExtendWithClassForAnnotationConditionally:
                      annotationPattern: '@javax.jdo.annotations.PersistenceCapable'
                      extendsFullClassName: example.Parent
                """ + condition.indent(6);
        YamlResourceLoader loader = new YamlResourceLoader(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                URI.create("memory://superclass-recipe.yml"),
                new Properties(),
                ExtendWithClassForAnnotationConditionallyTest.class.getClassLoader());
        Recipe activated = Environment.builder().scanRuntimeClasspath().load(loader).build()
                .activateRecipes("example.SuperclassRecipe");
        return findRecipe(activated);
    }

    private static ExtendWithClassForAnnotationConditionally findRecipe(Recipe recipe) {
        if (recipe instanceof ExtendWithClassForAnnotationConditionally conditional) {
            return conditional;
        }
        return recipe.getRecipeList().stream()
                .map(ExtendWithClassForAnnotationConditionallyTest::findRecipe)
                .findFirst().orElseThrow();
    }

    private static ExtendWithClassForAnnotationConditionally structuralRecipe() {
        return new ExtendWithClassForAnnotationConditionally(
                "@javax.jdo.annotations.PersistenceCapable",
                null,
                "org.estatio.base.prod.dom.EntityAbstract",
                "identityType",
                "javax.jdo.annotations.IdentityType.DATASTORE");
    }
}