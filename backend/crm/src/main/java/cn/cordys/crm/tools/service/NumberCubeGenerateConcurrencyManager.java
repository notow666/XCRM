package cn.cordys.crm.tools.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.tools.config.NumberCubeProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class NumberCubeGenerateConcurrencyManager {

    private final int maxPermits;
    private final Semaphore generateJobSemaphore;

    public NumberCubeGenerateConcurrencyManager(NumberCubeProperties properties) {
        this.maxPermits = Math.max(1, properties.getMaxConcurrentGenerateJobs());
        this.generateJobSemaphore = new Semaphore(this.maxPermits, true);
    }

    public void acquireGenerateSlot() {
        if (!generateJobSemaphore.tryAcquire()) {
            throw new GenericException(Translator.get("number_cube_generate_busy"));
        }
    }

    public void releaseGenerateSlot() {
        // After JVM restart no slots are held; never push permits above configured max.
        if (generateJobSemaphore.availablePermits() < maxPermits) {
            generateJobSemaphore.release();
        }
    }
}
