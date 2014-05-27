package com.google.gsoc14.sbml2biopax.converter;

import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.AbstractNamedSBase;
import org.sbml.jsbml.ModifierSpeciesReference;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.Species;

public class SBML2BioPAXUtilities {
    private BioPAXFactory bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();

    public BioPAXFactory getBioPAXFactory() {
        return bioPAXFactory;
    }

    public void setBioPAXFactory(BioPAXFactory bioPAXFactory) {
        this.bioPAXFactory = bioPAXFactory;
    }

    public Pathway convertPathway(Model bpModel, org.sbml.jsbml.Model sbmlModel) {
        Pathway pathway = bioPAXFactory.create(Pathway.class, sbmlModel.getId());
        setNames(sbmlModel, pathway);
        bpModel.add(pathway);

        return pathway;
    }

    public Model createModel() {
        Model model = bioPAXFactory.createModel();
        model.setXmlBase("http://www.humanmetabolism.org/#");
        return model;
    }

    public Conversion convertReaction(Model bpModel, Pathway pathway, Reaction reaction) {
        BiochemicalReaction biochemicalReaction = bioPAXFactory.create(BiochemicalReaction.class, reaction.getId());
        setNames(reaction, biochemicalReaction);
        bpModel.add(biochemicalReaction);
        pathway.addPathwayComponent(biochemicalReaction);
        return biochemicalReaction;
    }

    public void setNames(AbstractNamedSBase namedSBase, Named named) {
        String name = namedSBase.getName();
        named.setStandardName(name);
        named.setDisplayName(name);
        named.getName().add(name);
    }

    public Control convertModifier(Model bpModel, Pathway pathway, ModifierSpeciesReference modifierSpeciesReference) {
        String id = modifierSpeciesReference.hashCode() + "";
        Control control = (Control) bpModel.getByID(id);
        if(control == null) {
            control = bioPAXFactory.create(Control.class, id);
            bpModel.add(control);
        }
        pathway.addPathwayComponent(control);
        return control;
    }

    public Protein convertModifierSpecies(Model bpModel, Species species) {
        String id = species.getId();
        Protein protein = (Protein) bpModel.getByID(id);
        if(protein == null) {
            protein = bioPAXFactory.create(Protein.class, species.getId());
            setNames(species, protein);
            bpModel.add(protein);
        }
        return protein;
    }
}
