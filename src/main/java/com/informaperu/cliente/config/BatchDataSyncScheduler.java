package com.informaperu.cliente.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.informaperu.cliente.service.ClienteService;

@Component
public class BatchDataSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BatchDataSyncScheduler.class);
    private final ClienteService clienteService;

    public BatchDataSyncScheduler(ClienteService clienteService) {
        this.clienteService = clienteService;
        logger.info("✅ BatchDataSyncScheduler inicializado correctamente");
    }

    @Scheduled(cron = "0 */3 * * * ?") // Ejecutar cada 3 minutos para pruebas
    public void syncBatchData() {
        logger.info("⏰ Ejecutando sincronización programada de datos");
        clienteService.procesarBatchConReintentos();
    }
}