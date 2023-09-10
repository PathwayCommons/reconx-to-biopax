package org.humanmetabolism;

import org.humanmetabolism.converter.SbmlToBiopaxConverter;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReconxToBiopax {
    private static Logger log = LoggerFactory.getLogger(ReconxToBiopax.class);

    public static void main(String[] args) throws IOException, XMLStreamException {
        boolean makePathway = false; //default - won't create top model all-interactions pathway

        if(args.length < 2) {
            System.err.println("Usage: ReconxToBiopax input.sbml output.owl [--pathway]\n" +
                    "Optional parameters:\n" +
                    "--pathway\tcreate a root model Pathway that simply contains all the interactions");
            System.exit(-1);
        }

        String sbmlFile = args[0];
        String bpFile = args[1];
        if(args.length > 2 && args[2].equals("--pathway")) {
            makePathway = true;
        }

        log.info("Converting SBML model to BioPAX...");
        SbmlToBiopaxConverter sbmlToBiopaxConverter = new SbmlToBiopaxConverter();
        sbmlToBiopaxConverter.setMakePathway(makePathway);
        Model bpModel = sbmlToBiopaxConverter.convert(new File(sbmlFile));

        log.info("Saving BioPAX model to " + bpFile);
        SimpleIOHandler bpHandler = new SimpleIOHandler(BioPAXLevel.L3);
        bpHandler.convertToOWL(bpModel, new FileOutputStream(bpFile));
        log.info("Conversion completed.");
    }
}
