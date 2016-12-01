# reconx-to-biopax
Originated from Arman's https://bitbucket.org/armish/gsoc14 and will continue here (ToDo).

## SBML to BioPAX Level3 converter, specific to the Recon2 data/model.
Although there already existed an 
[SBML-to-BioPAX converter](https://sourceforge.net/apps/mediawiki/sbfc/index.php?title=Main_Page#SBML_to_BioPax), 
the produced BioPAX does not validate via the official BioPAX Validator 
and contain semantic errors, hindering its import into PC. For this part 
of the project, Arman fixed the SBML-to-BioPAX converter and made sure 
that it produces a valid BioPAX file with proper external identification 
information. 

### Data source
- **Home page**: [http://humanmetabolism.org](http://humanmetabolism.org); 
also - at the BioModels - http://www.ebi.ac.uk/biomodels-main/MODEL1109130000 
(Thiele *et al.*, 2013)
- **Type**: Human metabolism
- **Format**: SBML (Systems Biology Markup Language)
- **License**: N/A (Public)

### Implementation details
The existing converter was originally written for converting SBML 2 models 
into BioPAX and obviously was extended later to support BioPAX L3 as well.
This being said, the converter was not making good use of all Paxtools 
utilities that can make the code much simpler and cleaner.
I first tried to modify the existing code, but stuck with library conflicts 
and was not able to resolve the problems. See the initial changesets 
starting from tag `base1` till `milestone1.1`.

To keep things much simpler, we created a new project from the scratch. 
This project depends on Paxtools and JSBML libraries.
We implemented the converter so that this project can be used as a 
library by other projects as well.
The main class of this project, `ReconxToBiopax`, serves as an 
example to show how to use this API:

	//java
	// ...
	SBMLDocument sbmlDocument = SBMLReader.read(new File(sbmlFile));
	SbmlToBiopaxConverter converter = new SbmlToBiopaxConverter();
	Model bpModel = converter.convert(sbmlDocument);
	// where bpModel is the BioPAX model
	// ...

During implementation, we separated utility methods and main flow as 
much as possible, so that we have all main conversion logic in the 
`SbmlToBiopaxConverter` class and all utility methods in the `SbmlToBiopaxUtils`.

The logic of the conversion is as follows:

1. Load SBML document.
2. Get the parent model in the document.
3. Convert SBML::model to BioPAX::Pathway. ToDo: make it optional; create Provenance (instead).
4. Iterate over all reactions within SBML::model.
	1. Convert SBML::reaction to BioPAX::Conversion.
	2. Convert all SBML::modifiers to this reaction into BioPAX::Control reactions.
	3. Convert all SBML::reactants to BioPAX::leftParticipants.
	4. Convert all SBML::products to BioPAX::rightParticipants.
	5. If SBML::reaction::isReversible, make BioPAX::Conversion reversible as well.
	6. Add all reactions to the parent pathway. ToDo: make optional; define dataSource property for all Entities.
5. Fix outstanding issues with the model and complete it by adding missing components.

One key thing with this conversion is that, often, external knowledge 
is required to decide which particular BioPAX class to create.
For example, an SBML::species can be a BioPAX::Complex, Protein, SmallMolecule and *etc*.
Or you can have SBML::reactions as BioPAX::BiochemicalReaction or BioPAX::Transport.
To make these distinctions, this implementation uses SBO Terms used in Recon 2 model.
The good news is that SBO terms serve as a nice reference; and the bad 
news is that not all SBML models have these terms/annotations associated with SBML entities.

Due to these, etc., issues, 
**current implementation is coupled to the Recon 2 SBML Level2 v4 (with URNs) model** 
(contains MIRIAM URNs instead of Identified.org URIs). Although it is 
possible to convert any other SBML model into BioPAX, the semantics 
might suffer depending on the annotation details in that particular model.

### Usage
After checking out the repository, change your working directory to there:

	$ cd reconx-to-biopax

Build - create an executable JAR file:

	$ mvn clean package

You can then run the converter as follows:

	$ java -jar target/reconx-to-biopax.jar 
	> Usage: ReconxToBiopax input.sbml output.owl

To test the application, you can download the _SBML Level2 v4 (with URNs)_ 
model either from the corresponding [BioModel page](http://www.ebi.ac.uk/biomodels-main/MODEL1109130000).
**Note:** if you download the BioPAX L3 model from the MODEL1109130000 
BioModel page, that one is quite not good for importing into 
Pathway Commons due to being incomplete, simplified mapping from the SBML.

The following commands, for example, convert this file into BioPAX:

	$ wget https://bitbucket.org/armish/gsoc14/downloads/goal1_input_recon2.sbml.gz	
	$ gunzip goal1_input_recon2.sbml.gz
	$ java -Xmx16g -jar target/reconx-to-biopax.jar goal1_input_recon2.sbml goal1_output_recon2.owl

For sample output, you can check [goal1_output20140529.owl.gz](https://bitbucket.org/armish/gsoc14/downloads/goal1_output20140529.owl.gz).

### Validation results
The validation report for the converted model is pretty good and include 
only a single type of `error` due to the lack of annotations to some 
entities in the SBML model. The HTML report can be accessed from the `Downloads` section: 
[goal1_sbml2biopax_validationResults_20140529.zip](https://bitbucket.org/armish/gsoc14/downloads/goal1_sbml2biopax_validationResults_20140529.zip).
The outstanding error with the report is related to `EntityReference` 
instances that don't have any `UnificationXref`s associated with them.
This is not an artifact of the conversion, but rather a result of the 
lack of annotations in the Recon 2 model, where some of the `SmallMolecule` 
species do not have any annotations to them, hence don't have any `UnificationXref`s.
