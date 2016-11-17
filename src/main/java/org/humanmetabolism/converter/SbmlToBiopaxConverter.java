package org.humanmetabolism.converter;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SbmlToBiopaxConverter {
    private static Logger log = LoggerFactory.getLogger(SbmlToBiopaxConverter.class);
    private SbmlToBiopaxUtils sbmlToBiopaxUtils = new SbmlToBiopaxUtils();

    public Model convert(SBMLDocument sbmlDocument) {
        return convert(sbmlDocument.getModel());
    }

    private Model convert(org.sbml.jsbml.Model sbmlModel) {
        log.debug("First thing first: create a BioPAX model");
        Model bpModel = sbmlToBiopaxUtils.createModel();

        log.debug("Now, let's create a Pathway that corresponds to this SBML model.");
        Pathway pathway = sbmlToBiopaxUtils.convertPathway(bpModel, sbmlModel);

        // Reactions -> Conversions [start]
        ListOf<Reaction> sbmlReactions = sbmlModel.getListOfReactions();
        log.debug("There are " + sbmlReactions.size() + " reactions in the SBML model. ");
        log.debug("Let's iterate over reactions and convert them one by one.");
        for (Reaction reaction : sbmlReactions) {
            log.trace("Working on reaction conversion: " + reaction.getName());
            Conversion conversion = sbmlToBiopaxUtils.convertReaction(bpModel, reaction);
            pathway.addPathwayComponent(conversion);

            // Modifiers -> Control reactions [start]
            ListOf<ModifierSpeciesReference> listOfModifiers = reaction.getListOfModifiers();
            log.trace(
                    "- There are " + listOfModifiers.size() + " modifiers to this reaction. " +
                    "Converting them to controls to this reaction."
            );

            for (ModifierSpeciesReference modifierSpeciesReference : listOfModifiers) {
                Control control = sbmlToBiopaxUtils.convertModifier(bpModel, modifierSpeciesReference);
                pathway.addPathwayComponent(control);
                control.addControlled(conversion);
                Species species = sbmlModel.getSpecies(modifierSpeciesReference.getSpecies());
                Controller controller = sbmlToBiopaxUtils.convertSpecies(bpModel, species);
                control.addController(controller);
            }
            // Modifiers -> Controls [end]

            // Reactants -> Left Participants [start]
            ListOf<SpeciesReference> listOfReactants = reaction.getListOfReactants();
            log.trace("- There are " + listOfReactants.size() + " reactants to this reaction. " +
                    "Adding them to the reaction as left participants.");
            for (SpeciesReference reactantRef : listOfReactants) {
                Species species = sbmlModel.getSpecies(reactantRef.getSpecies());
                PhysicalEntity physicalEntity = sbmlToBiopaxUtils.convertSpecies(bpModel, species);
                conversion.addLeft(physicalEntity);
            }
            // Reactants -> Left Participants [end]

            // Products -> Right Participants [start]
            ListOf<SpeciesReference> listOfProducts = reaction.getListOfProducts();
            log.trace("- There are " + listOfProducts.size() + " products to this reaction. " +
                    "Adding them to the reaction as right participants.");
            for (SpeciesReference productRef : listOfProducts) {
                Species species = sbmlModel.getSpecies(productRef.getSpecies());
                PhysicalEntity physicalEntity = sbmlToBiopaxUtils.convertSpecies(bpModel, species);
                conversion.addRight(physicalEntity);
            }
            // Products -> Right Participants [end]
        }
        // Reactions -> Conversions [end]

        // The process above leaves some of the complexes empty. We need to fix this.
        sbmlToBiopaxUtils.fillComplexes(bpModel, sbmlModel);

        // Let's assign organism to every possible entity
        sbmlToBiopaxUtils.assignOrganism(bpModel);

        // Some references do not have relationship entities in them
        // Let's assign biomodels model id for them
        sbmlToBiopaxUtils.assignRelationXrefs(bpModel);


        return bpModel;
    }

}
