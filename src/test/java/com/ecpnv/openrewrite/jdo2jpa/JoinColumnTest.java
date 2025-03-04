package org.estatio.module.country.dom;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

public class JoinColumnTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void testJoinColumnUnidirectional() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                package module.country.dom;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.Column;
                                
                                import lombok.Getter;
                                import lombok.Setter;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                public class Country extends EntityAbstract {
                                    @Getter @Setter
                                    @Column(allowsNull = "false", length = 255)
                                    private String name;
                                }
                                
                                @Entity
                                public class State extends EntityAbstract {
                                    @Getter @Setter
                                    @Column(allowsNull = "false", length = 255)
                                    private String name;
                                
                                    @Getter @Setter
                                    @Column(allowsNull = "false", name = "countryId")
                                    private Country country;
                                }
                                """
                ,
                        """
                                package module.country.dom;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.JoinColumn;
                                import javax.persistence.ManyToOne;
                                import javax.persistence.Column;
                                
                                import lombok.Getter;
                                import lombok.Setter;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                public class Country extends EntityAbstract {
                                    @Getter @Setter
                                            @Column(nullable = false, length = 255)
                                    private String name;
                                }
                                
                                @Entity
                                public class State extends EntityAbstract {
                                    @Getter @Setter
                                            @Column(nullable = false, length = 255)
                                    private String name;
                                
                                    @Getter 
                                    @Setter
                                    @ManyToOne(optional = false)
                                    @JoinColumn(nullable = false, name = "countryId")
                                    private Country country;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void testNoJoinColumn() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                package module.country.dom;
                                
                                import javax.persistence.Entity;
                                
                                import lombok.Getter;
                                import lombok.Setter;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                public class Country extends EntityAbstract {
                                    @Getter @Setter
                                    private String name;
                                }
                                
                                @Entity
                                public class State extends EntityAbstract {
                                    @Getter @Setter
                                    private String name;
                                
                                    @Getter @Setter
                                    private Country country;
                                }
                                """
                ,
                        """
                                package module.country.dom;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.ManyToOne;
                                
                                import lombok.Getter;
                                import lombok.Setter;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                public class Country extends EntityAbstract {
                                    @Getter @Setter
                                    private String name;
                                }
                                
                                @Entity
                                public class State extends EntityAbstract {
                                    @Getter @Setter
                                    private String name;
                                
                                    @Getter
                                    @Setter
                                    @ManyToOne()
                                    private Country country;
                                }
                                """
                )
        );
    }
}
