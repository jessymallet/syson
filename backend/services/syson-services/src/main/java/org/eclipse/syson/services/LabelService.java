/*******************************************************************************
 * Copyright (c) 2023, 2025 Obeo.
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

import java.text.MessageFormat;
import java.util.Objects;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.core.api.IFeedbackMessageService;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.syson.services.grammars.DirectEditLexer;
import org.eclipse.syson.services.grammars.DirectEditParser;
import org.eclipse.syson.sysml.AttributeUsage;
import org.eclipse.syson.sysml.Comment;
import org.eclipse.syson.sysml.ConstraintUsage;
import org.eclipse.syson.sysml.Definition;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.Feature;
import org.eclipse.syson.sysml.FeatureDirectionKind;
import org.eclipse.syson.sysml.FeatureTyping;
import org.eclipse.syson.sysml.FeatureValue;
import org.eclipse.syson.sysml.LiteralExpression;
import org.eclipse.syson.sysml.MultiplicityRange;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.Redefinition;
import org.eclipse.syson.sysml.RequirementConstraintMembership;
import org.eclipse.syson.sysml.Subclassification;
import org.eclipse.syson.sysml.Subsetting;
import org.eclipse.syson.sysml.TextualRepresentation;
import org.eclipse.syson.sysml.Type;
import org.eclipse.syson.sysml.Usage;
import org.eclipse.syson.sysml.VariantMembership;
import org.eclipse.syson.sysml.helper.LabelConstants;
import org.eclipse.syson.sysml.textual.SysMLElementSerializer;
import org.eclipse.syson.sysml.textual.utils.FileNameDeresolver;
import org.eclipse.syson.sysml.textual.utils.INameDeresolver;
import org.eclipse.syson.sysml.util.ElementUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Label-related Java services used by SysON representations.
 *
 * @author arichard
 */
public class LabelService {

    public static final String MULTIPLICITY_OFF = "MULTIPLICITY_OFF";

    public static final String NAME_OFF = "NAME_OFF";

    public static final String REDEFINITION_OFF = "REDEFINITION_OFF";

    public static final String SUBSETTING_OFF = "SUBSETTING_OFF";

    public static final String TYPING_OFF = "TYPING_OFF";

    public static final String VALUE_OFF = "VALUE_OFF";

    public static final String TRANSITION_EXPRESSION_OFF = "TRANSITION_EXPRESSION_OFF";

    private final Logger logger = LoggerFactory.getLogger(LabelService.class);

    private final IFeedbackMessageService feedbackMessageService;

    public LabelService(IFeedbackMessageService feedbackMessageService) {
        this.feedbackMessageService = Objects.requireNonNull(feedbackMessageService);
    }

    public IFeedbackMessageService getFeedbackMessageService() {
        return this.feedbackMessageService;
    }

    /**
     * Return the container label for the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @return the container label for the given {@link Element}.
     */
    public String getContainerLabel(Element element) {
        return new MultiLineLabelSwitch(this).doSwitch(element);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @param newLabel
     *            the new value to apply.
     * @return the given {@link Element}.
     */
    public Element directEdit(Element element, String newLabel) {
        return this.directEdit(element, newLabel, (String[]) null);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given {@link Element}, with the provided {@code options}.
     * <p>
     * This method is typically used to enable direct edit with some restrictions (e.g. de-activate the ability to edit
     * the name of an element via direct edit). See {@link #directEdit(Element, String)} to perform a direct edit with
     * default options.
     * </p>
     *
     * @param element
     *            the given {@link Element}
     * @param newLabel
     *            the new value to apply
     * @param options
     *            the options of the direct edit
     * @return the given {@link Element}
     */
    public Element directEdit(Element element, String newLabel, String... options) {
        return this.directEdit(element, newLabel, false, options);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given graphical node {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @param newLabel
     *            the new value to apply.
     * @return the given {@link Element}.
     */
    public Element directEditNode(Element element, String newLabel) {
        return this.directEdit(element, newLabel, false, (String[]) null);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given graphical list item {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @param newLabel
     *            the new value to apply.
     * @return the given {@link Element}.
     */
    public Element directEditListItem(Element element, String newLabel) {
        return this.directEdit(element, newLabel, true, (String[]) null);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given {@link Element} without changing the name of the
     * element itself.
     *
     * @param element
     *            the given {@link Element}.
     * @param newLabel
     *            the new value to apply.
     * @return the given {@link Element}.
     */
    public Element directEditNameOff(Element element, String newLabel) {
        return this.directEdit(element, newLabel, LabelService.NAME_OFF);
    }

    /**
     * Apply the direct edit result (i.e. the newLabel) to the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @param newLabel
     *            the new value to apply.
     * @return the given {@link Element}.
     */
    public Element directEdit(Element element, String newLabel, boolean isCompartmentItem, String... options) {
        DirectEditLexer lexer = new DirectEditLexer(CharStreams.fromString(newLabel));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DirectEditParser parser = new DirectEditParser(tokens);
        ParseTree tree;
        if (element instanceof ConstraintUsage && element.getOwningMembership() instanceof RequirementConstraintMembership && isCompartmentItem) {
            // Use the constraint expression parser only if the element is a constraint owned by a requirement, other
            // constraints (including requirements) are parsed as regular elements.
            tree = parser.constraintExpression();
        } else if (isCompartmentItem) {
            tree = parser.listItemExpression();
        } else {
            tree = parser.nodeExpression();
        }
        ParseTreeWalker walker = new ParseTreeWalker();
        DiagramDirectEditListener listener = new DiagramDirectEditListener(element, this.getFeedbackMessageService(), options);
        walker.walk(listener, tree);
        listener.resolveProxies().forEach(proxy -> {
            this.feedbackMessageService.addFeedbackMessage(new Message(MessageFormat.format("Unable to resolve `{0}`", proxy.nameToResolve()), MessageLevel.WARNING));
        });
        return element;
    }

    /**
     * Get the value to display when a direct edit has been called on the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @return the value to display.
     */
    public String getDefaultInitialDirectEditLabel(Element element) {
        StringBuilder builder = new StringBuilder();
        if (element instanceof Usage usage) {
            builder.append(this.getBasicNamePrefix(usage));
        }
        builder.append(this.getIdentificationLabel(element));
        builder.append(this.getMultiplicityStringRepresentation(element, true));
        builder.append(this.getTypingLabel(element));
        builder.append(this.getRedefinitionLabel(element));
        builder.append(this.getSubsettingLabel(element));
        builder.append(this.getSubclassificationLabel(element));
        if (element instanceof Usage usage) {
            builder.append(this.getValueStringRepresentation(usage, true));
        }
        return builder.toString();
    }

    /**
     * Get the value to display when a direct edit has been called on the given {@link Comment}.
     *
     * @param comment
     *            the given {@link Comment}.
     * @return the value to display.
     */
    public String getDefaultInitialDirectEditLabel(Comment comment) {
        return comment.getBody();
    }

    /**
     * Get the value to display when a direct edit has been called on the given {@link TextualRepresentation}.
     *
     * @param textualRepresentation
     *            the given {@link Comment}.
     * @return the value to display.
     */
    public String getDefaultInitialDirectEditLabel(TextualRepresentation textualRepresentation) {
        return textualRepresentation.getBody();
    }

    /**
     * Return the label of the multiplicity part of the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @return the label of the multiplicity part of the given {@link Element} if there is one, an empty string
     *         otherwise.
     */
    public String getMultiplicityLabel(Element element) {
        return this.getMultiplicityStringRepresentation(element, false);
    }

    /**
     * Return the label of the multiplicity part of the given {@link Element}.
     *
     * @param element
     *            element the given {@link Element}.
     * @param directEditInput
     *            holds <code>true</code> if the label is used as a direct edit input
     * @return the label of the multiplicity part of the given {@link Element} if there is one, an empty string
     *         otherwise.
     */
    public String getMultiplicityStringRepresentation(Element element, boolean directEditInput) {
        StringBuilder label = new StringBuilder();
        var optMultiplicityRange = element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(m -> m.getOwnedRelatedElement().stream())
                .filter(MultiplicityRange.class::isInstance)
                .map(MultiplicityRange.class::cast)
                .findFirst();
        label.append(LabelConstants.SPACE);
        if (optMultiplicityRange.isPresent()) {
            var range = optMultiplicityRange.get();
            String firstBound = null;
            String secondBound = null;
            var bounds = range.getOwnedRelationship().stream()
                    .filter(OwningMembership.class::isInstance)
                    .map(OwningMembership.class::cast)
                    .flatMap(m -> m.getOwnedRelatedElement().stream())
                    .filter(LiteralExpression.class::isInstance)
                    .map(LiteralExpression.class::cast)
                    .toList();
            if (bounds.size() == 1) {
                firstBound = this.getSysmlTextualRepresentation(bounds.get(0), directEditInput);
            } else if (bounds.size() == 2) {
                firstBound = this.getSysmlTextualRepresentation(bounds.get(0), directEditInput);
                secondBound = this.getSysmlTextualRepresentation(bounds.get(1), directEditInput);
            }
            label.append(LabelConstants.OPEN_BRACKET);
            if (firstBound != null) {
                label.append(firstBound);
            }
            if (secondBound != null) {
                label.append("..");
                label.append(secondBound);
            }
            label.append(LabelConstants.CLOSE_BRACKET + LabelConstants.SPACE);
        }
        if (element instanceof Feature feature) {
            if (feature.isIsOrdered()) {
                label.append(LabelConstants.ORDERED + LabelConstants.SPACE);
            }
            if (!feature.isIsUnique()) {
                label.append(LabelConstants.NON_UNIQUE + LabelConstants.SPACE);
            }
        }
        String labelAsString = label.toString().trim();
        if (!labelAsString.isEmpty()) {
            return LabelConstants.SPACE + labelAsString;
        } else {
            return labelAsString;
        }
    }

    /**
     * Return the label of the prefix part of the given {@link Usage}.
     *
     * @param usage
     *            the given {@link Usage}.
     * @return the label of the prefix part of the given {@link Usage} if there is one, an empty string otherwise.
     */
    public String getBasicNamePrefix(Element element) {
        StringBuilder label = new StringBuilder();
        if (element instanceof Usage usage) {
            if (usage.isIsVariation()) {
                label.append(LabelConstants.VARIATION + LabelConstants.SPACE);
            }
        } else if (element instanceof Definition definition) {
            if (definition.isIsVariation()) {
                label.append(LabelConstants.VARIATION + LabelConstants.SPACE);
            }
        }
        EObject membership = element.getOwningMembership();
        if (membership != null) {
            EObject parent = membership.eContainer();
            boolean hasVariationParent = (parent instanceof Definition && ((Definition) parent).isIsVariation()) | (parent instanceof Usage && ((Usage) parent).isIsVariation());
            if (membership instanceof VariantMembership | hasVariationParent) {
                label.append(LabelConstants.VARIANT);
            }
        }
        if (element instanceof Type type && type.isIsAbstract()) {
            label.append(LabelConstants.ABSTRACT + LabelConstants.SPACE);
        }
        if (element instanceof Usage usage) {
            this.getReferenceUsagePrefix(usage, label);
        }
        return label.toString();
    }

    /**
     * Return the label of the prefix part of the given {@link Usage}.
     *
     * @param usage
     *            the given {@link Usage}.
     * @return the label of the prefix part of the given {@link Usage} if there is one, an empty string otherwise.
     */
    public String getUsageListItemPrefix(Usage usage) {
        StringBuilder label = new StringBuilder();
        if (usage.getDirection() == FeatureDirectionKind.IN) {
            label.append(LabelConstants.IN + LabelConstants.SPACE);
        } else if (usage.getDirection() == FeatureDirectionKind.OUT) {
            label.append(LabelConstants.OUT + LabelConstants.SPACE);
        } else if (usage.getDirection() == FeatureDirectionKind.INOUT) {
            label.append(LabelConstants.INOUT + LabelConstants.SPACE);
        }
        if (usage.isIsAbstract() && !usage.isIsVariation()) {
            label.append(LabelConstants.ABSTRACT + LabelConstants.SPACE);
        }
        if (usage.isIsVariation()) {
            label.append(LabelConstants.VARIATION + LabelConstants.SPACE);
        }
        if (usage.isIsConstant()) {
            label.append(LabelConstants.CONSTANT + LabelConstants.SPACE);
        }
        if (usage.isIsDerived()) {
            label.append(LabelConstants.DERIVED + LabelConstants.SPACE);
        }
        if (usage.isIsEnd()) {
            label.append(LabelConstants.END + LabelConstants.SPACE);
        }
        this.getReferenceUsagePrefix(usage, label);
        return label.toString();
    }

    /**
     * Get the SysML textual representation of the given element.
     *
     * @param element
     *            the element to convert to textual format
     * @param resolvableName
     *            holds <code>true</code> to compute resolvable names for references, otherwise simple name are used to
     *            reference an element.
     * @return a textual representation or <code>null</code> if none
     */
    public String getSysmlTextualRepresentation(Element element, boolean resolvableName) {
        return this.buildSerializer(resolvableName).doSwitch(element);
    }

    /**
     * Return the label of the value part of the given {@link Usage}.
     *
     * @param usage
     *            the given {@link Usage}.
     * @return the label of the value part of the given {@link Usage} if there is one, an empty string otherwise.
     */
    public String getValueLabel(Usage usage) {
        return this.getValueStringRepresentation(usage, false);
    }

    /**
     * Return the label of the value part of the given {@link Usage}.
     *
     * @param usage
     *            the given {@link Usage}.
     * @param directEditInput
     *            if the label is being used as direct edit input
     * @return the label of the value part of the given {@link Usage} if there is one, an empty string otherwise.
     */
    public String getValueStringRepresentation(Usage usage, boolean directEditInput) {
        StringBuilder label = new StringBuilder();
        var featureValue = usage.getOwnedRelationship().stream()
                .filter(FeatureValue.class::isInstance)
                .map(FeatureValue.class::cast)
                .findFirst();
        if (featureValue.isPresent()) {
            var expression = featureValue.get().getValue();
            String valueAsString = null;
            if (expression != null) {
                valueAsString = this.getSysmlTextualRepresentation(expression, directEditInput);
            }

            if (featureValue.get().isIsDefault()) {
                label
                        .append(LabelConstants.SPACE)
                        .append(LabelConstants.DEFAULT);
            }
            label
                    .append(LabelConstants.SPACE)
                    .append(this.getFeatureValueRelationshipSymbol(featureValue.get()))
                    .append(LabelConstants.SPACE)
                    .append(valueAsString);
        }
        return label.toString();
    }

    /**
     * Return the label of the reference prefix part of the given {@link Usage}.
     *
     * @param usage
     *            the given {@link Usage}.
     * @return the label of the reference prefix part of the given {@link Usage} if there is one, an empty string
     *         otherwise.
     */
    private void getReferenceUsagePrefix(Usage usage, StringBuilder label) {
        if (usage.isIsReference() && !(usage instanceof AttributeUsage)) {
            // AttributeUsage are always referential, so no need to add the ref keyword
            label.append(LabelConstants.REF + LabelConstants.SPACE);
        }
    }

    /**
     * Returns the identification of the provided {@code element}.
     * <p>
     * The identification of an element is the concatenation of its <i>short name</i> and its <i>declared name</i>.
     * </p>
     *
     * @param element
     *            the element to get the identification from
     * @return the identification
     */
    protected String getIdentificationLabel(Element element) {
        StringBuilder label = new StringBuilder();
        label.append(this.getShortNameLabel(element));
        String declaredName = element.getDeclaredName();
        if (declaredName != null) {
            label.append(declaredName);
        }
        return label.toString();
    }

    /**
     * Returns the label for the short name of the given {@code element}.
     *
     * @param element
     *            the element to compute the short name label from
     * @return the label for the short name of the given {@code element}
     */
    protected String getShortNameLabel(Element element) {
        StringBuilder label = new StringBuilder();
        String declaredShortName = element.getDeclaredShortName();
        if (declaredShortName != null && !declaredShortName.isBlank()) {
            label.append(LabelConstants.LESSER_THAN)
                    .append(declaredShortName)
                    .append(LabelConstants.GREATER_THAN)
                    .append(LabelConstants.SPACE);
        }
        return label.toString();
    }

    /**
     * Return the label of the typing part of the given {@link Element}.
     *
     * @param usage
     *            the given {@link Element}.
     * @return the label of the typing part of the given {@link Element} if there is one, an empty string otherwise.
     */
    protected String getTypingLabel(Element element) {
        StringBuilder label = new StringBuilder();
        var optFeatureTyping = element.getOwnedRelationship().stream()
                .filter(FeatureTyping.class::isInstance)
                .map(FeatureTyping.class::cast)
                .findFirst();
        if (optFeatureTyping.isPresent()) {
            FeatureTyping featureTyping = optFeatureTyping.get();
            if (!featureTyping.isIsImplied()) {
                var type = featureTyping.getType();
                if (type != null) {
                    label.append(LabelConstants.SPACE);
                    label.append(LabelConstants.COLON);
                    label.append(LabelConstants.SPACE);
                    label.append(this.getDeclaredNameLabel(type));
                }
            }
        }
        return label.toString();
    }

    /**
     * Return the label of the subclassification of the given {@link Element}.
     *
     * @param usage
     *            the given {@link Element}.
     * @return the label of the subclassification of the given {@link Element} if there is one, an empty string
     *         otherwise.
     */
    protected String getSubclassificationLabel(Element element) {
        StringBuilder label = new StringBuilder();
        var optSubclassification = element.getOwnedRelationship().stream()
                .filter(Subclassification.class::isInstance)
                .map(Subclassification.class::cast)
                .findFirst();
        if (optSubclassification.isPresent()) {
            Subclassification subclassification = optSubclassification.get();
            if (!subclassification.isIsImplied()) {
                var superclassifier = subclassification.getSuperclassifier();
                if (superclassifier != null) {
                    label.append(LabelConstants.SPACE);
                    label.append(LabelConstants.SUBCLASSIFICATION);
                    label.append(LabelConstants.SPACE);
                    label.append(this.getDeclaredNameLabel(superclassifier));
                }
            }
        }
        return label.toString();
    }

    /**
     * Return the label of the subsetting of the given {@link Element}.
     *
     * @param usage
     *            the given {@link Element}.
     * @return the label of the subsetting of the given {@link Element} if there is one, an empty string otherwise.
     */
    protected String getSubsettingLabel(Element element) {
        StringBuilder label = new StringBuilder();
        var optSubsetting = element.getOwnedRelationship().stream()
                .filter(r -> r instanceof Subsetting && !(r instanceof Redefinition))
                .map(Subsetting.class::cast)
                .findFirst();
        if (optSubsetting.isPresent()) {
            Subsetting subsetting = optSubsetting.get();
            if (!subsetting.isIsImplied()) {
                var subsettedFeature = subsetting.getSubsettedFeature();
                if (subsettedFeature != null) {
                    label.append(LabelConstants.SPACE);
                    label.append(LabelConstants.SUBSETTING);
                    label.append(LabelConstants.SPACE);
                    label.append(this.getDeclaredNameLabel(subsettedFeature));
                }
            }
        }
        return label.toString();
    }

    /**
     * Return the label of the redefinition of the given {@link Element}.
     *
     * @param element
     *            the given {@link Element}.
     * @return the label of the redefinition of the given {@link Element} if there is one, an empty string otherwise.
     */
    protected String getRedefinitionLabel(Element element) {
        StringBuilder label = new StringBuilder();
        var optRedefinition = element.getOwnedRelationship().stream()
                .filter(Redefinition.class::isInstance)
                .map(Redefinition.class::cast)
                .findFirst();
        if (optRedefinition.isPresent()) {
            Redefinition redefinition = optRedefinition.get();
            if (!redefinition.isIsImplied()) {
                var redefinedFeature = redefinition.getRedefinedFeature();
                if (redefinedFeature != null) {
                    label.append(LabelConstants.SPACE);
                    label.append(LabelConstants.REDEFINITION);
                    label.append(LabelConstants.SPACE);
                    label.append(this.getDeclaredNameLabel(redefinedFeature));
                }
            }
        }
        return label.toString();
    }

    /**
     * Builds a SysMLSerializer.
     *
     * @param resolvableName
     *            holds <code>true</code> to compute resolvable names for references, otherwise simple name are used to
     *            reference an element.
     * @return a new {@link SysMLElementSerializer}.
     */
    protected SysMLElementSerializer buildSerializer(boolean resolvableName) {
        final INameDeresolver nameDeresolver;
        if (resolvableName) {
            nameDeresolver = new FileNameDeresolver();
        } else {
            nameDeresolver = new SimpleNameDeresolver();
        }
        return new SysMLElementSerializer("\n", " ", nameDeresolver, s -> {
            this.logger.info(s.message());
        });
    }

    private String getFeatureValueRelationshipSymbol(FeatureValue featureValueMembership) {
        final String affectationSymbole;

        if (featureValueMembership.isIsInitial()) {
            affectationSymbole = LabelConstants.COLON_EQUAL;
        } else {
            affectationSymbole = LabelConstants.EQUAL;
        }
        return affectationSymbole;
    }

    protected String getDeclaredNameLabel(Element element) {
        var label = element.getName();
        if (ElementUtil.isFromStandardLibrary(element)) {
            label = element.getQualifiedName();
        }
        return label;

    }
}
