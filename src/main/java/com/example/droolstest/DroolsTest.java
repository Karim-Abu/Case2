package com.example.droolstest;

import org.drools.base.util.Drools;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import java.util.Arrays;
import java.util.List;

public class DroolsTest {

    public static void main(String[] args) throws Exception {
        DroolsTest l = new DroolsTest();
        l.init();
    }

    private void init() {
        KieServices kieServices = KieServices.Factory.get();

        // Load the rules from the Spreadsheet
        Resource dt = ResourceFactory.newClassPathResource("rules/Discount.drl.xls", getClass());

        // Initialize the rule engine
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem().write(dt);
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();
        KieRepository kieRepository = kieServices.getRepository();
        ReleaseId krDefaultReleaseId = kieRepository.getDefaultReleaseId();
        KieContainer kieContainer = kieServices.newKieContainer(krDefaultReleaseId);
        KieSession kieSession = kieContainer.newKieSession();

        // instantiate some sample data
        Customer customer = new Customer();
        customer.setCountry(Country.CH);
        customer.setYears(3);

        // submit sample data to rule engine
        kieSession.insert(customer);

        // tell the rule engine to process all known rules based on the submitted data
        kieSession.fireAllRules();

        // check the result -> the customer should now have a matching discount
        System.out.println("Discount: " + customer.getDiscount());
    }


}
