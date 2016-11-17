package org.humanmetabolism;

import org.humanmetabolism.converter.SbmlToBiopaxConverter;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.sbml.jsbml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReconxToBiopaxConverter {
    private static Logger log = LoggerFactory.getLogger(ReconxToBiopaxConverter.class);

    public static void main(String[] args) throws IOException, XMLStreamException {
        if(args.length < 2) {
            System.err.println("Usage: SBML2BioPAX input.sbml output.owl");
            System.exit(-1);
        }

        String sbmlFile = args[0],
                bpFile = args[1];

        log.info("Reading SBML file: " + sbmlFile);
        SBMLDocument sbmlDocument = SBMLReader.read(new File(sbmlFile));
        log.info("SBML model loaded: " + sbmlDocument.getModel().getNumReactions() + " reactions in it.");

        log.info("Converting SBML model to BioPAX...");
        SbmlToBiopaxConverter sbmlToBiopaxConverter = new SbmlToBiopaxConverter();
        Model bpModel = sbmlToBiopaxConverter.convert(sbmlDocument);

        log.info("Saving BioPAX model to " + bpFile);
        SimpleIOHandler bpHandler = new SimpleIOHandler(BioPAXLevel.L3);
        bpHandler.convertToOWL(bpModel, new FileOutputStream(bpFile));
        log.info("Conversion completed.");
    }
}
