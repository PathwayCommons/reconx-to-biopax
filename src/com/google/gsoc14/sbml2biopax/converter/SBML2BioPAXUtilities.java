package com.google.gsoc14.sbml2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SBML2BioPAXUtilities {
    private static Log log = LogFactory.getLog(SBML2BioPAXUtilities.class);

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
        // This could change, would be great to make this configurable
        model.setXmlBase("http://www.humanmetabolism.org/#");
        return model;
    }

    public Conversion convertReaction(Model bpModel, Reaction reaction) {
        Class<? extends Conversion> rxnClass;
        // Extend this switch with further SBO terms as needed
        switch (reaction.getSBOTerm()) {
            case 185: // Transport reaction
                rxnClass = Transport.class;
                break;
            case 176: // Biochemical reaction
            default:
                rxnClass = BiochemicalReaction.class;
                break;
        }

        return createBPEfromSBMLE(bpModel, rxnClass, reaction);
    }

    public void setNames(AbstractNamedSBase namedSBase, Named named) {
        String name = namedSBase.getName();
        if(name == null) {
            name = "N/A";
        }
        named.setStandardName(name);
        named.setDisplayName(name);
        named.getName().add(name);
    }

    public Control convertModifier(Model bpModel, ModifierSpeciesReference modifierSpeciesReference) {
        // Interesting enough, these reference objects don't have an ID associated with them
        // That is why we are using hashcodes to generate unique BioPAX ID.
        String id = "control_" + modifierSpeciesReference.hashCode();
        return createBPEfromSBMLE(bpModel, Control.class, modifierSpeciesReference, id);
    }

    public <T extends SimplePhysicalEntity, S extends EntityReference> T convertSpeciesToSPE(Model bpModel, Class<T> entityClass, Class<S> refClass, Species species) {
        T entity = createBPEfromSBMLE(bpModel, entityClass, species);
        S reference = createBPEfromSBMLE(bpModel, refClass, species, "ref_" + species.getId());

        // Append xrefs to the entity reference if they are not there
        if(reference.getXref().isEmpty()) {
            for (Xref xref : generateXrefsForSpecies(bpModel, UnificationXref.class, species)) {
                reference.addXref(xref);
            }
        }

        entity.setEntityReference(reference);

        return entity;
    }

    private <T extends Xref> T resourceToXref(Class<T> xrefClass, String xrefId, String resource) {
        // Sample miriam resource: urn:miriam:chebi:CHEBI%3A15589
        String[] tokens = resource.split(":");
        T xref = bioPAXFactory.create(xrefClass, xrefId);
        xref.setDb(tokens[2].replace("\\.", " "));
        try {
            URI uri = new URI(tokens[3]);
            xref.setId(uri.getPath());
        } catch (URISyntaxException e) {
            xref.setId(tokens[3]);
        }
        return xref;
    }

    public <T extends Named> T createBPEfromSBMLE(Model bpModel, Class<T> aClass, AbstractNamedSBase abstractNamedSBase) {
        return createBPEfromSBMLE(bpModel, aClass, abstractNamedSBase, abstractNamedSBase.getId());
    }


    public <T extends Named> T createBPEfromSBMLE(Model bpModel, Class<T> aClass, AbstractNamedSBase abstractNamedSBase, String bpId) {
        T entity = (T) bpModel.getByID(bpId);
        if(entity == null) {
            entity = bioPAXFactory.create(aClass, bpId);
            setNames(abstractNamedSBase, entity);
            bpModel.add(entity);
        }

        return entity;
    }

    public PhysicalEntity convertSpecies(Model bpModel, Species species) {
        PhysicalEntity physicalEntity;

        switch (species.getSBOTerm()) {
            case 297: // Complex
                physicalEntity = createComplexFromSpecies(bpModel, species);
                break;
            case 247: // Simple chemical
                physicalEntity = convertSpeciesToSPE(bpModel, SmallMolecule.class, SmallMoleculeReference.class, species);
                break;
            case 252: // Polypeptide chain ~ Protein
            default:
                physicalEntity = convertSpeciesToSPE(bpModel, Protein.class, ProteinReference.class, species);
                break;
        }

        return physicalEntity;
    }

    private Complex createComplexFromSpecies(Model bpModel, Species species) {
        Complex complex = createBPEfromSBMLE(bpModel, Complex.class, species);
        for (Xref xref : generateXrefsForSpecies(bpModel, RelationshipXref.class, species)) {
            complex.addXref(xref);
        }

        return complex;
    }

    private <T extends Xref> Set<Xref> generateXrefsForSpecies(Model bpModel, Class<T> xrefClass, Species species) {
        Annotation annotation = species.getAnnotation();
        HashSet<Xref> xrefs = new HashSet<Xref>();

        List<CVTerm> listOfCVTerms = annotation.getListOfCVTerms();
        log.trace(
                "- - Found " + listOfCVTerms.size() + " annotations for species: " + species.getName() + ". " +
                        "Converting them to UnificationXrefs."
        );
        for (CVTerm cvTerm : listOfCVTerms) {
            for (String resource : cvTerm.getResources()) {
                String xrefId = "xref_" + cvTerm.hashCode();
                // Let's not replicate xrefs if possible
                Xref xref = (Xref) bpModel.getByID(xrefId);
                if(xref == null) {
                    xref = resourceToXref(xrefClass, xrefId, resource);
                    bpModel.add(xref);
                }
                xrefs.add(xref);
            }
        }

        return xrefs;

    }

    public void fillComplexes(Model bpModel, org.sbml.jsbml.Model sbmlModel) {
        // This is redundant operation, but should be pretty fast as look-ups prevent duplication
        // and it will make sure we have all the species converted to BPEs
        for (Species species : sbmlModel.getListOfSpecies()) {
            convertSpecies(bpModel, species);
        }

        HashMap<String, Protein> xrefToProtein = new HashMap<String, Protein>();
        // Now let's use xrefs to find the complex components
        // First, find the proteins and map them with their xrefs
        for (Protein protein : bpModel.getObjects(Protein.class)) {
            for (Xref xref : protein.getEntityReference().getXref()) {
                xrefToProtein.put(xref.toString(), protein);
            }
        }

        // Now let's go to the complexes and see what xrefs they have
        int numOfComplexesFixed = 0;
        Set<Complex> complexes = bpModel.getObjects(Complex.class);
        for (Complex complex : complexes) {
            HashSet<Protein> components = new HashSet<Protein>();
            for (Xref xref : complex.getXref()) {
                Protein protein = xrefToProtein.get(xref.toString());
                if(protein != null) {
                    components.add(protein);
                }
            }

            // Let's keep track of these for debugging purposes
            if(!components.isEmpty()) {
                numOfComplexesFixed++;
            }

            for (Protein component : components) {
                log.trace("Adding " + component.getDisplayName() + " as a component to " + complex.getDisplayName());
                complex.addComponent(component);
            }
        }

        log.debug("We have fixed " + numOfComplexesFixed + " out of " + complexes.size() + " complexes.");
    }

    public void assignOrganism(Model bpModel) {
        // Since this is RECON2, everything is human
        BioSource bioSource = bioPAXFactory.create(BioSource.class, "http://identifiers.org/taxonomy/9606");
        bioSource.setDisplayName("Homo sapiens");
        bioSource.setStandardName("Homo sapiens");
        UnificationXref unificationXref = bioPAXFactory.create(UnificationXref.class, "urn:miriam:taxonomy:9606");
        unificationXref.setDb("taxonomy");
        unificationXref.setId("9606");
        bioSource.addXref(unificationXref);
        bpModel.add(bioSource);

        for (ProteinReference proteinReference : bpModel.getObjects(ProteinReference.class)) {
            proteinReference.setOrganism(bioSource);
        }
    }
}
