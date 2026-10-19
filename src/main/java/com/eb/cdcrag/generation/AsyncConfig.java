package com.eb.cdcrag.generation;

import com.eb.cdcrag.llm.OllamaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** Un thread par instance Ollama configurée (cdcrag.ollama.urls) : au-delà,
     * des threads supplémentaires ne feraient qu'attendre une instance libre
     * (OllamaClient.acquire). La contrainte RAM d'origine (deux générations
     * 7b concurrentes saturent la machine) reste valable PAR MACHINE -- ne
     * pas mettre deux fois localhost dans cdcrag.ollama.urls. */
    @Bean(name = "generationExecutor")
    public Executor generationExecutor(@Value("${cdcrag.ollama.urls:http://localhost:11434}") String ollamaUrls) {
        int instanceCount = OllamaClient.parseUrls(ollamaUrls).size();
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(instanceCount);
        e.setMaxPoolSize(instanceCount);
        e.setQueueCapacity(20);
        e.setThreadNamePrefix("cdc-gen-");
        e.initialize();
        return e;
    }
}
