package com.ecpnv.openrewrite.jdo2jpa.causeway;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.xml.Assertions.xml;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class LayoutXmlTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway");
    }

    /**
     * Tests the removal of actions that are considered obsolete from a layout XML file.
     * <p>
     * The test validates that certain actions, identified as obsolete, are no longer present
     * in the resulting XML. The test takes an initial layout XML containing a set of configured
     * actions and ensures that the output layout XML excludes the specified obsolete actions,
     * while leaving the rest of the content unchanged.
     * <p>
     * This ensures that the recipe applied to the layout XML correctly identifies and removes
     * the designated obsolete actions, maintaining a clean and updated configuration.
     */
    @DocumentExample
    @Test
    void removeObsoleteActions() {
        rewriteRun(
                //language=xml
                xml(
                        """
                                <bs3:grid>
                                    <bs3:row>
                                        <bs3:col>
                                            <bs3:tabGroup>
                                                <bs3:tab name="Metadata">
                                                    <bs3:row>
                                                        <bs3:col span="12">
                                                            <cpt:fieldSet name="Metadata" id="metadata">
                                                                <cpt:action id="links"/>
                                                                <cpt:action id="clearHints" position="PANEL"/>
                                                                <cpt:action id="rebuildMetamodel" position="PANEL"/>
                                                                <cpt:action id="recentCommands" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="recentAuditEntries" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="recentChanges" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="findChangesByDate" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="downloadLayoutXml" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="downloadJdoMetadata" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="downloadMetaModelXml" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="openInAnotherStack" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="openRestApi" position="PANEL_DROPDOWN"/>
                                                                <cpt:property id="id"/>
                                                                <cpt:property id="versionSequence"/>
                                                                <cpt:property id="datanucleusId"/>
                                                                <cpt:property id="datanucleusVersionLong"/>
                                                                <cpt:property id="datanucleusVersionTimestamp"/>
                                                            </cpt:fieldSet>
                                                        </bs3:col>
                                                    </bs3:row>
                                                </bs3:tab>
                                            </bs3:tabGroup>
                                        </bs3:col>
                                    </bs3:row>
                                </bs3:grid>
                                """,
                        """
                                <bs3:grid>
                                    <bs3:row>
                                        <bs3:col>
                                            <bs3:tabGroup>
                                                <bs3:tab name="Metadata">
                                                    <bs3:row>
                                                        <bs3:col span="12">
                                                            <cpt:fieldSet name="Metadata" id="metadata">
                                                                <cpt:action id="links"/>
                                                                <cpt:action id="clearHints" position="PANEL"/>
                                                                <cpt:action id="rebuildMetamodel" position="PANEL"/>
                                                                <cpt:action id="recentCommands" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="recentAuditEntries" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="recentChanges" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="findChangesByDate" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="downloadLayoutXml" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="downloadMetaModelXml" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="openInAnotherStack" position="PANEL_DROPDOWN"/>
                                                                <cpt:action id="openRestApi" position="PANEL_DROPDOWN"/>
                                                                <cpt:property id="id"/>
                                                                <cpt:property id="versionSequence"/>
                                                            </cpt:fieldSet>
                                                        </bs3:col>
                                                    </bs3:row>
                                                </bs3:tab>
                                            </bs3:tabGroup>
                                        </bs3:col>
                                    </bs3:row>
                                </bs3:grid>
                                """,
                        spec -> spec.path("test.layout.xml")
                )
        );
    }


    /**
     * Tests the removal of obsolete menu actions from a layout XML file.
     * <p>
     * This test verifies that specific actions, flagged as obsolete, are appropriately removed
     * from a given XML configuration. The test ensures that non-obsolete actions and the
     * overall structure of the XML remain unaffected after processing.
     * <p>
     * Input XML contains multiple sections with menu actions. After running the test,
     * the resulting XML is expected to exclude the obsolete actions while retaining
     * all valid actions and structural elements.
     * <p>
     * The test checks the correctness of the implemented recipe in cleaning up obsolete
     * configurations to maintain an updated and optimized layout XML.
     */
    @DocumentExample
    @Test
    void removeObsoleteMenuActions() {
        rewriteRun(
                //language=xml
                xml(
                        """
                                <mb3:menuBars>
                                    <mb3:primary>
                                        <mb3:menu>
                                            <mb3:section>
                                                <mb3:named>Layouts</mb3:named>
                                                <mb3:serviceAction objectType="isis.applib.SitemapServiceMenu" id="downloadSitemap"/>
                                            </mb3:section>
                                            <mb3:section>
                                                <mb3:named>Persistence</mb3:named>
                                                <mb3:serviceAction objectType="isis.persistence.jdo.JdoMetamodelMenu" id="downloadMetamodels"/>
                                                <mb3:serviceAction objectType="isis.ext.h2Console.H2ManagerMenu" id="openH2Console"/>
                                            </mb3:section>
                                        </mb3:menu>
                                    </mb3:tertiary>
                                </mb3:menuBars>
                                """,
                        """
                                <mb3:menuBars>
                                    <mb3:primary>
                                        <mb3:menu>
                                            <mb3:section>
                                                <mb3:named>Layouts</mb3:named>
                                                <mb3:serviceAction objectType="isis.applib.SitemapServiceMenu" id="downloadSitemap"/>
                                            </mb3:section>
                                            <mb3:section>
                                                <mb3:named>Persistence</mb3:named>
                                                <mb3:serviceAction objectType="isis.ext.h2Console.H2ManagerMenu" id="openH2Console"/>
                                            </mb3:section>
                                        </mb3:menu>
                                    </mb3:tertiary>
                                </mb3:menuBars>
                                """,
                        spec -> spec.path("test.layout.xml")
                )
        );
    }
}
