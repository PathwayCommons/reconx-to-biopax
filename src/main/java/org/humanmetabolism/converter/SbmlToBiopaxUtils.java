package org.humanmetabolism.converter;

import org.apache.commons.lang3.StringUtils;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.sbml.jsbml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class SbmlToBiopaxUtils {
    private static Logger log = LoggerFactory.getLogger(SbmlToBiopaxUtils.class);

    private BioPAXFactory bioPAXFactory;
    private String xmlBase;

    public SbmlToBiopaxUtils() {
        this.xmlBase = "";
        this.bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();
    }

    public String getXmlBase() {
        return xmlBase;
    }

    public void setXmlBase(String xmlBase) {
        this.xmlBase = (StringUtils.isBlank(xmlBase)) ? "" : xmlBase;
    }

    public String completeId(String partialId) {
        return getXmlBase() + partialId;
    }

    public Pathway convertPathway(Model bpModel, org.sbml.jsbml.Model sbmlModel) {
        Pathway pathway = createBPEfromSBMLE(bpModel, Pathway.class, sbmlModel);
        for (Xref xref : generateXrefsForSBase(bpModel, RelationshipXref.class, sbmlModel)) {
            pathway.addXref(xref);
        }
        return pathway;
    }

    public Provenance convertProvenance(Model bpModel, org.sbml.jsbml.Model sbmlModel) {
        Provenance p = createBPEfromSBMLE(bpModel, Provenance.class, sbmlModel,
                "bioregistry.io/biomodels.db:" + sbmlModel.getId());
        for (Xref xref : generateXrefsForSBase(bpModel, RelationshipXref.class, sbmlModel)) {
            p.addXref(xref);
        }
        final UnificationXref x = bpModel.addNew(UnificationXref.class,"biomodels.db:" + sbmlModel.getId());
        x.setDb("biomodels.db");
        x.setId(sbmlModel.getId());
        p.addXref(x);

        return p;
    }

    public Model createModel() {
        Model model = bioPAXFactory.createModel();
        // This could change, would be great to make this configurable
        model.setXmlBase(getXmlBase());
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
        if(name == null || name.toLowerCase().equals("null")) {
            name = "N/A";
        }
        named.setStandardName(name);
        named.setDisplayName(name);
        named.getName().add(name);
    }

    public Control convertModifier(Model bpModel, ModifierSpeciesReference modifierSpeciesReference) {
        // Interesting enough, these reference objects don't have an ID associated with them
        // That is why we are using hashcodes to generate unique BioPAX ID.
        String id = completeId("control_" + modifierSpeciesReference.hashCode());
        Control control = createBPEfromSBMLE(bpModel, Control.class, modifierSpeciesReference, id);
        control.setControlType(ControlType.ACTIVATION);
        return control;
    }

    public <T extends SimplePhysicalEntity, S extends EntityReference> T convertSpeciesToSPE(
        Model bpModel, Class<T> entityClass, Class<S> refClass, Species species)
    {
        Set<Xref> xrefs = generateXrefsForSBase(bpModel, UnificationXref.class, species);
        HashSet<XReferrable> ers = new HashSet<>();
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
            reference = createBPEfromSBMLE(bpModel, refClass, species, completeId("ref_" + species.getId()));
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
                            + ers.iterator().next().getUri()
            );

            reference = (S) ers.iterator().next();
        }

        T entity = createBPEfromSBMLE(bpModel, entityClass, species);
        entity.setEntityReference(reference);

        // Clean-up non-used xrefs
        for (Xref xref : xrefs) {
            if(xref.getXrefOf().isEmpty()) {
                bpModel.remove(xref);
            }
        }

        return entity;
    }

    private <T extends Xref> T resourceToXref(Class<T> xrefClass, String xrefId, String resource) {
        // Sample miriam resource: urn:miriam:chebi:CHEBI%3A15589
        String[] tokens = resource.split(":");
        T xref = bioPAXFactory.create(xrefClass, xrefId);
        // biomodels.db -> biomodels; ec-code -> ec code
        String dataBase = tokens[2]
                .replace(".", " ")
                .replace("-", " ")
                .replace(" db", " database")
                .replace("obo ", "")
        ;
        xref.setDb(dataBase);
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
        return createBPEfromSBMLE(bpModel, aClass, abstractNamedSBase, completeId(abstractNamedSBase.getId()));
    }


    public <T extends Named> T createBPEfromSBMLE(Model bpModel, Class<T> aClass, AbstractNamedSBase abstractNamedSBase, String uri) {
        T entity = (T) bpModel.getByID(uri);
        if(entity == null) {
            entity = bpModel.addNew(aClass, uri);
            setNames(abstractNamedSBase, entity);
            setComments(abstractNamedSBase, entity);
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
        String id = completeId(compartment.getId());
        CellularLocationVocabulary cellularLocationVocabulary = (CellularLocationVocabulary) bpModel.getByID(id);
        if(cellularLocationVocabulary == null) {
            cellularLocationVocabulary = bpModel.addNew(CellularLocationVocabulary.class, id);
            cellularLocationVocabulary.addTerm(compartment.getName());
            for (Xref xref : generateXrefsForSBase(bpModel, UnificationXref.class, compartment)) {
                cellularLocationVocabulary.addXref(xref);
            }
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
                String xrefId = completeId(xrefClass.getSimpleName().toLowerCase() + "_" + cvTerm.hashCode());
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

    public void fillComplexes(Model bpModel) {
        HashMap<String, ProteinReference> xrefToProtein = new HashMap<>();
        // Now let's use xrefs to find the complex components
        // First, find the protein refs and map them with their xrefs
        for (ProteinReference proteinRef : bpModel.getObjects(ProteinReference.class)) {
            for (Xref xref : proteinRef.getXref()) {
                xrefToProtein.put(xref.toString(), proteinRef);
            }
        }

        // Now let's go to the complexes and see what xrefs they have
        Set<Complex> complexes = new HashSet<>(bpModel.getObjects(Complex.class));
        for (Complex complex : complexes) {
            HashSet<String> names = new HashSet<>(Arrays.asList(complex.getDisplayName().split(":")));

            // Let's try to capture proteins from the model first
            HashSet<Protein> components = new HashSet<>();
            for (Xref xref : complex.getXref()) {
                ProteinReference proteinRef = xrefToProtein.get(xref.toString());
                if(proteinRef != null) {
                    String cProteinId = completeId(complex.getUri() + "_" + proteinRef.getUri());
                    if(!bpModel.containsID(cProteinId)) {
                        Protein protein = bpModel.addNew(Protein.class, cProteinId);
                        protein.setDisplayName(proteinRef.getDisplayName());
                        protein.setStandardName(proteinRef.getStandardName());
                        protein.setEntityReference(proteinRef);
                        components.add(protein);
                        names.remove(protein.getDisplayName());
                    }
                }
            }

            // These are the ones we were not able to capture from the model
            // Let's create proteins for them
            for (String name : names) {
                final String nameBasedURI = completeId("protein_" + name);
                Protein protein = (Protein) bpModel.getByID(nameBasedURI);
                if(protein == null) {
                    protein = bpModel.addNew(Protein.class, nameBasedURI);
                    protein.setDisplayName(name);
                    protein.setStandardName(name);
                }

                String refId = completeId("ref_" + nameBasedURI);
                ProteinReference proteinReference = (ProteinReference) bpModel.getByID(refId);
                if(proteinReference == null) {
                    proteinReference = bpModel.addNew(ProteinReference.class, refId);
                    proteinReference.setDisplayName(name);
                    proteinReference.setStandardName(name);
                }

                String u = completeId("symbol_" + name);
                RelationshipXref rx = (RelationshipXref) bpModel.getByID(u);
                if(rx == null) {
                    rx = bpModel.addNew(RelationshipXref.class, u);
                    rx.setDb("hgnc.symbol");
                    rx.setId(name);
                }

                proteinReference.addXref(rx);
                protein.setEntityReference(proteinReference);
                components.add(protein);
            }

            // Now, add all these proteins as components
            for (Protein component : components) {
                complex.addComponent(component);
                component.setCellularLocation(complex.getCellularLocation());
            }
        }

        log.debug("Fixed " + complexes.size() + " complexes in the model.");
    }

    public void assignOrganism(Model bpModel) {
        // Since this is RECON2, everything is human
        BioSource bioSource = bpModel.addNew(BioSource.class, "bioregistry.io/ncbitaxon:9606");
        bioSource.setDisplayName("Homo sapiens");
        bioSource.setStandardName("Homo sapiens");
        UnificationXref unificationXref = bpModel.addNew(UnificationXref.class, "ncbitaxon:9606");
        unificationXref.setDb("ncbitaxon");
        unificationXref.setId("9606");
        bioSource.addXref(unificationXref);

        for (SequenceEntityReference ser : bpModel.getObjects(SequenceEntityReference.class)) {
            ser.setOrganism(bioSource);
        }
        for (Gene g : bpModel.getObjects(Gene.class)) { //but, there are probably no Gene objects...
            g.setOrganism(bioSource);
        }
        for (Pathway p : bpModel.getObjects(Pathway.class)) {
            p.setOrganism(bioSource);
        }
    }

}
