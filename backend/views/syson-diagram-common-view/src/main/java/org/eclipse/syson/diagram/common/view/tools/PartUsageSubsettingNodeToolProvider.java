/*******************************************************************************
 * Copyright (c) 2024 Obeo.
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

package org.eclipse.syson.diagram.common.view.tools;

import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.view.builder.IViewDiagramElementFinder;
import org.eclipse.sirius.components.view.builder.generated.diagram.DiagramBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.INodeToolProvider;
import org.eclipse.sirius.components.view.diagram.NodeContainmentKind;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.components.view.diagram.NodeTool;
import org.eclipse.syson.sysml.SysmlPackage;
import org.eclipse.syson.util.AQLConstants;
import org.eclipse.syson.util.AQLUtils;
import org.eclipse.syson.util.IDescriptionNameGenerator;

/**
 * Node tool provider to create a subsetting edge from a Part usage to a new Part usage.
 *
 * @author Jerome Gout
 */
public class PartUsageSubsettingNodeToolProvider implements INodeToolProvider {

    private final DiagramBuilders diagramBuilderHelper = new DiagramBuilders();

    private final ViewBuilders viewBuilderHelper = new ViewBuilders();

    private final IDescriptionNameGenerator nameGenerator;

    public PartUsageSubsettingNodeToolProvider(IDescriptionNameGenerator nameGenerator) {
        this.nameGenerator = Objects.requireNonNull(nameGenerator);
    }

    @Override
    public NodeTool create(IViewDiagramElementFinder cache) {
        var builder = this.diagramBuilderHelper.newNodeTool();

        var creationSubsettingServiceCall = this.viewBuilderHelper.newChangeContext()
                .expression(AQLUtils.getSelfServiceCallExpression("createPartUsageAndSubsetting"))
                .build();

        NodeDescription nodeDescription = cache.getNodeDescription(nameGenerator.getNodeName(SysmlPackage.eINSTANCE.getPartUsage())).orElse(null);

        var createView = this.diagramBuilderHelper.newCreateView()
                .containmentKind(NodeContainmentKind.CHILD_NODE)
                .elementDescription(nodeDescription)
                .parentViewExpression(AQLUtils.getSelfServiceCallExpression("getParentNode", List.of("selectedNode", "diagramContext")))
                .semanticElementExpression(AQLConstants.AQL_SELF)
                .variableName("newView");

        var rootChangContext = this.viewBuilderHelper.newChangeContext()
                .expression(AQLConstants.AQL_SELF)
                .children(creationSubsettingServiceCall, createView.build())
                .build();

        return builder.name(nameGenerator.getCreationToolName(SysmlPackage.eINSTANCE.getSubsetting()))
                .iconURLsExpression("/icons/full/obj16/Subsetting.svg")
                .body(rootChangContext)
                .build();
    }
}