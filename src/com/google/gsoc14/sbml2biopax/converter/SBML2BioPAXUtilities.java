package com.google.gsoc14.sbml2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.*;

import javax.xml.stream.XMLStreamException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
        Pathway pathway = createBPEfromSBMLE(bpModel, Pathway.class, sbmlModel);
        for (Xref xref : generateXrefsForSBase(bpModel, RelationshipXref.class, sbmlModel)) {
            pathway.addXref(xref);
        }
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

        Conversion conversion = createBPEfromSBMLE(bpModel, rxnClass, reaction);
        conversion.setConversionDirection(
                reaction.getReversible()
                        ? ConversionDirectionType.REVERSIBLE
                        : ConversionDirectionType.LEFT_TO_RIGHT
        );

        for (Xref xref : generateXrefsForSBase(bpModel, RelationshipXref.class, reaction)) {
            conversion.addXref(xref);
        }

        return conversion;
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
        Control control = createBPEfromSBMLE(bpModel, Control.class, modifierSpeciesReference, id);
        control.setControlType(ControlType.ACTIVATION);
        return control;
    }

    public <T extends SimplePhysicalEntity, S extends EntityReference> T convertSpeciesToSPE(Model bpModel, Class<T> entityClass, Class<S> refClass, Species species) {
        Set<Xref> xrefs = generateXrefsForSBase(bpModel, UnificationXref.class, species);
        HashSet<XReferrable> ers = new HashSet<XReferrable>();
        for (Xref xref : xrefs) {
            for (XReferrable xReferrable : xref.getXrefOf()) {
                // Only add the entity references
                if(xReferrable instanceof EntityReference) {
                    ers.add(xReferrable);
                }
            }
        }

        S reference;
        if(ers.isEmpty()) {
            reference = createBPEfromSBMLE(bpModel, refClass, species, "ref_" + species.getId());
            for (Xref xref : xrefs) {
                reference.addXref(xref);
            }
        } else if(ers.size() == 1) { // There shouldn't be more than one
            reference = (S) ers.iterator().next();
        } else {
            log.warn(
                    "There are more than one EntityReferences that match with the same unification xref for species: "
                            + species.getName()
                            + ". Picking the first one: "
                            + ers.iterator().next().getRDFId()
            );

            reference = (S) ers.iterator().next();
        }

        T entity = createBPEfromSBMLE(bpModel, entityClass, species);
        entity.setEntityReference(reference);

        return entity;
    }

    private <T extends Xref> T resourceToXref(Class<T> xrefClass, String xrefId, String resource) {
        // Sample miriam resource: urn:miriam:chebi:CHEBI%3A15589
        String[] tokens = resource.split(":");
        T xref = bioPAXFactory.create(xrefClass, xrefId);
        xref.setDb(tokens[2].replace(".", " "));
        String idToken = tokens[3];
        try {
            URI uri = new URI(idToken);
            xref.setId(uri.getPath());
        } catch (URISyntaxException e) {
            log.warn("Problem parsing the URI,  " + idToken + ":" + e.getLocalizedMessage());
            xref.setId(idToken);
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
            setComments(abstractNamedSBase, entity);
            bpModel.add(entity);
        }

        return entity;
    }

    private <T extends Named> void setComments(AbstractNamedSBase abstractNamedSBase, T entity) {
        try {
            // Strip out html tags
            entity.addComment(abstractNamedSBase.getNotesString().replaceAll("\\<[^>]*>",""));
        } catch (XMLStreamException e) {
            log.warn("Problem parsing the notes XML: " + e.getLocalizedMessage());
        }
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

        CellularLocationVocabulary cellularLocationVocabulary = createCompartment(bpModel, species.getCompartmentInstance());
        physicalEntity.setCellularLocation(cellularLocationVocabulary);

        return physicalEntity;
    }

    public CellularLocationVocabulary createCompartment(Model bpModel, Compartment compartment) {
        String id = compartment.getId();
        CellularLocationVocabulary cellularLocationVocabulary = (CellularLocationVocabulary) bpModel.getByID(id);
        if(cellularLocationVocabulary == null) {
            cellularLocationVocabulary = bioPAXFactory.create(CellularLocationVocabulary.class, id);
            cellularLocationVocabulary.addTerm(compartment.getName());
            for (Xref xref : generateXrefsForSBase(bpModel, UnificationXref.class, compartment)) {
                cellularLocationVocabulary.addXref(xref);
            }
            bpModel.add(cellularLocationVocabulary);
        }

        return cellularLocationVocabulary;
    }

    private Complex createComplexFromSpecies(Model bpModel, Species species) {
        Complex complex = createBPEfromSBMLE(bpModel, Complex.class, species);
        for (Xref xref : generateXrefsForSBase(bpModel, RelationshipXref.class, species)) {
            complex.addXref(xref);
        }

        return complex;
    }

    private <T extends Xref> Set<Xref> generateXrefsForSBase(Model bpModel, Class<T> xrefClass, AbstractSBase sBase) {
        Annotation annotation = sBase.getAnnotation();
        HashSet<Xref> xrefs = new HashSet<Xref>();

        for (CVTerm cvTerm : annotation.getListOfCVTerms()) {
            for (String resource : cvTerm.getResources()) {
                String xrefId = "xref_" + cvTerm.hashCode();
                // Let's not replicate xrefs if possible
                Xref xref = (Xref) bpModel.getByID(xrefId);
                if(xref == null) {
                    if(resource.toLowerCase().contains("pubmed")) {
                        xref = resourceToXref(PublicationXref.class, xrefId, resource);
                    } else {
                        xref = resourceToXref(xrefClass, xrefId, resource);
                    }
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
        HashSet<Protein> newProteins = new HashSet<Protein>();
        // Now let's use xrefs to find the complex components
        // First, find the proteins and map them with their xrefs
        for (ProteinReference proteinRef : bpModel.getObjects(ProteinReference.class)) {
            for (Xref xref : proteinRef.getXref()) {
                Protein protein = bioPAXFactory.create(Protein.class, "complex_" + proteinRef.getRDFId());
                protein.setDisplayName(proteinRef.getDisplayName());
                protein.setStandardName(proteinRef.getStandardName());
                xrefToProtein.put(xref.toString(), protein);
                protein.setEntityReference(proteinRef);
                newProteins.add(protein);
            }
        }
        // Add all these new proteins to the model
        for (Protein newProtein : newProteins) {
            bpModel.add(newProtein);
        }

        // We have to work with a map not to create concurrency problems
        // This will hold all new entities
        HashMap<String, BioPAXElement> newBPEs = new HashMap<String, BioPAXElement>();

        // Now let's go to the complexes and see what xrefs they have
        Set<Complex> complexes = bpModel.getObjects(Complex.class);

        for (Complex complex : complexes) {
            HashSet<String> names = new HashSet<String>(Arrays.asList(complex.getDisplayName().split(":")));

            // Let's try to capture proteins from the model first
            HashSet<Protein> components = new HashSet<Protein>();
            for (Xref xref : complex.getXref()) {
                Protein protein = xrefToProtein.get(xref.toString());
                if(protein != null) {
                    components.add(protein);
                    names.remove(protein.getDisplayName());
                }
            }

            // These are the ones we were not able to capture from the model
            // Let's create proteins for them
            for (String name : names) {
                String nameBasedURI = "protein_" + name;
                Protein protein = bioPAXFactory.create(Protein.class, nameBasedURI);
                protein.setDisplayName(name);
                protein.setStandardName(name);
                newBPEs.put(nameBasedURI, protein);

                String refId = "ref_" + nameBasedURI;
                ProteinReference proteinReference = (ProteinReference) newBPEs.get(refId);
                if(proteinReference == null) {
                    proteinReference = bioPAXFactory.create(ProteinReference.class, refId);
                    proteinReference.setDisplayName(name);
                    proteinReference.setStandardName(name);
                    newBPEs.put(refId, proteinReference);
                }

                String xrefId = "symbol_" + name;
                UnificationXref unificationXref = (UnificationXref) newBPEs.get(xrefId);
                if(unificationXref == null) {
                    unificationXref = bioPAXFactory.create(UnificationXref.class, xrefId);
                    unificationXref.setDb("HUGO Symbol");
                    unificationXref.setId(name);
                    newBPEs.put(xrefId, unificationXref);
                }

                proteinReference.addXref(unificationXref);
                protein.setEntityReference(proteinReference);
                components.add(protein);
            }

            // Now, add all these proteins as components
            for (Protein component : components) {
                complex.addComponent(component);
            }
        }

        // Finally add all these new BPEs to the model
        for (BioPAXElement bioPAXElement : newBPEs.values()) {
            bpModel.add(bioPAXElement);
        }

        log.trace("Fixed " + complexes.size() + " complexes in the model.");

    }

    public void assignOrganism(Model bpModel) {
        // Since this is RECON2, everything is human
        BioSource bioSource = bioPAXFactory.create(BioSource.class, "source_human");
        bioSource.setDisplayName("Homo sapiens");
        bioSource.setStandardName("Homo sapiens");
        UnificationXref unificationXref = bioPAXFactory.create(UnificationXref.class, "xref_human_tax");
        unificationXref.setDb("taxonomy");
        unificationXref.setId("9606");
        bpModel.add(unificationXref);
        bioSource.addXref(unificationXref);
        bpModel.add(bioSource);

        for (ProteinReference proteinReference : bpModel.getObjects(ProteinReference.class)) {
            proteinReference.setOrganism(bioSource);
        }
    }

    public void assignRelationXrefs(Model bpModel) {
        RelationshipXref xref = bioPAXFactory.create(RelationshipXref.class, "relxref_biomodels");
        xref.setDb("BioModels");
        xref.setId("MODEL1109130000");
        bpModel.add(xref);

        for (EntityReference entityReference : bpModel.getObjects(EntityReference.class)) {
            if(entityReference.getXref().isEmpty()) {
                entityReference.addXref(xref);
            }
        }
    }

}
