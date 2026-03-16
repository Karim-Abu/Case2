package com.example.droolsengine;

import jakarta.annotation.PostConstruct;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drools-Service: Wertet Versandregeln aus der Excel-Entscheidungstabelle aus.
 *
 * Architekturentscheidung: KieContainer wird einmalig beim Applikationsstart
 * gebaut (@PostConstruct). KieContainer ist thread-safe und teuer zu erzeugen
 * (parst die Excel und kompiliert alle Regeln).
 * KieSession wird pro Request erzeugt — sie ist stateful, cheap und nicht
 * thread-safe.
 *
 * Fail-Fast: Falls die Excel Kompilierfehler enthält, wirft @PostConstruct eine
 * IllegalStateException und verhindert den Start der Applikation.
 */
@Service
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    private KieContainer kieContainer;

    @PostConstruct
    public void init() {
        log.info("Initialisiere Drools KieContainer aus rules/Logistics.drl.xls ...");
        KieServices kieServices = KieServices.Factory.get();
        Resource dt = ResourceFactory.newClassPathResource("rules/Logistics.drl.xls", getClass());

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem().write(dt);
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(
                    "Drools-Regelkompilierung fehlgeschlagen — App-Start abgebrochen: "
                            + results.getMessages());
        }

        KieRepository kieRepository = kieServices.getRepository();
        ReleaseId krDefaultReleaseId = kieRepository.getDefaultReleaseId();
        this.kieContainer = kieServices.newKieContainer(krDefaultReleaseId);
        log.info("Drools KieContainer erfolgreich initialisiert.");
    }

    /**
     * Wertet die Versandregeln für das übergebene Logistics-Objekt aus.
     * Setzt deliveryType und ruleName auf dem Objekt.
     * Wirft RuntimeException bei technischen Fehlern (kein stilles Schlucken).
     */
    public void logisticDecisionManager(Logistics logistics) {
        try (KieSession kieSession = kieContainer.newKieSession()) {
            kieSession.addEventListener(new RuleNameListener(logistics));
            kieSession.insert(logistics);
            kieSession.fireAllRules();
            log.info("Regelauswertung abgeschlossen: {}", logistics);
        } catch (Exception e) {
            log.error("Fehler bei der Drools-Regelauswertung: ", e);
            throw new RuntimeException("Drools-Regelauswertung fehlgeschlagen", e);
        }
    }
}
