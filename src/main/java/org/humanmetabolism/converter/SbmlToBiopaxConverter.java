package org.humanmetabolism.converter;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SbmlToBiopaxConverter {
    private static Logger log = LoggerFactory.getLogger(SbmlToBiopaxConverter.class);
    private final SbmlToBiopaxUtils sbmlToBiopaxUtils = new SbmlToBiopaxUtils();
    private boolean makePathway = false;

    public void biopaxModelXmlBase(String xmlBase) {
        sbmlToBiopaxUtils.setXmlBase(xmlBase);
    }

    /**
     * Whether to generate the model's root pathway that contains all the interactions.
     *
     * @return true/false
     */
    public boolean isMakePathway() {
        return makePathway;
    }
    public void setMakePathway(boolean makePathway) {
        this.makePathway = makePathway;
    }

    public Model convert(InputStream sbmlInputStream) throws XMLStreamException {
        return convert(SBMLReader.read(sbmlInputStream));
    }

    public Model convert(File sbmlFile) throws XMLStreamException, IOException {
        return convert(SBMLReader.read(sbmlFile));
    }

    public Model convert(SBMLDocument sbmlDocument) {
        return convert(sbmlDocument.getModel());
    }

    private Model convert(org.sbml.jsbml.Model sbmlModel) {
        Model bpModel = sbmlToBiopaxUtils.createModel();

        final Provenance provenance = sbmlToBiopaxUtils.convertProvenance(bpModel, sbmlModel);

        // create a Pathway that corresponds to this SBML model (optional)
        Pathway pathway = null;
        if(makePathway)
            pathway = sbmlToBiopaxUtils.convertPathway(bpModel, sbmlModel);

        // Reactions -> Conversions [start]
        ListOf<Reaction> sbmlReactions = sbmlModel.getListOfReactions();
        log.info("There are " + sbmlReactions.size() + " reactions in the SBML model. ");
        // iterate over reactions and convert them one by one.
        for (Reaction reaction : sbmlReactions) {
            log.trace("Working on reaction conversion: " + reaction.getName());
            Conversion conversion = sbmlToBiopaxUtils.convertReaction(bpModel, reaction);
            if(makePathway) pathway.addPathwayComponent(conversion);

            // Modifiers -> Control reactions [start]
            ListOf<ModifierSpeciesReference> listOfModifiers = reaction.getListOfModifiers();
            log.debug("- There are " + listOfModifiers.size() + " modifiers to this reaction. " +
                    "Converting them to controls to this reaction.");

            for (ModifierSpeciesReference modifierSpeciesReference : listOfModifiers) {
                Control control = sbmlToBiopaxUtils.convertModifier(bpModel, modifierSpeciesReference);
                if(makePathway) pathway.addPathwayComponent(control);
                control.addControlled(conversion);
                Species species = sbmlModel.getSpecies(modifierSpeciesReference.getSpecies());
                Controller controller = sbmlToBiopaxUtils.convertSpecies(bpModel, species);
                control.addController(controller);
            }
            // Modifiers -> Controls [end]

            // Reactants -> Left Participants [start]
            ListOf<SpeciesReference> listOfReactants = reaction.getListOfReactants();
            log.debug("- There are " + listOfReactants.size() + " reactants to this reaction. " +
                    "Adding them to the reaction as left participants.");
            for (SpeciesReference reactantRef : listOfReactants) {
                Species species = sbmlModel.getSpecies(reactantRef.getSpecies());
                PhysicalEntity physicalEntity = sbmlToBiopaxUtils.convertSpecies(bpModel, species);
                conversion.addLeft(physicalEntity);
            }
            // Reactants -> Left Participants [end]

            // Products -> Right Participants [start]
            ListOf<SpeciesReference> listOfProducts = reaction.getListOfProducts();
            log.debug("- There are " + listOfProducts.size() + " products to this reaction. " +
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
        sbmlToBiopaxUtils.fillComplexes(bpModel);

        // Let's assign organism to where applicable
        sbmlToBiopaxUtils.assignOrganism(bpModel);

        // assign the Provenance to all the Entities in the model
        for(Entity e : bpModel.getObjects(Entity.class)) {
            e.addDataSource(provenance);
        }

        return bpModel;
    }

}
