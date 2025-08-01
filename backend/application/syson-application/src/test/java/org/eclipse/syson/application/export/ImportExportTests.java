/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.eclipse.sirius.web.application.project.dto.CreateProjectInput;
import org.eclipse.sirius.web.application.project.dto.CreateProjectSuccessPayload;
import org.eclipse.sirius.web.application.project.dto.DeleteProjectInput;
import org.eclipse.sirius.web.application.project.services.ProjectApplicationService;
import org.eclipse.sirius.web.application.project.services.api.IProjectApplicationService;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.tests.services.api.IGivenInitialServerState;
import org.eclipse.syson.AbstractIntegrationTests;
import org.eclipse.syson.application.export.checker.SysmlImportExportChecker;
import org.eclipse.syson.sysml.export.SysMLv2DocumentExporter;
import org.eclipse.syson.sysml.upload.SysMLExternalResourceLoaderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests that importing a SysML textual file and then exporting it gives coherent result.
 *
 * @author Arthur Daussy
 */
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImportExportTests extends AbstractIntegrationTests {

    @Autowired
    private SysMLExternalResourceLoaderService sysmlLoader;

    @Autowired
    private IEditingContextSearchService editingContextSearchService;

    @Autowired
    private SysMLv2DocumentExporter exporter;

    @Autowired
    private IGivenInitialServerState givenInitialServerState;

    @Autowired
    private ProjectApplicationService projectCreationService;

    @Autowired
    private IProjectEditingContextService projectToEditingContext;

    @Autowired
    private IProjectApplicationService projectDeletionService;

    private SysmlImportExportChecker checker;

    private String projectId;

    @BeforeEach
    public void setUp() {
        this.givenInitialServerState.initialize();
        UUID randomUUID = UUID.randomUUID();
        IPayload project = this.projectCreationService.createProject(new CreateProjectInput(randomUUID, "ImportExport-" + randomUUID.toString(), List.of()));
        assertThat(project instanceof CreateProjectSuccessPayload);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        this.projectId = ((CreateProjectSuccessPayload) project).project().id().toString();

        Optional<String> editingContextId = this.projectToEditingContext.getEditingContextId(this.projectId);
        Optional<IEditingContext> optEditingContext = this.editingContextSearchService.findById(editingContextId.get());

        this.checker = new SysmlImportExportChecker(this.sysmlLoader, this.exporter, (EditingContext) optEditingContext.get());
    }

    @AfterEach
    public void tearDown() {
        IPayload payload = this.projectDeletionService.deleteProject(new DeleteProjectInput(UUID.randomUUID(), this.projectId));
        assertThat(payload).isInstanceOf(SuccessPayload.class);
    }

    @Test
    @DisplayName("GIVEN a model with basic FlowUsages, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkFlowUsageBaseExample() throws IOException {
        var input = """
                part def P1Def {
                    port po1 : PortDef1;
                }
                port def PortDef1 {
                    out item item1 : P2Def;
                }
                part def P2Def;
                part def P3Def {
                    in item item2 : P3Def;
                }
                part p1 {
                    part p2 : P1Def;
                    part p3 : P3Def;
                    flow from p2.po1.item1 to p3.item2;
                    flow f1 from p2.po1.item1 to p3.item2;
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with FlowUsages with payload, WHEN importing/exporting the file, THEN the exported text file should be semantically the same as the imported one.")
    public void checkFlowUsageWithPayload() throws IOException {
        var input = """
                package 'Port Example' {
                    attribute def Temp;
                    part def Fuel;
                    port def FuelOutPort {
                        attribute temperature : Temp;
                        out item fuelSupply : Fuel;
                        in item fuelReturn : Fuel;
                    }
                    port def FuelInPort {
                        attribute temperature : Temp;
                        in item fuelSupply : Fuel;
                        out item fuelReturn : Fuel;
                    }
                    part def FuelTankAssembly {
                        port fuelTankPort : FuelOutPort;
                    }
                    part def Engine {
                        port engineFuelPort : FuelInPort;
                    }
                }
                package 'Flow Connection Interface Example' {
                    private import 'Port Example'::*;
                    part def Vehicle;
                    part vehicle : Vehicle {
                        part tankAssy : FuelTankAssembly;
                        part eng : Engine;
                        flow of Fuel from tankAssy.fuelTankPort.fuelSupply to eng.engineFuelPort.fuelSupply;
                        flow of Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of [1] Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of Fuel [1] from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of fuel : Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of fuel [1] : Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                    }
                }""";
        // Expected difference : Multiplicity are always after Type
        var expected = """
                package 'Port Example' {
                    attribute def Temp;
                    part def Fuel;
                    port def FuelOutPort {
                        attribute temperature : Temp;
                        out item fuelSupply : Fuel;
                        in item fuelReturn : Fuel;
                    }
                    port def FuelInPort {
                        attribute temperature : Temp;
                        in item fuelSupply : Fuel;
                        out item fuelReturn : Fuel;
                    }
                    part def FuelTankAssembly {
                        port fuelTankPort : FuelOutPort;
                    }
                    part def Engine {
                        port engineFuelPort : FuelInPort;
                    }
                }
                package 'Flow Connection Interface Example' {
                    private import 'Port Example'::*;
                    part def Vehicle;
                    part vehicle : Vehicle {
                        part tankAssy : FuelTankAssembly;
                        part eng : Engine;
                        flow of Fuel from tankAssy.fuelTankPort.fuelSupply to eng.engineFuelPort.fuelSupply;
                        flow of Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of Fuel [1] from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of Fuel [1] from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of fuel : Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of fuel : Fuel [1] from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                        flow of Fuel from eng.engineFuelPort.fuelReturn to tankAssy.fuelTankPort.fuelReturn;
                    }
                }""";
        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a model with ForkNode, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkForkNode() throws IOException {
        var input = """
                action action1 {
                    action a1;
                    action a2;
                    fork fork1;
                    then a1;
                    then a2;
                    first start then fork1;
                }
                action action2 {
                    action a1;
                    action a2;
                    fork fork1 {
                        /* doc1 */
                    }
                    then a1;
                    then a2;
                    first start then fork1;
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with JoinNode, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkJoinNode() throws IOException {
        var input = """
                action action1 {
                    action a1;
                    action a2;
                    fork fork1;
                    then a1;
                    then a2;
                    join join1;
                    first start then fork1;
                    first a1 then join1;
                    first a2 then join1;
                    then done;
                }""";
        /**
         * Here we have differences here because :
         *
         * <ul>
         * <li>The construction of SuccessionAsUsage defining new ActionUsage is hard to detect so we chose to use the
         * complete syntax "first source then target;"</li>
         * <ul>
         */
        var expected = """
                action action1 {
                    action a1;
                    action a2;
                    fork fork1;
                    then a1;
                    then a2;
                    join join1;
                    first start then fork1;
                    first a1 then join1;
                    first a2 then join1;
                    first join1 then done;
                }""";
        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a model with MergeNode, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkMergeNode() throws IOException {
        var input = """
                action action1 {
                    action a1;
                    action a2;
                    fork fork1;
                    then a1;
                    then a2;
                    merge merge1;
                    first start then fork1;
                    first a1 then merge1;
                    first a2 then merge1;
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with TextualRepresentation, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkTextualRepresentation() throws IOException {
        var input = """
                action def P1 {
                    language "naturalLanguage"
                        /* some comment some other comment */
                    rep l2 language "naturalLanguage2"
                        /* some comment 3 */
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with TransitionUsage used between actions, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkTransitionUsageBetweenActions() throws IOException {
        var input = """
                action def A1 {
                    private import ScalarValues::Integer;
                    attribute x : Integer;
                    action a1;
                    action a2;
                    first a1 if x == 1 then a2;
                    succession s1 first a1 if x > 1 & x < 2 then a2;
                    succession s2 first a1 if x > 2 & x < 3 then a2 {
                        doc /* Some documentation on that succession */
                    }
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with ViewUsage, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkViewUsage() throws IOException {
        var input = """
                package AxleAssembly_1 {
                    private import Views::*;
                    part def AxleAssembly;
                    part def Differential;
                    part rearAxleAssembly : AxleAssembly {
                        part differential : Differential;
                        part rearAxle;
                    }
                    view 'First-Level-All' {
                        expose rearAxleAssembly;
                        expose rearAxleAssembly::rearAxle;
                        expose rearAxleAssembly::differential;
                        render asTreeDiagram;
                    }
                } """;
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with AcceptActionUsage, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkAcceptActionUsage() throws IOException {
        var input = """
                part part1 {
                    private import SI::*;
                    private import ScalarValues::*;
                    action b {
                        attribute f : Boolean;
                    }
                    action a1 {
                        item def S1;
                        item def S2;
                        item def S3;
                        port p1;
                        port p2;
                        port p3;
                        action a1 accept s1 : S1 via p1;
                        action a2 accept S2 via p2;
                        accept S3 via p3;
                        accept after 5 [minute];
                        accept when b.f;
                    }
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with a DecisionNode with named TransitionUsage , WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkDecisionWithNamedTransition() throws IOException {
        var input = """
                action def A1 {
                    private import ScalarValues::Integer;
                    attribute x : Integer;
                    action a0;
                    action a1;
                    action a2;
                    action a3;
                    then decide d1;
                        succession sd1 first d1 if x < 0 then a1;
                        succession sd2 first d1 if x == 0 then a2;
                        succession sd3 first d1 then a3;
                }""";
        var expected = """
                action def A1 {
                    private import ScalarValues::Integer;
                    attribute x : Integer;
                    action a0;
                    action a1;
                    action a2;
                    action a3;
                    then d1;
                    decide d1;
                    succession sd1 first d1 if x < 0 then a1;
                    succession sd2 first d1 if x == 0 then a2;
                    succession sd3 first d1 then a3;
                }""";
        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a model with a DecisionNode, WHEN importing/exporting the file, THEN the exported text file should be the same as the imported one.")
    public void checkDecisionNode() throws IOException {
        var input = """
                action def A1 {
                    action a1;
                    action a2;
                    action a3;
                    attribute x : ScalarValues::Real;
                    decide decision1;
                    if x >= 2.1 then a1;
                    if x >= 1.1 and x < 2.1 then a2;
                    else a3;
                }""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with a PortDefinition, WHEN importing/exporting the file, THEN check that the conjugated port reference is kept during the process.")
    public void checkConjugatedPortUse() throws IOException {
        var input = """
                package Conjugated {
                    attribute def Temp;
                    port def TempPort {
                        attribute temperature : Temp;
                    }
                    part def TempPortClassic {
                        port tempPortClassic : TempPort;
                    }
                    part def TempPortConj {
                        port tempPortConj : ~TempPort;
                    }
                }""";

        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a named SuccessionAsUsage, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkNamedSuccessionAsUsageInActionDefinitionTest() throws IOException {
        var input = """
                action def A4 {
                    action a1;
                    action a2;
                    succession s1 first a1 then a2;
                }""";

        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a SuccessionAsUsage with an implicit source feature, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkSuccessionAsUsageImplicitSourceTest() throws IOException {
        var input = """
                action def ActionDef1 {
                    action a0;
                    action a1;
                    action a2;
                    then a1;
                    then a0;
                }""";

        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a SuccessionAsUsage with target defined after the then keyword, WHEN importing and exporting the model,  then the exported text file should be semantically equal.")
    public void checkSuccessionDefiningImplicitTarget() throws IOException {
        var input = """
                action def ActionDef1 {
                    action a0;
                    first a0;
                    then action a1;
                    then action a2;
                }""";
        /**
         * Here we have differences here because :
         *
         * <ul>
         * <li>The construction of SuccessionAsUsage defining new ActionUsage is hard to detect so we chose to use the
         * complete syntax "first source then target;"</li>
         * <ul>
         */
        var expected = """
                action def ActionDef1 {
                    action a0;
                    then a1;
                    action a1;
                    then a2;
                    action a2;
                }""";

        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a SuccessionAsUsage with an implicit source feature targeting the 'start' standard library element, WHEN importing and exporting the model, THEN the exported text file should be semantically equal.")
    public void checkSuccessionAsUsageImplicitSourceToStartTest() throws IOException {
        var input = """
                action def ActionDef1 {
                    action a2;
                    first start;
                    then a2;
                }""";
        /**
         * Here we have differences here because :
         *
         * <ul>
         * <li>The strange construction of the Membership referencing 'start' is hard to detect so we chose to use the
         * complete syntax "first source then target;"</li>
         * <ul>
         */
        var expected = """
                action def ActionDef1 {
                    action a2;
                    first start then a2;
                }""";

        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a SuccessionAsUsage with an explicit source feature, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkSuccessionAsUsageExplicitSourceTest() throws IOException {
        var input = """
                action def ActionDef1 {
                    action a0;
                    action a1;
                    action a2;
                    first a0 then a1;
                    first a1 then a2;
                }""";

        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with unrestricted names, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkUnrestrictedNamesResolution() throws IOException {
        var input = """
                package p1 {
                    package 'p 2' {
                        action def 'A 1';
                    }
                    action 'a 2' : 'p 2'::'A 1';
                }""";

        this.checker.check(input, input);
    }

    /**
     * Test import/export on test file UseCaseTest.sysml. The content of UseCaseTest.sysml that have been copied below
     * is under LGPL-3.0-only license. The LGPL-3.0-only license is accessible at the root of this repository, in the
     * LICENSE-LGPL file.
     *
     * @see <a href=
     *      "https://github.com/Systems-Modeling/SysML-v2-Release/blob/master/sysml/src/examples/Simple%20Tests/UseCaseTest.sysml">UseCaseTest</a>
     */
    @Test
    public void checkUseCaseTest() throws IOException {
        /*
         * The file has been modified because a problem has been detected during the export phase. Those problem force
         * us to use some full-length qualified name. This should be investigated. for example: include
         * UseCaseTest::uc2; instead of include uc2;
         */
        var input = """
                package UseCaseTest {

                    part def System;
                    part def User;

                    use case def UseSystem {
                        subject system : System;
                        actor user : User;

                        objective  {
                            /* Goal */
                        }

                        include use case uc1 : UC1;
                        include use case uc2 {
                            subject = system;
                            actor user = user;
                        }
                    }

                    use case def UC1;

                    part user : User;

                    use case uc2 {
                        actor :>> user;
                    }

                    use case u : UseSystem;

                    part system : System {
                        include UseCaseTest::uc2;
                        perform UseCaseTest::u;
                    }

                }
                """;

        var expected = """
                package UseCaseTest {
                    part def System;
                    part def User;
                    use case def UseSystem {
                        subject system : System;
                        actor user : User;
                        objective {
                            /* Goal */
                        }
                        include use case uc1 : UC1;
                        include use case uc2 {
                            subject = system;
                            actor user = user;
                        }
                    }
                    use case def UC1;
                    part user : User;
                    use case uc2 {
                        actor :>> user;
                    }
                    use case u : UseSystem;
                    part system : System {
                        include uc2;
                        perform u;
                    }
                }""";

        this.checker.check(input, expected);
    }

    /**
     * Test import/export simple PortDef and PortUsage.
     *
     * @throws IOException
     */
    @Test
    @DisplayName("GIVEN a model with PortDefinition and PortUsage, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkImportPort() throws IOException {
        var input = """
                port def Port1;
                part part1 {
                    port port1 : Port1;
                }""";
        this.checker.check(input, input);
    }

    /**
     * Test import/export of AttributeUsages with FeatureValue with different combination of default or initial value.
     *
     * @throws IOException
     */
    @Test
    @DisplayName("GIVEN a model with AttributeUsages with default and initial value, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkScalarValueAttribute() throws IOException {
        var input = """
                package Occurrences {
                    private import ScalarValues::*;
                    occurrence def Occurrence1 {
                        attribute a : Integer;
                        attribute b : Integer default = 1;
                        attribute c : Integer = 1;
                        attribute d : Integer := 3;
                        attribute e : MyEnum default = enum1;
                    }
                    enum def MyEnum {
                        enum1;
                    }
                }""";
        this.checker.check(input, input);
    }

    /**
     * Test import/export on test file ImportTest.sysml.
     *
     * @see <a href=
     *      "https://github.com/Systems-Modeling/SysML-v2-Release/blob/master/sysml/src/examples/Simple%20Tests/ImportTest.sysml">ImportTest</a>
     */
    @Test
    public void checkImportTest() throws IOException {
        /*
         * The file has been modified because a problem has been detected during the export phase. Those problem force
         * us to use some full-length qualified name. This should be investigated. for example: private import
         * Pkg2::Pkg21::Pkg211::*::**; instead of private import Pkg211::*::**;
         */
        var input = """
                package ImportTest {
                    package Pkg1 {
                        private import Pkg2::Pkg21::Pkg211::P211;
                        private import Pkg2::Pkg21::*;
                        private import Pkg211::*::**;
                        part p11 : P211;
                        part def P12;
                    }
                    package Pkg2 {
                        private import Pkg1::*;
                        package Pkg21 {
                            package Pkg211 {
                                part def P211 :> P12;
                            }
                        }
                    }
                }
                """;

        var expected = """
                package ImportTest {
                    package Pkg1 {
                        private import Pkg2::Pkg21::Pkg211::P211;
                        private import Pkg2::Pkg21::*;
                        private import Pkg2::Pkg21::Pkg211::*::**;
                        part p11 : P211;
                        part def P12;
                    }
                    package Pkg2 {
                        private import Pkg1::*;
                        package Pkg21 {
                            package Pkg211 {
                                part def P211 :> P12;
                            }
                        }
                    }
                }""";

        this.checker.check(input, expected);
    }

    /**
     * Checks the import/export of model containing.
     *
     * <ul>
     * <li>ConcernDefintion</li>
     * <li>ConcerUsage</li>
     * <li>StakeholderMembership</li>
     * <li>RequirementUsage</li>
     * <li>RequirementDefinition</li>
     * <ul>
     */
    @Test
    @DisplayName("GIVEN a model with ConcernDefinition, ConcernUsage, StakeholderMembership, RequirementUsage, and RequirementDefinition, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkConcernTest() throws IOException {

        var input = """
                package root {
                    part def PartDef1;
                    concern def ConcernDef1 {
                        stakeholder s : PartDef1;
                    }
                    concern concernUsage1 : ConcernDef1 {
                        stakeholder s1;
                    }
                    requirement def R1Def {
                        stakeholder s : PartDef1;
                    }
                    requirement r1 : R1Def {
                        stakeholder s1;
                    }
                }""";

        this.checker.check(input, input);
    }

    /**
     * Test import/export on test file OccurrenceTest.sysml.
     *
     * @see <a href=
     *      "https://github.com/Systems-Modeling/SysML-v2-Release/blob/master/sysml/src/examples/Simple%20Tests/OccurrenceTest.sysml">OccurrenceTest</a>
     */
    @Test
    public void checkOccurrenceTest() throws IOException {
        var input = """
                package OccurrenceTest {
                    occurrence def Occ {
                        attribute a;
                        ref occurrence occ1 : Occ;
                        occurrence occ2 : Occ;
                        item x;
                        part y;
                        individual snapshot s : Ind;
                        timeslice t;
                    }
                    occurrence occ : Occ {
                        occurrence o1 : Occ;
                        ref occurrence o2 : Occ;
                        item z;
                    }
                    individual occurrence def Ind {
                        snapshot s2;
                        timeslice t2;
                    }
                    individual occurrence ind : Ind, Occ {
                        snapshot s3;
                        individual timeslice t3;
                    }
                    individual snapshot s4 : Ind;
                    occurrence o1 {
                        occurrence o2;
                    }
                }""";

        var expected = """
                package OccurrenceTest {
                    occurrence def Occ {
                        attribute a;
                        ref occurrence occ1 : Occ;
                        occurrence occ2 : Occ;
                        item x;
                        part y;
                        individual snapshot s : Ind;
                        timeslice t;
                    }
                    occurrence occ : Occ {
                        occurrence o1 : Occ;
                        ref occurrence o2 : Occ;
                        item z;
                    }
                    individual occurrence def Ind {
                        snapshot s2;
                        timeslice t2;
                    }
                    individual occurrence ind : Ind, Occ {
                        snapshot s3;
                        individual timeslice t3;
                    }
                    individual snapshot s4 : Ind;
                    occurrence o1 {
                        occurrence o2;
                    }
                }""";

        this.checker.check(input, expected);
    }

    @Test
    @DisplayName("GIVEN a model with a FeatureReferenceExpression set with a qualified name, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkFeatureReferenceExpressionWithQualifiedNameDeresolution() throws IOException {
        var input = """
                package P1 {
                    part p1;
                }
                package P2 {
                    part part2 {
                        part p = P1::p1;
                    }
                }""";

        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with an AttributeUsage containing a LiteralString, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one.")
    public void checkLiteralString() throws IOException {
        var input = """
                attribute myAttribute = "value";""";
        this.checker.check(input, input);
    }

    @Test
    @DisplayName("GIVEN a model with a PartUsage containing a Multiplicity with LiteralInteger bounds, WHEN importing and exporting the model, THEN the exported text file should be the same as the imported one")
    public void checkMultiplicityRangeWithLiteralIntegerBounds() throws IOException {
        var input = """
                part myPart [1..2];""";
        this.checker.check(input, input);
    }
}
