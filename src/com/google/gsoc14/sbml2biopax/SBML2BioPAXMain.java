package com.google.gsoc14.sbml2biopax;

import com.google.gsoc14.sbml2biopax.converter.SBML2BioPAXConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.sbml.jsbml.*;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SBML2BioPAXMain {
    private static Log log = LogFactory.getLog(SBML2BioPAXMain.class);

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
        SBML2BioPAXConverter sbml2BioPAXConverter = new SBML2BioPAXConverter();
        Model bpModel = sbml2BioPAXConverter.convert(sbmlDocument);

        log.info("Saving BioPAX model to " + bpFile);
        SimpleIOHandler bpHandler = new SimpleIOHandler(BioPAXLevel.L3);
        bpHandler.convertToOWL(bpModel, new FileOutputStream(bpFile));
        log.info("Conversion completed.");
    }
}
