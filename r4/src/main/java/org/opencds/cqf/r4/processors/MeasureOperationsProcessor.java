package org.opencds.cqf.r4.processors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.cql.execution.LibraryLoader;
import org.opencds.cqf.library.r4.NarrativeProvider;
import org.opencds.cqf.measure.r4.CqfMeasure;
import org.opencds.cqf.r4.evaluation.MeasureEvaluation;
import org.opencds.cqf.r4.helpers.LibraryHelper;
import org.opencds.cqf.r4.providers.DataRequirementsProvider;
import org.opencds.cqf.r4.providers.HQMFProvider;

import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.common.factories.DefaultTerminologyProviderFactory;
import org.opencds.cqf.common.helpers.DateHelper;
import org.opencds.cqf.r4.factories.DefaultLibraryLoaderFactory;

import com.alphora.cql.service.Service;
import com.alphora.cql.service.factory.DataProviderFactory;
import com.alphora.cql.service.factory.TerminologyProviderFactory;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoRegistry;
import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.TokenParamModifier;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

//Might even separate the Operations into MeasureRelatedDataProcessor and MeasureEvaluationProcessor
public class MeasureOperationsProcessor {

    private NarrativeProvider narrativeProvider;
    private HQMFProvider hqmfProvider;
    private DataRequirementsProvider dataRequirementsProvider;

    private LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResolutionProvider;
    private MeasureResourceProvider measureResourceProvider;
    private DaoRegistry registry;
    private DataProviderFactory dataProviderFactory;
    private TerminologyProvider localSystemTerminologyProvider;
    private FhirContext fhirContext;

    public MeasureOperationsProcessor(DaoRegistry registry, DataProviderFactory dataProviderFactory, TerminologyProvider localSystemTerminologyProvider, NarrativeProvider narrativeProvider, HQMFProvider hqmfProvider, LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResolutionProvider,
    MeasureResourceProvider measureResourceProvider, FhirContext fhirContext) {
        this.registry = registry;
        this.dataProviderFactory = dataProviderFactory;

        this.libraryResolutionProvider = libraryResolutionProvider;
        this.narrativeProvider = narrativeProvider;
        this.hqmfProvider = hqmfProvider;
        this.dataRequirementsProvider = new DataRequirementsProvider();
        this.measureResourceProvider = measureResourceProvider;
        this.localSystemTerminologyProvider = localSystemTerminologyProvider;
        this.fhirContext = fhirContext;
        
    }

    public Parameters hqmf(IdType theId) {
        Measure theResource = this.measureResourceProvider.getDao().read(theId);
        String hqmf = this.generateHQMF(theResource);
        Parameters p = new Parameters();
        p.addParameter().setValue(new StringType(hqmf));
        return p;
    }

    private String generateHQMF(Measure theResource) {
        CqfMeasure cqfMeasure = this.dataRequirementsProvider.createCqfMeasure(theResource, this.libraryResolutionProvider);
        return this.hqmfProvider.generateHQMF(cqfMeasure);
    }

    public MethodOutcome refreshGeneratedContent(HttpServletRequest theRequest, RequestDetails theRequestDetails, IdType theId) {
        Measure theResource = this.measureResourceProvider.getDao().read(theId);
        CqfMeasure cqfMeasure = this.dataRequirementsProvider.createCqfMeasure(theResource, this.libraryResolutionProvider);

        // Ensure All Related Artifacts for all referenced Libraries
        if (!cqfMeasure.getRelatedArtifact().isEmpty()) {
            for (RelatedArtifact relatedArtifact : cqfMeasure.getRelatedArtifact()) {
                boolean artifactExists = false;
                // logger.info("Related Artifact: " + relatedArtifact.getUrl());
                for (RelatedArtifact resourceArtifact : theResource.getRelatedArtifact()) {
                    if (resourceArtifact.equalsDeep(relatedArtifact)) {
                        // logger.info("Equals deep true");
                        artifactExists = true;
                        break;
                    }
                }
                if (!artifactExists) {
                    theResource.addRelatedArtifact(relatedArtifact.copy());
                }
            }
        }

        try {
			Narrative n = this.narrativeProvider.getNarrative(this.measureResourceProvider.getContext(), cqfMeasure);
			theResource.setText(n.copy());
		} catch (Exception e) {
			//Ignore the exception so the resource still gets updated
        }
        
        return this.measureResourceProvider.update(theRequest, theResource, theId,
                theRequestDetails.getConditionalUrl(RestOperationTypeEnum.UPDATE), theRequestDetails);
    }

    public Parameters getNarrative(IdType theId) {
        Measure theResource = this.measureResourceProvider.getDao().read(theId);
        CqfMeasure cqfMeasure = this.dataRequirementsProvider.createCqfMeasure(theResource, this.libraryResolutionProvider);
        Narrative n = this.narrativeProvider.getNarrative(this.measureResourceProvider.getContext(), cqfMeasure);
        Parameters p = new Parameters();
        p.addParameter().setValue(new StringType(n.getDivAsString()));
        return p;
    }

    /*
     *
     * NOTE that the source, user, and pass parameters are not standard parameters
     * for the FHIR $evaluate-measure operation
     *
     */
    public MeasureReport evaluateMeasure(IdType theId, String periodStart, String periodEnd, String measureRef,
     String reportType, String patientRef, String productLine, String practitionerRef, String lastReceivedOn,
     Endpoint terminologyEndpoint) throws InternalErrorException, FHIRException {
        
        DefaultLibraryLoaderFactory libraryFactory = new DefaultLibraryLoaderFactory(this.libraryResolutionProvider);
        LibraryLoader libraryLoader = libraryFactory.create(this.libraryResolutionProvider);
        Measure measure = this.measureResourceProvider.getDao().read(theId);

        if (measure == null) {
            throw new RuntimeException("Could not find Measure/" + theId.getIdPart());
        }
        
        LibraryHelper.loadLibraries(measure, libraryLoader, this.libraryResolutionProvider);

        // resolve primary library
        org.cqframework.cql.elm.execution.Library library = LibraryHelper.resolvePrimaryLibrary(measure, libraryLoader, this.libraryResolutionProvider);
        

        Map<Pair<String, String>, Object> parametersMap = new HashMap<Pair<String, String>, Object>();

        if (periodStart != null && periodEnd != null) {
            // resolve the measurement period
            Interval measurementPeriod = new Interval(DateHelper.resolveRequestDate(periodStart, true), true,
            DateHelper.resolveRequestDate(periodEnd, false), true);

            parametersMap.put(Pair.of(null, "Measurement Period"),
                    new Interval(DateTime.fromJavaDate((Date) measurementPeriod.getStart()), true,
                            DateTime.fromJavaDate((Date) measurementPeriod.getEnd()), true));
        }
        
        if (productLine != null) {
            parametersMap.put(Pair.of(null, "Product Line"), productLine);
        }

        Map<String, Endpoint> endpointIndex = new HashMap<String, Endpoint>();
        if(terminologyEndpoint != null) {
            endpointIndex.put(terminologyEndpoint.getAddress(), terminologyEndpoint);
        }

        //TODO: resolveContextParameters i.e. patient
        com.alphora.cql.service.Parameters evaluationParameters = new com.alphora.cql.service.Parameters();
        evaluationParameters.libraries = Collections.singletonList(library.toString());
        evaluationParameters.parameters = parametersMap;   
        evaluationParameters.expressions =  new ArrayList<Pair<String, String>>();
        evaluationParameters.libraryName = library.getIdentifier().getId();   

        TerminologyProviderFactory terminologyProviderFactory = new DefaultTerminologyProviderFactory<Endpoint>(fhirContext, this.localSystemTerminologyProvider, endpointIndex);

        Service service = new Service(libraryFactory, dataProviderFactory, terminologyProviderFactory, null, null, null, null);

        // resolve report type
        MeasureEvaluation evaluator = new MeasureEvaluation(evaluationParameters, service, library, this.registry);
        if (reportType != null) {
            switch (reportType) {
            case "patient":
                return evaluator.evaluatePatientMeasure(measure, patientRef);
            case "patient-list":  
                return evaluator.evaluateSubjectListMeasure(measure, practitionerRef);
            case "population":
                return evaluator.evaluatePopulationMeasure(measure);
            default:
                throw new IllegalArgumentException("Invalid report type: " + reportType);
            }
        }

        // default report type is patient
        MeasureReport report = evaluator.evaluatePatientMeasure(measure, patientRef);
        if (productLine != null)
        {
            Extension ext = new Extension();
            ext.setUrl("http://hl7.org/fhir/us/cqframework/cqfmeasures/StructureDefinition/cqfm-productLine");
            ext.setValue(new StringType(productLine));
            report.addExtension(ext);
        }

        return report;
    }

    // public MeasureReport evaluateMeasure(IdType theId, Bundle sourceData, String periodStart, String periodEnd) {
    //     if (periodStart == null || periodEnd == null) {
    //         throw new IllegalArgumentException("periodStart and periodEnd are required for measure evaluation");
    //     }
    //     LibraryLoader libraryLoader = LibraryHelper.createLibraryLoader(this.libraryResourceProvider);
    //     MeasureEvaluationSeed seed = new MeasureEvaluationSeed(this.factory, libraryLoader, this.libraryResourceProvider);
    //     Measure measure = this.getDao().read(theId);

    //     if (measure == null) {
    //         throw new RuntimeException("Could not find Measure/" + theId.getIdPart());
    //     }

    //     seed.setup(measure, periodStart, periodEnd, null, null, null, null);
    //     BundleDataProviderStu3 bundleProvider = new BundleDataProviderStu3(sourceData);
    //     bundleProvider.setTerminologyProvider(provider.getTerminologyProvider());
    //     seed.getContext().registerDataProvider("http://hl7.org/fhir", bundleProvider);
    //     MeasureEvaluation evaluator = new MeasureEvaluation(bundleProvider, seed.getMeasurementPeriod());
    //     return evaluator.evaluatePatientMeasure(seed.getMeasure(), seed.getContext(), "");
    // }

    public Bundle careGapsReport( String periodStart, String periodEnd, String topic, String patientRef, Endpoint terminologyEndpoint) {
        List<IBaseResource> measures = this.measureResourceProvider.getDao().search(new SearchParameterMap().add("topic",
                new TokenParam().setModifier(TokenParamModifier.TEXT).setValue(topic))).getResources(0, 1000);
        Bundle careGapReport = new Bundle();
        careGapReport.setType(Bundle.BundleType.DOCUMENT);

        Composition composition = new Composition();
        // TODO - this is a placeholder code for now ... replace with preferred code
        // once identified
        CodeableConcept typeCode = new CodeableConcept()
                .addCoding(new Coding().setSystem("http://loinc.org").setCode("57024-2"));
        composition.setStatus(Composition.CompositionStatus.FINAL).setType(typeCode)
                .setSubject(new Reference(patientRef.startsWith("Patient/") ? patientRef : "Patient/" + patientRef))
                .setTitle(topic + " Care Gap Report");

        List<MeasureReport> reports = new ArrayList<>();
        MeasureReport report = new MeasureReport();
        for (IBaseResource resource : measures) {
            Composition.SectionComponent section = new Composition.SectionComponent();

            Measure measure = (Measure) resource;
            section.addEntry(
                    new Reference(measure.getIdElement().getResourceType() + "/" + measure.getIdElement().getIdPart()));
            if (measure.hasTitle()) {
                section.setTitle(measure.getTitle());
            }
            CodeableConcept improvementNotation = new CodeableConcept().addCoding(new Coding().setCode("increase").setSystem("http://terminology.hl7.org/CodeSystem/measure-improvement-notation")); // defaulting to "increase"
            if (measure.hasImprovementNotation()) {
                improvementNotation = measure.getImprovementNotation();
                section.setText(new Narrative().setStatus(Narrative.NarrativeStatus.GENERATED)
                        .setDiv(new XhtmlNode().setValue(improvementNotation.getCodingFirstRep().getCode())));
            }

        DefaultLibraryLoaderFactory libraryFactory = new DefaultLibraryLoaderFactory(this.libraryResolutionProvider);
        LibraryLoader libraryLoader = libraryFactory.create(this.libraryResolutionProvider);
        
        LibraryHelper.loadLibraries(measure, libraryLoader, this.libraryResolutionProvider);

        // resolve primary library
        org.cqframework.cql.elm.execution.Library library = LibraryHelper.resolvePrimaryLibrary(measure, libraryLoader, this.libraryResolutionProvider);
        

        Map<Pair<String, String>, Object> parametersMap = new HashMap<Pair<String, String>, Object>();

        if (periodStart != null && periodEnd != null) {
            // resolve the measurement period
            Interval measurementPeriod = new Interval(DateHelper.resolveRequestDate(periodStart, true), true,
            DateHelper.resolveRequestDate(periodEnd, false), true);

            parametersMap.put(Pair.of(null, "Measurement Period"),
                    new Interval(DateTime.fromJavaDate((Date) measurementPeriod.getStart()), true,
                            DateTime.fromJavaDate((Date) measurementPeriod.getEnd()), true));
        }

        Map<String, Endpoint> endpointIndex = new HashMap<String, Endpoint>();
        if(terminologyEndpoint != null) {
            endpointIndex.put(terminologyEndpoint.getAddress(), terminologyEndpoint);
        }

        //TODO: resolveContextParameters i.e. patient
        com.alphora.cql.service.Parameters evaluationParameters = new com.alphora.cql.service.Parameters();
        evaluationParameters.libraries = Collections.singletonList(library.toString());
        evaluationParameters.parameters = parametersMap;    
        evaluationParameters.expressions =  new ArrayList<Pair<String, String>>();  

        TerminologyProviderFactory terminologyProviderFactory = new DefaultTerminologyProviderFactory<Endpoint>(fhirContext, this.localSystemTerminologyProvider, endpointIndex);

        Service service = new Service(libraryFactory, dataProviderFactory, terminologyProviderFactory, null, null, null, null);

        // resolve report type
        MeasureEvaluation evaluator = new MeasureEvaluation(evaluationParameters, service, library, this.registry);
            // TODO - this is configured for patient-level evaluation only
            report = evaluator.evaluatePatientMeasure(measure, patientRef);

            if (report.hasGroup() && measure.hasScoring()) {
                int numerator = 0;
                int denominator = 0;
                for (MeasureReport.MeasureReportGroupComponent group : report.getGroup()) {
                    if (group.hasPopulation()) {
                        for (MeasureReport.MeasureReportGroupPopulationComponent population : group.getPopulation()) {
                            // TODO - currently configured for measures with only 1 numerator and 1
                            // denominator
                            if (population.hasCode()) {
                                if (population.getCode().hasCoding()) {
                                    for (Coding coding : population.getCode().getCoding()) {
                                        if (coding.hasCode()) {
                                            if (coding.getCode().equals("numerator") && population.hasCount()) {
                                                numerator = population.getCount();
                                            } else if (coding.getCode().equals("denominator")
                                                    && population.hasCount()) {
                                                denominator = population.getCount();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                double proportion = 0.0;
                if (measure.getScoring().hasCoding() && denominator != 0) {
                    for (Coding coding : measure.getScoring().getCoding()) {
                        if (coding.hasCode() && coding.getCode().equals("proportion")) {
                            proportion = numerator / denominator;
                        }
                    }
                }

                // TODO - this is super hacky ... change once improvementNotation is specified
                // as a code
                if (improvementNotation.getCodingFirstRep().getCode().toLowerCase().equals("increase")) {
                    if (proportion < 1.0) {
                        composition.addSection(section);
                        reports.add(report);
                    }
                } else if (improvementNotation.getCodingFirstRep().getCode().toLowerCase().equals("decrease")) {
                    if (proportion > 0.0) {
                        composition.addSection(section);
                        reports.add(report);
                    }
                }

                // TODO - add other types of improvement notation cases
            }
        }

        careGapReport.addEntry(new Bundle.BundleEntryComponent().setResource(composition));

        for (MeasureReport rep : reports) {
            careGapReport.addEntry(new Bundle.BundleEntryComponent().setResource(rep));
        }

        return careGapReport;
    }

    public Parameters collectData(IdType theId, String periodStart, String periodEnd, String patientRef, String practitionerRef,
     String lastReceivedOn, Endpoint endpoint) throws FHIRException {
        // TODO: Spec says that the periods are not required, but I am not sure what to
        // do when they aren't supplied so I made them required
        MeasureReport report = evaluateMeasure(theId, periodStart, periodEnd, null, null,  patientRef, null,
                practitionerRef, lastReceivedOn, null);
        report.setGroup(null);

        Parameters parameters = new Parameters();

        parameters.addParameter(
                new Parameters.ParametersParameterComponent().setName("measurereport").setResource(report));

        if (report.hasContained()) {
            for (Resource contained : report.getContained()) {
                if (contained instanceof Bundle) {
                    addEvaluatedResourcesToParameters((Bundle) contained, parameters);
                }
            }
        }

        // TODO: need a way to resolve referenced resources within the evaluated
        // resources
        // Should be able to use _include search with * wildcard, but HAPI doesn't
        // support that

        return parameters;
    }

    private void addEvaluatedResourcesToParameters(Bundle contained, Parameters parameters) {
        Map<String, Resource> resourceMap = new HashMap<>();
        if (contained.hasEntry()) {
            for (Bundle.BundleEntryComponent entry : contained.getEntry()) {
                if (entry.hasResource() && !(entry.getResource() instanceof ListResource)) {
                    if (!resourceMap.containsKey(entry.getResource().getIdElement().getValue())) {
                        parameters.addParameter(new Parameters.ParametersParameterComponent().setName("resource")
                                .setResource(entry.getResource()));

                        resourceMap.put(entry.getResource().getIdElement().getValue(), entry.getResource());

                        resolveReferences(entry.getResource(), parameters, resourceMap);
                    }
                }
            }
        }
    }

    private void resolveReferences(Resource resource, Parameters parameters, Map<String, Resource> resourceMap) {
        List<IBase> values;
        for (BaseRuntimeChildDefinition child : this.measureResourceProvider.getContext().getResourceDefinition(resource).getChildren()) {
            values = child.getAccessor().getValues(resource);
            if (values == null || values.isEmpty()) {
                continue;
            }

            else if (values.get(0) instanceof Reference
                    && ((Reference) values.get(0)).getReferenceElement().hasResourceType()
                    && ((Reference) values.get(0)).getReferenceElement().hasIdPart()) {
                Resource fetchedResource = (Resource) registry
                        .getResourceDao(((Reference) values.get(0)).getReferenceElement().getResourceType())
                        .read(new IdType(((Reference) values.get(0)).getReferenceElement().getIdPart()));

                if (!resourceMap.containsKey(fetchedResource.getIdElement().getValue())) {
                    parameters.addParameter(new Parameters.ParametersParameterComponent().setName("resource")
                            .setResource(fetchedResource));

                    resourceMap.put(fetchedResource.getIdElement().getValue(), fetchedResource);
                }
            }
        }
    }

    // TODO - this needs a lot of work
    public org.hl7.fhir.r4.model.Library dataRequirements(IdType theId, String startPeriod, String endPeriod) throws InternalErrorException, FHIRException {
        Measure measure = this.measureResourceProvider.getDao().read(theId);
        return this.dataRequirementsProvider.getDataRequirements(measure, this.libraryResolutionProvider);
    }

    public Resource submitData(RequestDetails details, IdType theId, MeasureReport report, List<IAnyResource> resources) {
        Bundle transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);

        /*
         * TODO - resource validation using $data-requirements operation (params are the
         * provided id and the measurement period from the MeasureReport)
         * 
         * TODO - profile validation ... not sure how that would work ... (get
         * StructureDefinition from URL or must it be stored in Ruler?)
         */

        transactionBundle.addEntry(createTransactionEntry(report));

        for (IAnyResource resource : resources) {
            Resource res = (Resource) resource;
            if (res instanceof Bundle) {
                for (Bundle.BundleEntryComponent entry : createTransactionBundle((Bundle) res).getEntry()) {
                    transactionBundle.addEntry(entry);
                }
            } else {
                // Build transaction bundle
                transactionBundle.addEntry(createTransactionEntry(res));
            }
        }

        return (Resource) this.registry.getSystemDao().transaction(details, transactionBundle);
    }

    private Bundle createTransactionBundle(Bundle bundle) {
        Bundle transactionBundle;
        if (bundle != null) {
            if (bundle.hasType() && bundle.getType() == Bundle.BundleType.TRANSACTION) {
                transactionBundle = bundle;
            } else {
                transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.hasResource()) {
                            transactionBundle.addEntry(createTransactionEntry(entry.getResource()));
                        }
                    }
                }
            }
        } else {
            transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION).setEntry(new ArrayList<>());
        }

        return transactionBundle;
    }

    private Bundle.BundleEntryComponent createTransactionEntry(Resource resource) {
        Bundle.BundleEntryComponent transactionEntry = new Bundle.BundleEntryComponent().setResource(resource);
        if (resource.hasId()) {
            transactionEntry.setRequest(
                    new Bundle.BundleEntryRequestComponent().setMethod(Bundle.HTTPVerb.PUT).setUrl(resource.getId()));
        } else {
            transactionEntry.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(Bundle.HTTPVerb.POST)
                    .setUrl(resource.fhirType()));
        }
        return transactionEntry;
    }
}