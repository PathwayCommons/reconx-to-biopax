package com.google.gsoc14.sbml2biopax;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SBML2BioPAX {
    private static Log log = LogFactory.getLog(SBML2BioPAX.class);

    public static void main(String[] args) throws IOException, XMLStreamException {
        if(args.length < 2) {
            System.err.println("Usage: SBML2BioPAX input.sbml output.owl");
            System.exit(-1);
        }

        String sbmlFile = args[0],
                bpFile = args[1];

        log.debug("Reading SBML file: " + sbmlFile);
        SBMLDocument sbmlDocument = SBMLReader.read(new File(sbmlFile));
        org.sbml.jsbml.Model sbmlModel = sbmlDocument.getModel();
        log.debug("SBML model loaded: " + sbmlModel.getNumReactions() + " reactions in it.");



        BioPAXFactory bpFactory = BioPAXLevel.L3.getDefaultFactory();
        Model bpModel = bpFactory.createModel();

        log.debug("Saving BioPAX model to " + bpFile);
        SimpleIOHandler bpHandler = new SimpleIOHandler(BioPAXLevel.L3);
        bpHandler.convertToOWL(bpModel, new FileOutputStream(bpFile));
        log.debug("Conversion completed.");
    }
}
