/*******************************************************************************
 * Copyright (c) 2023, 2024 Obeo.
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
package org.eclipse.syson.services;

import java.util.List;
import java.util.Objects;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EValidator.Registry;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IValidationService;
import org.springframework.stereotype.Service;

/**
 * Used to validate EMF elements in SysON.
 *
 * @author arichard
 */
@Service
public class EMFValidationService implements IValidationService {

    private final Registry eValidatorRegistry;

    private final ComposedAdapterFactory composedAdapterFactory;

    public EMFValidationService(EValidator.Registry eValidatorRegistry, ComposedAdapterFactory composedAdapterFactory) {
        this.eValidatorRegistry = Objects.requireNonNull(eValidatorRegistry);
        this.composedAdapterFactory = Objects.requireNonNull(composedAdapterFactory);
    }

    @Override
    public List<Object> validate(IEditingContext editingContext) {
        return List.of();
    }

    @Override
    public List<Object> validate(Object object, Object feature) {
        if (object instanceof EObject) {
            Diagnostician diagnostician = this.getNewDiagnostician();
            Diagnostic diagnostic = diagnostician.validate((EObject) object);
            if (Diagnostic.OK != diagnostic.getSeverity()) {
                return diagnostic.getChildren().stream()
                        .filter(diag -> this.filterDiagnosticByObject(diag, object))
                        .filter(diag -> this.filterDiagnosticByFeature(diag, feature))
                        .map(Object.class::cast)
                        .toList();
            }
        }

        return List.of();
    }

    private boolean filterDiagnosticByObject(Diagnostic diagnostic, Object object) {
        if (diagnostic.getData() != null && !diagnostic.getData().isEmpty() && object != null) {
            return diagnostic.getData().stream()
                    .anyMatch(object::equals);
        }
        return false;
    }

    private boolean filterDiagnosticByFeature(Diagnostic diagnostic, Object feature) {
        if (diagnostic.getData() != null && !diagnostic.getData().isEmpty() && feature != null) {
            return diagnostic.getData().stream()
                    .anyMatch(feature::equals);
        }
        return false;
    }

    private Diagnostician getNewDiagnostician() {
        return new Diagnostician(this.eValidatorRegistry) {
            @Override
            public String getObjectLabel(EObject eObject) {
                IItemLabelProvider itemLabelProvider = (IItemLabelProvider) EMFValidationService.this.composedAdapterFactory.adapt(eObject, IItemLabelProvider.class);
                if (itemLabelProvider != null) {
                    return itemLabelProvider.getText(eObject);
                }

                return super.getObjectLabel(eObject);
            }
        };
    }
}
