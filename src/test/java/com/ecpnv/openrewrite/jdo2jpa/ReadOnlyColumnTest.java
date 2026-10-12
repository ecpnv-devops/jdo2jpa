package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;

/**
 * DataNucleus' {@code insertable}/{@code updateable} column extensions (a column the ORM reads but never writes) map
 * to JPA's {@code @Column(insertable = false, updatable = false)}.
 */
class ReadOnlyColumnTest extends BaseRewriteTest {

    @Test
    void insertable_and_updateable_extensions_become_insertable_and_updatable_attributes() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Column"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                import javax.jdo.annotations.Extension;
                                import javax.persistence.Entity;

                                @Entity
                                class Occupancy {
                                    @Column(name = "effectiveStartDate", allowsNull = "true", extensions = {
                                            @Extension(vendorName = "datanucleus", key = "insertable", value = "false"),
                                            @Extension(vendorName = "datanucleus", key = "updateable", value = "false")})
                                    private String effectiveStartDatePersisted;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Entity;

                                @Entity
                                class Occupancy {
                                    @Column(updatable = false, insertable = false, name = "effectiveStartDate")
                                    private String effectiveStartDatePersisted;
                                }
                                """));
    }

    @Test
    void only_updateable_extension_becomes_updatable_attribute() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Column"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                import javax.jdo.annotations.Extension;
                                import javax.persistence.Entity;

                                @Entity
                                class Thing {
                                    @Column(name = "createdBy", extensions = {
                                            @Extension(vendorName = "datanucleus", key = "updateable", value = "false")})
                                    private String createdBy;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Entity;

                                @Entity
                                class Thing {
                                    @Column(updatable = false, name = "createdBy")
                                    private String createdBy;
                                }
                                """));
    }
}
