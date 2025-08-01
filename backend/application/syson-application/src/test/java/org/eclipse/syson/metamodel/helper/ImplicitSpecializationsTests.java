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
package org.eclipse.syson.metamodel.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.components.collaborative.diagrams.dto.DiagramEventInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DiagramRefreshedEventPayload;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.web.tests.services.api.IGivenInitialServerState;
import org.eclipse.syson.AbstractIntegrationTests;
import org.eclipse.syson.application.controller.editingContext.checkers.ISemanticChecker;
import org.eclipse.syson.application.controller.editingContext.checkers.SemanticCheckerService;
import org.eclipse.syson.application.data.GeneralViewWithTopNodesTestProjectData;
import org.eclipse.syson.services.SemanticRunnableFactory;
import org.eclipse.syson.services.diagrams.api.IGivenDiagramSubscription;
import org.eclipse.syson.sysml.AcceptActionUsage;
import org.eclipse.syson.sysml.ActionUsage;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.PartDefinition;
import org.eclipse.syson.sysml.PartUsage;
import org.eclipse.syson.sysml.Redefinition;
import org.eclipse.syson.sysml.Specialization;
import org.eclipse.syson.sysml.SysmlFactory;
import org.eclipse.syson.sysml.helper.EMFUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import reactor.test.StepVerifier;
import reactor.test.StepVerifier.Step;

/**
 * Tests the implicit specializations of SysMLv2 elements. We need an integration test to benefit from the standard
 * libraries.
 *
 * @author arichard
 */
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImplicitSpecializationsTests extends AbstractIntegrationTests {

    @Autowired
    private IGivenInitialServerState givenInitialServerState;

    @Autowired
    private IGivenDiagramSubscription givenDiagramSubscription;

    @Autowired
    private IObjectSearchService objectSearchService;

    @Autowired
    private SemanticRunnableFactory semanticRunnableFactory;

    private Step<DiagramRefreshedEventPayload> verifier;

    private SemanticCheckerService semanticCheckerService;

    @BeforeEach
    public void setUp() {
        this.givenInitialServerState.initialize();
        var diagramEventInput = new DiagramEventInput(UUID.randomUUID(),
                GeneralViewWithTopNodesTestProjectData.EDITING_CONTEXT_ID,
                GeneralViewWithTopNodesTestProjectData.GraphicalIds.DIAGRAM_ID);
        var flux = this.givenDiagramSubscription.subscribe(diagramEventInput);
        this.verifier = StepVerifier.create(flux);
        this.semanticCheckerService = new SemanticCheckerService(this.semanticRunnableFactory, this.objectSearchService, GeneralViewWithTopNodesTestProjectData.EDITING_CONTEXT_ID,
                GeneralViewWithTopNodesTestProjectData.SemanticIds.PACKAGE_1_ID);
    }

    @AfterEach
    public void tearDown() {
        if (this.verifier != null) {
            this.verifier.thenCancel()
                    .verify(Duration.ofSeconds(1));
        }
    }

    @DisplayName("GIVEN a PartUsage with or without Specialization (FeatureTyping, Susbetting or Redefinition), WHEN validation is executed, THEN the Part implictly specializes 'Parts::parts' from the standard libraries.")
    @Sql(scripts = { GeneralViewWithTopNodesTestProjectData.SCRIPT_PATH }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
            config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(scripts = { "/scripts/cleanup.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Test
    public void testImplicitSpecializationOnPartUsage() {
        ISemanticChecker semanticChecker = (editingContext) -> {
            Object semanticRootObject = this.objectSearchService.getObject(editingContext, GeneralViewWithTopNodesTestProjectData.SemanticIds.PACKAGE_1_ID).orElse(null);
            assertThat(semanticRootObject).isInstanceOf(Element.class);
            Element semanticRootElement = (Element) semanticRootObject;
            Optional<PartUsage> optPartUsage = EMFUtils.allContainedObjectOfType(semanticRootElement, PartUsage.class)
                    .filter(element -> Objects.equals(element.getName(), "part"))
                    .findFirst();
            assertThat(optPartUsage).isPresent();
            PartUsage partUsage = optPartUsage.get();

            assertTrue(partUsage.specializesFromLibrary("Parts::parts"));
            assertTrue(partUsage.specializesFromLibrary("Parts::Part"));

            Optional<PartDefinition> optPartDef = EMFUtils.allContainedObjectOfType(semanticRootElement, PartDefinition.class)
                    .filter(element -> Objects.equals(element.getName(), "PartDefinition"))
                    .findFirst();
            assertThat(optPartDef).isPresent();
            PartDefinition partDef = optPartDef.get();

            var newFeatureTyping = SysmlFactory.eINSTANCE.createFeatureTyping();
            partUsage.getOwnedRelationship().add(newFeatureTyping);
            newFeatureTyping.setType(partDef);
            newFeatureTyping.setTypedFeature(partUsage);

            assertTrue(partUsage.specializesFromLibrary("Parts::parts"));
            assertTrue(partUsage.specializesFromLibrary("Parts::Part"));
            assertTrue(partDef.specializesFromLibrary("Parts::Part"));

            var newMembership = SysmlFactory.eINSTANCE.createOwningMembership();
            semanticRootElement.getOwnedRelationship().add(newMembership);
            var anotherPart = SysmlFactory.eINSTANCE.createPartUsage();
            newMembership.getOwnedRelatedElement().add(anotherPart);

            var newSubsetting = SysmlFactory.eINSTANCE.createSubsetting();
            partUsage.getOwnedRelationship().add(newSubsetting);
            newSubsetting.setSubsettedFeature(anotherPart);
            newSubsetting.setSubsettingFeature(partUsage);

            assertTrue(partUsage.specializesFromLibrary("Parts::parts"));
            assertTrue(partUsage.specializesFromLibrary("Parts::Part"));
            assertTrue(anotherPart.specializesFromLibrary("Parts::parts"));
            assertTrue(anotherPart.specializesFromLibrary("Parts::Part"));
        };

        this.semanticCheckerService.checkEditingContext(semanticChecker, this.verifier);
    }

    @DisplayName("GIVEN an AcceptAction, WHEN the validation is executed, THEN the AcceptAction implictly specializes 'Actions::acceptActions'")
    @Sql(scripts = { GeneralViewWithTopNodesTestProjectData.SCRIPT_PATH }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
            config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(scripts = { "/scripts/cleanup.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Test
    public void testImplicitSpecializationOnAcceptActionUsage() {
        ISemanticChecker semanticChecker = (editingContext) -> {
            Object semanticRootObject = this.objectSearchService.getObject(editingContext, GeneralViewWithTopNodesTestProjectData.SemanticIds.PACKAGE_1_ID).orElse(null);
            assertThat(semanticRootObject).isInstanceOf(Element.class);
            Element semanticRootElement = (Element) semanticRootObject;
            Optional<AcceptActionUsage> optAcceptActionUsage = EMFUtils.allContainedObjectOfType(semanticRootElement, AcceptActionUsage.class)
                    .filter(element -> Objects.equals(element.getName(), "acceptAction"))
                    .findFirst();
            assertThat(optAcceptActionUsage).isPresent();
            AcceptActionUsage acceptActionUsage = optAcceptActionUsage.get();

            assertTrue(acceptActionUsage.specializesFromLibrary("Actions::acceptActions"));
        };

        this.semanticCheckerService.checkEditingContext(semanticChecker, this.verifier);
    }

    @DisplayName("GIVEN a parameter of an ActionUsage that subsets another ActionUsage with parameters, WHEN the validation is executed, THEN the ActionUsgae implicitly redefines the corresponding parameter")
    @Sql(scripts = { GeneralViewWithTopNodesTestProjectData.SCRIPT_PATH }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
            config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(scripts = { "/scripts/cleanup.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Test
    public void testImplicitParameterRedefinitionOnItemUsage() {
        ISemanticChecker semanticChecker = (editingContext) -> {
            Object semanticRootObject = this.objectSearchService.getObject(editingContext, GeneralViewWithTopNodesTestProjectData.SemanticIds.PACKAGE_1_ID).orElse(null);
            assertThat(semanticRootObject).isInstanceOf(Element.class);
            Element semanticRootElement = (Element) semanticRootObject;
            Optional<ActionUsage> optionalParentActionWithParameter = EMFUtils.allContainedObjectOfType(semanticRootElement, ActionUsage.class)
                    .filter(actionUsage -> Objects.equals(actionUsage.getName(), "parentActionWithParameter"))
                    .findFirst();
            assertThat(optionalParentActionWithParameter).isPresent();
            ActionUsage parentActionWithParameter = optionalParentActionWithParameter.get();
            Optional<ActionUsage> optionalSubActionWithParameter = EMFUtils.allContainedObjectOfType(semanticRootElement, ActionUsage.class)
                    .filter(actionUsage -> Objects.equals(actionUsage.getName(), "subActionWithParameter"))
                    .findFirst();
            assertThat(optionalSubActionWithParameter).isPresent();
            ActionUsage subActionWithParameter = optionalSubActionWithParameter.get();
            assertThat(parentActionWithParameter.getParameter()).hasSize(1);
            assertThat(subActionWithParameter.getParameter()).hasSize(1);
            List<Specialization> subParameterSpecializations = subActionWithParameter.getParameter().get(0).getOwnedSpecialization();
            Optional<Redefinition> optionalRedefinition = subParameterSpecializations.stream()
                    .filter(Redefinition.class::isInstance)
                    .map(Redefinition.class::cast)
                    .findFirst();
            assertThat(optionalRedefinition).isPresent();
            assertThat(optionalRedefinition)
                    .get()
                    .extracting(Redefinition::getRedefinedFeature)
                    .isEqualTo(parentActionWithParameter.getParameter().get(0));
        };
        this.semanticCheckerService.checkEditingContext(semanticChecker, this.verifier);
    }
}
