import org.eclipse.rdf4j.federated.FedXFactory;
import org.eclipse.rdf4j.federated.QueryManager;
import org.eclipse.rdf4j.federated.endpoint.Endpoint;
import org.eclipse.rdf4j.federated.endpoint.EndpointFactory;
import org.eclipse.rdf4j.federated.repository.FedXRepository;
import org.eclipse.rdf4j.federated.structures.FedXTupleQuery;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    static private final String queryPrefixes = "prefix fmi: <http://iwu.fraunhofer.de/h2link/fmi/> " +
            "prefix ssp: <http://iwu.fraunhofer.de/h2link/ssp/>" +
            "prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
            "prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
            "prefix port: <http://iwu.fraunhofer.de/h2link/port/>" +
            "prefix h2link: <http://iwu.fraunhofer.de/h2link/db/>" +
            "prefix dfe: <http://example.org/models/h2link/dfe/>" ;


    public static void main(String[] args) throws IOException {
        RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);

        SailRepository repo1 = new SailRepository(new MemoryStore());
        RepositoryConnection conn1 = repo1.getConnection();
        Model model1 = new LinkedHashModel();
        rdfParser.setRDFHandler(new StatementCollector(model1));
        rdfParser.parse(Main.class.getResource("db.ttl").openStream());
        conn1.add(model1);


        SailRepository repo2 = new SailRepository(new MemoryStore());
        RepositoryConnection conn2 = repo2.getConnection();
        Model model2 = new LinkedHashModel();
        rdfParser.setRDFHandler(new StatementCollector(model2));
        rdfParser.parse(Main.class.getResource("frontendData.ttl").openStream());
        conn2.add(model2);

        List<Endpoint> endpoints = List.of(
                EndpointFactory.loadEndpoint("repo1", repo1),
                EndpointFactory.loadEndpoint("repo2", repo2)
        );

        FedXRepository fedXRepository = FedXFactory.newFederation().withMembers(endpoints).create();
        RepositoryConnection fedxConn = fedXRepository.getConnection();

        QueryManager qm = fedXRepository.getQueryManager();

        Query queryComponent = qm.prepareQuery(
                queryPrefixes +
                        "select distinct ?comp" +
                        "{" +
                        "{?interface a port:Port.} UNION {?interface a fmi:ScalarVariable.}. " +
                        "?comp a fmi:FmuModel. " +
                        "?conn a ssp:Connection. " +
                        "?interface  port:isPortOf | fmi:isVariableOf ?comp. " +
                        "?conn ssp:connectsFrom | ssp:connectsTo ?interface." +
                        "}");

        List<BindingSet> componentList = ((FedXTupleQuery)queryComponent).evaluate().stream().collect(Collectors.toList());
        assert componentList.size() > 0;

        componentList.forEach(componentListElement -> {
            String component = componentListElement.getBinding("comp").getValue().stringValue();

            Query queryPorts = qm.prepareQuery(
                    queryPrefixes +
                            "select DISTINCT ?port" +
                            "{ ?port a port:Port." +
                            "?comp a fmi:FmuModel." +
                            "?port port:isPortOf <" + component + ">." +
                            "}");
            List<BindingSet> portList = ((FedXTupleQuery)queryPorts).evaluate().stream().collect(Collectors.toList());
            assert portList.size() > 0;

            Query queryConnVariables = qm.prepareQuery(
                    queryPrefixes +
                            "select DISTINCT ?variable" +
                            "{ ?variable a fmi:ScalarVariable." +
                            "?comp a fmi:FmuModel." +
                            "?variable  fmi:isVariableOf <" + component + ">." +
                            "?conn ssp:connectsFrom | ssp:connectsTo ?variable." +
                            "}");
            //Hier die Stelle mit der Timeoutexception
            List<BindingSet> variableList = ((FedXTupleQuery)queryConnVariables).evaluate().stream().collect(Collectors.toList());
            assert variableList.size() == 0;

        });

    }
}
