package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

/**
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
class ExtendWithAbstractEntityForPersistenceCapableAnnotationTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithAbstractEntityForPersistenceCapableAnnotation(
                                "org.estatio.base.prod.dom.EntityAbstract",
                                "jdo2jpa-abstract")),
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
                        .recipe(new ExtendWithAbstractEntityForPersistenceCapableAnnotation(
                                "org.estatio.base.prod.dom.EntityAbstract",
                                "jdo2jpa-abstract")),
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
                        .recipe(new ExtendWithAbstractEntityForPersistenceCapableAnnotation(
                                "org.estatio.base.prod.dom.EntityAbstract",
                                "jdo2jpa-abstract")),
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
}