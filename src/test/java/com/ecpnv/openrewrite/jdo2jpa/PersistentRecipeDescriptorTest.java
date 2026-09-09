package com.ecpnv.openrewrite.jdo2jpa;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentRecipeDescriptorTest {

    @Test
    void persistentStagesAreExplicitlyOrdered() throws IOException {
        String descriptor;
        try (InputStream input = getClass().getResourceAsStream(
                "/META-INF/rewrite/datanucleus-jdo-to-jpa-eclipselink.yml")) {
            assertThat(input).isNotNull();
            descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int persistent = descriptor.indexOf("name: com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent\n");
        int relationships = descriptor.indexOf(
                "  - com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent.relationships", persistent);
        int scalar = descriptor.indexOf(
                "  - com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent.scalarFetch", persistent);
        int cleanup = descriptor.indexOf(
                "  - com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent.cleanup", persistent);
        int nextRecipe = descriptor.indexOf("\n---", persistent);

        assertThat(persistent).isGreaterThanOrEqualTo(0);
        assertThat(relationships).isBetween(persistent, nextRecipe);
        assertThat(scalar).isBetween(relationships, nextRecipe);
        assertThat(cleanup).isBetween(scalar, nextRecipe);
    }
}
