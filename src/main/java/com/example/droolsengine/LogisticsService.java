package com.example.droolsengine;


import com.example.droolstest.Country;
import com.example.droolstest.Customer;
import org.drools.decisiontable.InputType;
import org.drools.decisiontable.SpreadsheetCompiler;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    public static void main(String[] args) {
        LogisticsService l = new LogisticsService();
        Logistics logistics = new Logistics(50, DeliveryCountry.fromString("RS"));
        l.logisticDecisionManager(logistics);
    }

    public void logisticDecisionManager(Logistics logistics) {
        KieServices kieServices = KieServices.Factory.get();
        Resource dt = ResourceFactory.newClassPathResource("rules/Logistics.drl.xls", getClass());

        SpreadsheetCompiler spreadsheetCompiler = new SpreadsheetCompiler();
        String drl = spreadsheetCompiler.compile(dt, InputType.XLS);

        System.out.println(drl);

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem().write(dt);
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        KieRepository kieRepository = kieServices.getRepository();
        ReleaseId krDefaultReleaseId = kieRepository.getDefaultReleaseId();
        KieContainer kieContainer = kieServices.newKieContainer(krDefaultReleaseId);

        try (KieSession kieSession = kieContainer.newKieSession()) {

            kieSession.insert(logistics);
            kieSession.fireAllRules();

            System.out.println(logistics.getDeliveryType());
        } catch (Exception e) {
            log.error("e: ", e);
        }



    }
}
