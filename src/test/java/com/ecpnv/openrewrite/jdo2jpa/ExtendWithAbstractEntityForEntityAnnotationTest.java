package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class ExtendWithAbstractEntityForEntityAnnotationTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void extendWithAbstractEntityAnnotationHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithAbstractEntityForEntityAnnotation()),
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class SomeEntity implements Comparable<SomeEntity> {
                                    private List<String> listofStrings;

                                    @Override
                                    public int compareTo(SomeEntity o) {
                                        return 0;
                                    }
                                }
                                """,
                        """
                                import java.util.List;
                                
                                import javax.persistence.Entity;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                public class SomeEntity extends EntityAbstract {
                                    private List<String> listofStrings;

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
    void extendWithAbstractEntityAnnotationUnhappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithAbstractEntityForEntityAnnotation()),
                java(
                        """
                                import javax.persistence.Entity;
                                
                                @Entity
                                public class AnotherEntity extends String {
                                }
                                """
                )
        );
    }
}