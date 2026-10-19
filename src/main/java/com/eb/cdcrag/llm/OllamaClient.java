package com.eb.cdcrag.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** Selects the first available configured Ollama instance. Availability-based
 * selection avoids leaving a faster instance idle while a slower instance is
 * still generating. A single configured URL is also supported. */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    // Selected after evaluation: larger alternatives exceeded the read timeout.
    private static final String MODEL = "qwen2.5:7b";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final List<OllamaInstance> instances;

    public OllamaClient(@Value("${cdcrag.ollama.urls:http://localhost:11434}") String urlsCsv) {
        this.instances = parseUrls(urlsCsv).stream().map(OllamaClient::buildInstance).toList();
        if (instances.isEmpty()) {
            throw new IllegalStateException("cdcrag.ollama.urls ne contient aucune URL valide");
        }
        log.info("OllamaClient : {} instance(s) configurée(s) : {}", instances.size(),
                instances.stream().map(OllamaInstance::url).toList());
    }

    /** Exposé pour AsyncConfig (package différent) : le pool de threads
     * (generationExecutor) doit avoir autant de threads que d'instances
     * configurées, sans dupliquer ici la logique de parsing. */
    public static List<String> parseUrls(String urlsCsv) {
        return List.of(urlsCsv.split(",")).stream()
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static OllamaInstance buildInstance(String url) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(10));
        // Allow up to six minutes for a section before trying another instance.
        f.setReadTimeout(Duration.ofMinutes(6));
        RestClient client = RestClient.builder().baseUrl(url).requestFactory(f).build();
        return new OllamaInstance(url, client, new Semaphore(1));
    }

    /** context ne sert qu'à la journalisation (quelle instance a traité
     * quoi), jamais envoyé à Ollama. En cas d'échec ou de non-réponse d'une
     * instance, réessaye sur une autre avant d'abandonner -- l'appelant
     * (CdcSectionGenerationWorker) n'a pas à connaître les instances : une
     * section passe FAILED seulement si TOUTES ont échoué. */
    public String generate(String prompt, String context) {
        Set<String> excludedUrls = new HashSet<>();
        Exception lastError = null;

        for (int attempt = 0; attempt < instances.size(); attempt++) {
            OllamaInstance instance = acquire(excludedUrls);
            excludedUrls.add(instance.url());
            long t0 = System.currentTimeMillis();
            try {
                String result = call(instance, prompt);
                log.info("{} : généré sur {} en {} s", context, instance.url(),
                        (System.currentTimeMillis() - t0) / 1000);
                return result;
            } catch (Exception ex) {
                log.warn("{} : échec sur {} ({}), nouvelle tentative sur une autre instance",
                        context, instance.url(), ex.toString());
                lastError = ex;
            } finally {
                instance.semaphore().release();
            }
        }
        throw new IllegalStateException(
                "Toutes les instances Ollama ont échoué pour " + context, lastError);
    }

    private String call(OllamaInstance instance, String prompt) {
        Response r = instance.client().post()
                .uri("/api/generate")
                .body(Map.of(
                        "model", MODEL,
                        "prompt", prompt,
                        "stream", false,
                        "keep_alive", "30m",
                        "options", Map.of("num_ctx", 8192, "num_predict", 1500, "temperature", 0.2)))
                .retrieve()
                .body(Response.class);
        if (r == null || r.response() == null) throw new IllegalStateException("Ollama returned nothing");
        return r.response().trim();
    }

    /** Un sémaphore par instance (1 permis chacun) : une instance traite un
     * appel à la fois. Scrute dans l'ordre de configuration et prend la
     * première libre (tryAcquire, non bloquant) ; si aucune ne l'est,
     * attend un court instant et rescrute -- plutôt que de bloquer sur une
     * instance précise, qui pourrait rester occupée alors qu'une autre se
     * libère avant elle (round-robin déguisé). */
    private OllamaInstance acquire(Set<String> excludedUrls) {
        while (true) {
            for (OllamaInstance instance : instances) {
                if (excludedUrls.contains(instance.url())) continue;
                if (instance.semaphore().tryAcquire()) return instance;
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrompu en attente d'une instance Ollama libre", e);
            }
        }
    }

    private record OllamaInstance(String url, RestClient client, Semaphore semaphore) {}

    record Response(String response, boolean done) {}
}
