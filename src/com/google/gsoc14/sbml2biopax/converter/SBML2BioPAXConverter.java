package com.google.gsoc14.sbml2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.ModifierSpeciesReference;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.Species;

import java.util.HashSet;

public class SBML2BioPAXConverter {
    private static Log log = LogFactory.getLog(SBML2BioPAXConverter.class);
    private SBML2BioPAXUtilities sbml2BioPAXUtilities = new SBML2BioPAXUtilities();

    public Model convert(SBMLDocument sbmlDocument) {
        return convert(sbmlDocument.getModel());
    }

    private Model convert(org.sbml.jsbml.Model sbmlModel) {
        log.debug("First thing first: create a BioPAX model");
        Model bpModel = sbml2BioPAXUtilities.createModel();

        log.debug("Now, let's create a Pathway that corresponds to this SBML model.");
        Pathway pathway = sbml2BioPAXUtilities.convertPathway(bpModel, sbmlModel);

        log.debug("Next, let's iterate over reactions and convert them one by one.");
        for (Reaction reaction : sbmlModel.getListOfReactions()) {
            Conversion conversion = sbml2BioPAXUtilities.convertReaction(bpModel, pathway, reaction);
            for (ModifierSpeciesReference modifierSpeciesReference : reaction.getListOfModifiers()) {
                Control control = sbml2BioPAXUtilities.convertModifier(bpModel, pathway, modifierSpeciesReference);
                control.addControlled(conversion);
                Species species = sbmlModel.getSpecies(modifierSpeciesReference.getSpecies());
                Protein protein = sbml2BioPAXUtilities.convertModifierSpecies(bpModel, species);
                control.addController(protein);
            }
        }

        return bpModel;
    }
}
