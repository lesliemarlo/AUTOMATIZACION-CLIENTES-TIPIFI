package com.informaperu.cliente.serviceImpl;

import com.informaperu.cliente.entity.BatchState;
import com.informaperu.cliente.entity.Cliente;
import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.model.ResponseClienteWrapper;
import com.informaperu.cliente.repository.BatchStateRepository;
import com.informaperu.cliente.repository.ClienteRepository;
import com.informaperu.cliente.service.ClienteService;
import com.informaperu.cliente.service.EmailService;
import com.informaperu.cliente.service.LogService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements ClienteService {

    private static final Logger logger = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClienteRepository repository;
    private final BatchStateRepository batchStateRepository;
    private final RestTemplate restTemplate;
    private final EmailService emailService;
    private final LogService logService;

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.token}")
    private String apiToken;

    @Value("${batch.size}")
    private int batchSize;

    @Value("${batch.max-retries}")
    private int maxRetries;

    @Value("${batch.retry-wait-minutes}")
    private int retryWaitMinutes;

    // Batch parameters
    private String batchStartDate;
    private String batchEndDate;
    private int batchIntervalDays;
    private int batchLimit;
    private int batchOffset;
    private String batchPortfolio;
    private String notificationEmail;
    private String batchId;

    // Flag to control scheduled execution
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);
    private int currentRetryCount = 0;

    // Counters for summary
    private final AtomicInteger totalRecordsFetched = new AtomicInteger(0);
    private final AtomicInteger totalDuplicates = new AtomicInteger(0);
    private final AtomicInteger totalNewRecordsInserted = new AtomicInteger(0);

    // Time tracking
    private long batchStartTime;
    private List<Long> intervalTimes = new ArrayList<>();

    @Autowired
    public ClienteServiceImpl(ClienteRepository repository, BatchStateRepository batchStateRepository,
                              RestTemplate restTemplate, EmailService emailService, LogService logService) {
        this.repository = repository;
        this.batchStateRepository = batchStateRepository;
        this.restTemplate = restTemplate;
        this.emailService = emailService;
        this.logService = logService;
    }

    @Override
    public void setBatchParameters(String startDate, String endDate, int intervalDays, int limit, int offset,
                                   String portfolio, String notificationEmail) {
        this.batchStartDate = startDate;
        this.batchEndDate = endDate;
        this.batchIntervalDays = intervalDays;
        this.batchLimit = limit;
        this.batchOffset = offset;
        this.batchPortfolio = portfolio;
        this.notificationEmail = notificationEmail;
        this.batchId = UUID.randomUUID().toString();
        this.batchRunning.set(true);
        this.currentRetryCount = 0;
        this.totalRecordsFetched.set(0);
        this.totalDuplicates.set(0);
        this.totalNewRecordsInserted.set(0);
        this.batchStartTime = System.currentTimeMillis();
        this.intervalTimes.clear();

        // Initialize batch state
        BatchState state = new BatchState();
        state.setBatchId(batchId);
        state.setCompleted(false);
        batchStateRepository.save(state);

        String configLog = String.format(
                "╔══════════════════════════════════════════════════════════════════════╗\n" +
                "║                      CONFIGURACIÓN DE BATCH                          ║\n" +
                "╠══════════════════════════════════════════════════════════════════════╣\n" +
                "║ Batch ID: %-50s ║\n" +
                "║ Portfolio: %-50s ║\n" +
                "║ Fecha inicio: %-50s ║\n" +
                "║ Fecha fin: %-52s ║\n" +
                "║ Intervalo días: %-48s ║\n" +
                "║ Límite: %-54s ║\n" +
                "║ Offset: %-54s ║\n" +
                "║ Correo notificaciones: %-40s ║\n" +
                "╚══════════════════════════════════════════════════════════════════════╝",
                batchId, portfolio, startDate, endDate, intervalDays, limit, offset, notificationEmail);
        logger.info(configLog);
        logService.sendLog(configLog);

        emailService.sendNotification(this.notificationEmail, "Batch Configurado",
                String.format("Batch %s configurado con éxito.\nPortfolio: %s\nInicio: %s\nFin: %s\nIntervalo: %d días",
                        batchId, portfolio, startDate, endDate, intervalDays));
    }

    @Override
    @Transactional
    public void procesarBatchConReintentos() {
        if (!batchRunning.get()) {
            String log = "⏸️ Batch no iniciado. Acción requerida: Inicie el batch mediante el endpoint /api/cliente/batch con parámetros válidos.";
            logger.info(log);
            logService.sendLog(log);
            return;
        }

        String startLog = String.format(
                "╔═══════════════════════════════════════════════════════════════════╗\n" +
                "║                INICIANDO PROCESAMIENTO DE BATCH                   ║\n" +
                "╠═══════════════════════════════════════════════════════════════════╣\n" +
                "║ Batch ID: %-50s ║\n" +
                "║ Portfolio: %-50s ║\n" +
                "║ Periodo: %-25s a %-25s ║\n" +
                "╚═══════════════════════════════════════════════════════════════════╝",
                batchId, batchPortfolio, batchStartDate, batchEndDate);
        logger.info(startLog);
        logService.sendLog(startLog);

        try {
            processFullBatch();
        } catch (Exception e) {
            handleBatchError(e);
        }
    }

    private void processFullBatch() {
        try {
            LocalDateTime start = LocalDateTime.parse(batchStartDate, DATE_TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(batchEndDate, DATE_TIME_FORMATTER);
            LocalDateTime currentStart;

            // Load last processed state
            Optional<BatchState> stateOpt = batchStateRepository.findById(batchId);
            BatchState state = stateOpt.orElse(new BatchState());
            if (state.getLastProcessedEnd() != null && !state.isCompleted()) {
                currentStart = state.getLastProcessedEnd().plusSeconds(1);
                String log = String.format("🔄 Reanudando desde el último intervalo procesado: %s. Motivo: Batch interrumpido previamente.", currentStart);
                logger.info(log);
                logService.sendLog(log);
            } else {
                currentStart = start;
                String log = String.format("🚀 Iniciando batch desde el comienzo: %s.", currentStart);
                logger.info(log);
                logService.sendLog(log);
            }

            // Calculate total intervals for progress
            long totalIntervals = 0;
            LocalDateTime tempStart = start;
            while (!tempStart.isAfter(end)) {
                totalIntervals++;
                tempStart = tempStart.plusDays(batchIntervalDays);
            }

            long currentInterval = 0;
            long intervalsProcessed = 0;
            LocalDateTime tempCurrent = start;
            while (!tempCurrent.isAfter(currentStart)) {
                intervalsProcessed++;
                tempCurrent = tempCurrent.plusDays(batchIntervalDays);
            }
            currentInterval = intervalsProcessed - 1;

            while (!currentStart.isAfter(end)) {
                currentInterval++;

                LocalDateTime currentEnd = currentStart.plusDays(batchIntervalDays);
                if (currentEnd.isAfter(end)) {
                    currentEnd = end;
                }

                String formattedStart = currentStart.format(DATE_TIME_FORMATTER);
                String formattedEnd = currentEnd.format(DATE_TIME_FORMATTER);

                long intervalStartTime = System.currentTimeMillis();

                String intervalLog = String.format("📅 Procesando intervalo %d de %d: %s a %s. Portfolio: %s.", 
                        currentInterval, totalIntervals, formattedStart, formattedEnd, batchPortfolio);
                logger.info(intervalLog);
                logService.sendLog(intervalLog);

                // Send progress
                int progress = (int) (currentInterval * 100 / totalIntervals);
                String progressLog = String.format("Progress: %d%%", progress);
                logger.info(progressLog);
                logService.sendLog(progressLog);

                try {
                    procesarIntervalo(formattedStart, formattedEnd);

                    // Update batch state
                    state.setLastProcessedStart(currentStart);
                    state.setLastProcessedEnd(currentEnd);
                    state.setBatchId(batchId);
                    batchStateRepository.save(state);

                    long intervalDuration = System.currentTimeMillis() - intervalStartTime;
                    intervalTimes.add(intervalDuration);
                    double avgIntervalTime = intervalTimes.stream().mapToLong(Long::longValue).average().orElse(0);
                    long remainingIntervals = totalIntervals - currentInterval;
                    long estimatedRemainingTime = (long) (avgIntervalTime * remainingIntervals);

                    String timeLog = String.format("⏱️ Intervalo %d completado en %d ms. Tiempo promedio por intervalo: %d ms. Tiempo restante estimado: %d s.", 
                            currentInterval, intervalDuration, (long) avgIntervalTime, estimatedRemainingTime / 1000);
                    logger.info(timeLog);
                    logService.sendLog(timeLog);

                    emailService.sendNotification(notificationEmail, "Intervalo Procesado",
                            String.format("Batch %s: Intervalo %d/%d procesado.\nPortfolio: %s\nDesde: %s\nHasta: %s\nRegistros obtenidos: %d\nDuplicados: %d\nInsertados: %d\nTiempo: %d ms\nTiempo restante estimado: %d s",
                                    batchId, currentInterval, totalIntervals, batchPortfolio, formattedStart, formattedEnd,
                                    totalRecordsFetched.get(), totalDuplicates.get(), totalNewRecordsInserted.get(),
                                    intervalDuration, estimatedRemainingTime / 1000));
                } catch (Exception e) {
                    String errorLog = String.format("❌ Error al procesar intervalo %s a %s. Portfolio: %s. Motivo: %s. Acción: Revisar los datos de entrada o la conexión con la API/base de datos.", 
                            formattedStart, formattedEnd, batchPortfolio, e.getMessage());
                    logger.error(errorLog);
                    logService.sendLog(errorLog);
                    throw e;
                }

                currentStart = currentEnd.plusSeconds(1);
            }

            // Send final progress
            String finalProgressLog = "Progress: 100%";
            logger.info(finalProgressLog);
            logService.sendLog(finalProgressLog);

            // Mark batch as completed
            state.setCompleted(true);
            batchStateRepository.save(state);
            batchRunning.set(false);
            currentRetryCount = 0;

            long totalBatchDuration = System.currentTimeMillis() - batchStartTime;

            // Log summary
            logBatchSummary(totalIntervals, totalBatchDuration);

            emailService.sendNotification(notificationEmail, "Batch Completado",
                    String.format("Batch %s completado con éxito.\nPortfolio: %s\nPeriodo: %s a %s\nTotal intervalos: %d\nTotal registros obtenidos: %d\nTotal duplicados: %d\nTotal nuevos insertados: %d\nTiempo total: %d s",
                            batchId, batchPortfolio, batchStartDate, batchEndDate, totalIntervals, 
                            totalRecordsFetched.get(), totalDuplicates.get(), totalNewRecordsInserted.get(),
                            totalBatchDuration / 1000));
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar batch completo: " + e.getMessage(), e);
        }
    }

    private void logBatchSummary(long totalIntervals, long totalDuration) {
        String summaryLog = String.format(
                "╔═══════════════════════════════════════════════════════════════════╗\n" +
                "║                    RESUMEN DEL BATCH COMPLETADO                   ║\n" +
                "╠═══════════════════════════════════════════════════════════════════╣\n" +
                "║ Batch ID: %-50s ║\n" +
                "║ Portfolio: %-50s ║\n" +
                "║ Periodo: %-25s a %-25s ║\n" +
                "║ Total intervalos procesados: %-37s ║\n" +
                "║ Total registros obtenidos de API: %-32s ║\n" +
                "║ Total registros duplicados: %-34s ║\n" +
                "║ Total nuevos registros insertados: %-27s ║\n" +
                "║ Tiempo total de procesamiento: %-31s s ║\n" +
                "╚═══════════════════════════════════════════════════════════════════╝",
                batchId, batchPortfolio, batchStartDate, batchEndDate, totalIntervals, 
                totalRecordsFetched.get(), totalDuplicates.get(), totalNewRecordsInserted.get(), totalDuration / 1000);
        logger.info(summaryLog);
        logService.sendLog(summaryLog);
    }

    private void procesarIntervalo(String formattedStart, String formattedEnd) {
        try {
            String fetchLog = String.format("🔄 Obteniendo datos de la API para el intervalo %s a %s. Portfolio: %s.", formattedStart, formattedEnd, batchPortfolio);
            logger.info(fetchLog);
            logService.sendLog(fetchLog);

            List<ClienteDTO> datos = obtenerDatosDesdeAPI(batchLimit, batchOffset, batchPortfolio, formattedStart, formattedEnd);
            totalRecordsFetched.addAndGet(datos.size());
            String successLog = String.format("✅ Datos obtenidos correctamente: %d registros. Motivo: Respuesta exitosa de la API.", datos.size());
            logger.info(successLog);
            logService.sendLog(successLog);

            String saveLog = String.format("💾 Guardando datos en la base de datos para el intervalo %s a %s. Portfolio: %s.", formattedStart, formattedEnd, batchPortfolio);
            logger.info(saveLog);
            logService.sendLog(saveLog);

            guardarDatosEnBD(datos);
            String completeLog = String.format("✅ Datos procesados para el intervalo %s a %s. Acción: Continuar con el siguiente intervalo.", formattedStart, formattedEnd);
            logger.info(completeLog);
            logService.sendLog(completeLog);
        } catch (Exception e) {
            String errorLog = String.format("❌ Error al procesar intervalo %s a %s. Portfolio: %s. Motivo: %s. Acción: Reintentar o revisar la configuración de API/base de datos.", 
                    formattedStart, formattedEnd, batchPortfolio, e.getMessage());
            logger.error(errorLog);
            logService.sendLog(errorLog);
            throw e;
        }
    }

    private void handleBatchError(Exception e) {
        currentRetryCount++;

        String errorMessage = String.format("Batch %s: Error en intento %d/%d.\nPortfolio: %s\nMensaje: %s\nAcción: Reintentar en %d minutos o revisar logs para detalles.",
                batchId, currentRetryCount, maxRetries, batchPortfolio, e.getMessage(), retryWaitMinutes);
        logger.error("❌ {}", errorMessage);
        logService.sendLog("❌ " + errorMessage);
        emailService.sendNotification(notificationEmail, "Error en Batch", errorMessage);

        if (currentRetryCount >= maxRetries) {
            String abortLog = String.format("❌ Se alcanzó el máximo de reintentos (%d). Motivo: Error persistente en el procesamiento. Acción: Abortando batch. Revise los logs en /var/log/automatizacion.log.", maxRetries);
            logger.error(abortLog);
            logService.sendLog(abortLog);
            emailService.sendNotification(notificationEmail, "Batch Abortado",
                    String.format("Batch %s abortado tras %d reintentos fallidos.\nPortfolio: %s\nAcción: Revise los logs en /var/log/automatizacion.log.", batchId, maxRetries, batchPortfolio));
            return;
        }

        try {
            String waitLog = String.format("⏱️ Esperando %d minutos antes de reintentar el batch completo. Motivo: Error en intento %d/%d.", retryWaitMinutes, currentRetryCount, maxRetries);
            logger.info(waitLog);
            logService.sendLog(waitLog);
            for (int i = retryWaitMinutes; i > 0; i--) {
                String remainingLog = String.format("⏳ Tiempo restante para reintento: %d minutos.", i);
                logger.info(remainingLog);
                logService.sendLog(remainingLog);
                Thread.sleep(60 * 1000);
            }
            String retryLog = String.format("🔄 Reiniciando batch (intento %d/%d).", currentRetryCount, maxRetries);
            logger.info(retryLog);
            logService.sendLog(retryLog);
            processFullBatch();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            String interruptLog = String.format("⚠️ Interrupción durante espera para reintento. Motivo: %s. Acción: Revisar el estado del sistema.", ie.getMessage());
            logger.error(interruptLog);
            logService.sendLog(interruptLog);
            emailService.sendNotification(notificationEmail, "Interrupción en Batch",
                    String.format("Batch %s interrumpido durante espera de reintento: %s\nPortfolio: %s\nAcción: Reinicie el batch manualmente.", batchId, ie.getMessage(), batchPortfolio));
        } catch (Exception retryException) {
            handleBatchError(retryException);
        }
    }

    @Scheduled(cron = "0 */3 * * * ?")
    public void triggerBatch() {
        String log = String.format("⏰ Ejecución programada activada. Estado del batch: %s.", batchRunning.get() ? "Activo" : "Inactivo");
        logger.info(log);
        logService.sendLog(log);
        if (batchRunning.get()) {
            procesarBatchConReintentos();
        } else {
            String logInactive = String.format("ℹ️ Batch no activo. Acción: Inicie un nuevo batch mediante /api/cliente/batch.");
            logger.info(logInactive);
            logService.sendLog(logInactive);
        }
    }

    @Override
    public List<ClienteDTO> obtenerDatosDesdeAPI(int limit, int offset, String portfolio, String startDate, String endDate) {
        try {
            if (startDate == null || endDate == null) {
                String errorLog = String.format("❌ Parámetros inválidos. Motivo: start_date o end_date es nulo. Acción: Proporcione fechas válidas.");
                logger.error(errorLog);
                logService.sendLog(errorLog);
                throw new IllegalArgumentException("start_date y end_date son obligatorios");
            }

            LocalDateTime startDateTime = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
            LocalDateTime endDateTime = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);

            String formattedStartDate = startDateTime.format(DATE_TIME_FORMATTER);
            String formattedEndDate = endDateTime.plusSeconds(1).format(DATE_TIME_FORMATTER);

            String url = String.format("%s?limit=%d&offset=%d&portfolio=%s&start_date=%s&end_date=%s",
                    apiUrl, limit, offset, URLEncoder.encode(portfolio, StandardCharsets.UTF_8.name()),
                    formattedStartDate, formattedEndDate);

            String urlLog = String.format("🔗 URL API: %s.", url);
            logger.info(urlLog);
            logService.sendLog(urlLog);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Token " + apiToken);
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.set("Accept-Encoding", "gzip, deflate, br");
            headers.set("Connection", "keep-alive");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Cache-Control", "no-cache");

            String headersLog = String.format("🔧 Headers configurados correctamente.");
            logger.debug(headersLog);
            logService.sendLog(headersLog);

            String sendLog = String.format("🔄 Enviando solicitud a API...");
            logger.info(sendLog);
            logService.sendLog(sendLog);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<ResponseClienteWrapper> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ResponseClienteWrapper.class);

            String responseLog = String.format("✅ Respuesta recibida. Status Code: %s.", response.getStatusCode());
            logger.info(responseLog);
            logService.sendLog(responseLog);

            if (response.getBody() != null && response.getBody().getResults() != null) {
                List<ClienteDTO> results = response.getBody().getResults();
                String resultsLog = String.format("📊 Registros recibidos de API: %d.", results.size());
                logger.info(resultsLog);
                logService.sendLog(resultsLog);
             // Agregar log para inspeccionar idGestion
                String idGestionLog = results.stream()
                	    .map(dto -> "idGestion=" + dto.getIdGestion() + ", fechaTipificacion=" + dto.getFechaTipificacion())
                	    .collect(Collectors.joining(", ", "🔍 Registros recibidos: [", "]"));
                	logger.info(idGestionLog);
                	logService.sendLog(idGestionLog);

                results = results.stream()
                	    .filter(dto -> {
                	        boolean isValid = dto.getIdGestion() != null; // Solo validar idGestion
                	        if (!isValid) {
                	            String log = String.format("⚠️ Registro inválido: idGestion=%d. Motivo: idGestion nulo.", 
                	                    dto.getIdGestion());
                	            logger.warn(log);
                	            logService.sendLog(log);
                	        }
                	        return isValid;
                	    })
                	    .collect(Collectors.toList());

                String validLog = String.format("✅ Registros válidos: %d (%d%%). Motivo: Filtrado de registros con idGestion y fechaTipificacion válidos.", 
                        results.size(), response.getBody().getResults().size() > 0 ?
                                Math.round(results.size() * 100.0 / response.getBody().getResults().size()) : 0);
                logger.info(validLog);
                logService.sendLog(validLog);
                return results;
            } else {
                String warningLog = String.format("⚠️ Respuesta de API vacía. Motivo: No se recibieron datos. Acción: Verifique los parámetros de la API.");
                logger.warn(warningLog);
                logService.sendLog(warningLog);
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            String errorLog = String.format("❌ Error en la API. Status: %s. Motivo: %s. Acción: Verifique el token de API o la URL.", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            logger.error(errorLog);
            logService.sendLog(errorLog);
            throw new RuntimeException("Error en la API: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            String errorLog = String.format("❌ Error al consumir la API. Motivo: %s. Acción: Revise la conectividad de red o los parámetros de la solicitud.", 
                    e.getMessage());
            logger.error(errorLog);
            logService.sendLog(errorLog);
            throw new RuntimeException("No se pudo obtener datos de la API: " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosEnBD(List<ClienteDTO> datos) {
        if (datos == null || datos.isEmpty()) {
            String log = String.format("ℹ️ No hay datos para guardar. Motivo: Lista de datos vacía. Acción: Continuar con el siguiente intervalo.");
            logger.info(log);
            logService.sendLog(log);
            return;
        }

        String processLog = String.format("🔄 Procesando %d registros para guardar en BD...", datos.size());
        logger.info(processLog);
        logService.sendLog(processLog);

        AtomicInteger duplicatesCount = new AtomicInteger(0);
        AtomicInteger newRecordsCount = new AtomicInteger(0);

        List<Cliente> entidades = datos.stream()
        	    .filter(dto -> {
        	        boolean isValid = dto.getIdGestion() != null; // Solo validar idGestion
        	        if (!isValid) {
        	            String log = String.format("⚠️ Registro inválido: idGestion=%d. Motivo: idGestion nulo. Acción: Omitir registro.", 
        	                    dto.getIdGestion());
        	            logger.warn(log);
        	            logService.sendLog(log);
        	            return false;
        	        }
        	        
        	        List<Cliente> existing = repository.findAllByIdGestion(dto.getIdGestion());
        	        if (!existing.isEmpty()) {
        	            duplicatesCount.incrementAndGet();
        	            String log = String.format("⚠️ Registro duplicado: idGestion=%d. Motivo: Ya existe en la BD. Acción: Omitir registro.", 
        	                    dto.getIdGestion());
        	            logger.debug(log);
        	            logService.sendLog(log);
        	            return false;
        	        }
        	        return true;
        	    })
        	    .map(this::mapToEntity)
        	    .collect(Collectors.toList());

        totalDuplicates.addAndGet(duplicatesCount.get());
        newRecordsCount.set(entidades.size());
        totalNewRecordsInserted.addAndGet(newRecordsCount.get());

        String validationLog = String.format("📊 Resultado de validación: %d registros recibidos, %d duplicados, %d nuevos para insertar. Motivo: Verificación contra BD completada.", 
                datos.size(), duplicatesCount.get(), newRecordsCount.get());
        logger.info(validationLog);
        logService.sendLog(validationLog);

        if (entidades.isEmpty()) {
            String log = String.format("ℹ️ No hay registros válidos o nuevos para guardar. Motivo: Todos los registros son duplicados o inválidos. Acción: Continuar con el siguiente intervalo.");
            logger.info(log);
            logService.sendLog(log);
            return;
        }

        String insertLog = String.format("📊 Iniciando inserción de %d registros nuevos en la base de datos.", entidades.size());
        logger.info(insertLog);
        logService.sendLog(insertLog);

        int totalBatches = (int) Math.ceil(entidades.size() / (double) batchSize);

        for (int i = 0; i < entidades.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            List<Cliente> batch = entidades.subList(i, Math.min(i + batchSize, entidades.size()));

            String batchLog = String.format("🔄 Guardando lote %d/%d (%d registros)...", batchNum, totalBatches, batch.size());
            logger.info(batchLog);
            logService.sendLog(batchLog);

            try {
                repository.saveAll(batch);
                int progress = (int) ((batchNum * 100.0) / totalBatches);
                String progressLog = String.format("Progress: %d%%", progress);
                logger.info(progressLog);
                logService.sendLog(progressLog);
                String successLog = String.format("✅ Lote %d/%d guardado exitosamente: %d registros insertados. Motivo: Inserción completada sin errores.", 
                        batchNum, totalBatches, batch.size());
                logger.info(successLog);
                logService.sendLog(successLog);
            } catch (Exception e) {
                String errorLog = String.format("❌ Error al insertar lote %d/%d. Motivo: %s. Acción: Revisar la conexión a la BD o la integridad de los datos.", 
                        batchNum, totalBatches, e.getMessage());
                logger.error(errorLog);
                logService.sendLog(errorLog);
                throw new RuntimeException("Error al guardar datos en la BD: " + e.getMessage());
            }
        }

        String completeLog = String.format("✅ Inserción completada: %d nuevos registros guardados en la base de datos. Motivo: Todos los lotes procesados con éxito.", entidades.size());
        logger.info(completeLog);
        logService.sendLog(completeLog);
        logService.sendLog("Progress: 100%");
    }
    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            String log = String.format("⚠️ Fecha nula o vacía. Motivo: Entrada inválida. Acción: Retornar null.");
            logger.warn(log);
            logService.sendLog(log);
            return null;
        }
        try {
            return LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
            } catch (Exception e2) {
                String log = String.format("❌ Error parseando fecha: %s. Motivo: Formato no reconocido. Acción: Verifique el formato de fecha en los datos de entrada.", dateTime);
                logger.error(log);
                logService.sendLog(log);
                return null;
            }
        }
    }

    private Cliente mapToEntity(ClienteDTO dto) {
        Cliente cliente = new Cliente();
        cliente.setEmpresa(dto.getEmpresa());
        cliente.setCampana(dto.getCampana());
        cliente.setBase(dto.getBase());
        cliente.setEstrategia(dto.getEstrategia());
        cliente.setTipoCanal(dto.getTipoCanal());
        cliente.setTipoMarcacion(dto.getTipoMarcacion());
        cliente.setCodigoContacto(dto.getCodigoContacto());
        cliente.setContacto(dto.getContacto());
        cliente.setFechaCreacionContacto(parseDateTime(dto.getFechaCreacionContacto()));
        cliente.setFechaUltimaActualizacionContacto(parseDateTime(dto.getFechaUltimaActualizacionContacto()));
        cliente.setCodigoProducto(dto.getCodigoProducto());
        cliente.setTelefono(dto.getTelefono());
        cliente.setGrupo(dto.getGrupo());
        cliente.setCodigoGrupo(dto.getCodigoGrupo());
        cliente.setResultado(dto.getResultado());
        cliente.setCodigoResultado(dto.getCodigoResultado());
        cliente.setMotivo(dto.getMotivo());
        cliente.setCodigoMotivo(dto.getCodigoMotivo());
        cliente.setSubmotivo(dto.getSubmotivo());
        cliente.setCodigoSubmotivo(dto.getCodigoSubmotivo());
        cliente.setComentarioTipificacion(dto.getComentarioTipificacion());
        cliente.setResultadoAtk(dto.getResultadoAtk());
        cliente.setZipCode(dto.getZipCode());
        cliente.setFechaAsignacionTicket(parseDateTime(dto.getFechaAsignacionTicket()));
        cliente.setFechaTipificacion(parseDateTime(dto.getFechaTipificacion()));
        cliente.setFechaResolucionTicket(parseDateTime(dto.getFechaResolucionTicket()));
        cliente.setFechaPdp(dto.getFechaPdp());
        cliente.setMontoPdp(dto.getMontoPdp());
        cliente.setFechaAgenda(dto.getFechaAgenda());
        cliente.setIdUsuario(dto.getIdUsuario());
        cliente.setUsuarioGestion(dto.getUsuarioGestion());
        cliente.setAnexoUsuario(dto.getAnexoUsuario());
        cliente.setIdGestion(dto.getIdGestion());
        cliente.setIdLlamada(dto.getIdLlamada());
        cliente.setIdTicket(dto.getIdTicket());
        cliente.setFechaSincronizacion(parseDateTime(dto.getFechaSincronizacion()));
        return cliente;
    }

    @Override
    public void updateBatchConfig(Map<String, Object> config) {
        if (config.containsKey("batchSize")) {
            this.batchSize = Integer.parseInt(config.get("batchSize").toString());
            String log = String.format("✅ batchSize actualizado a %d. Motivo: Configuración dinámica aplicada.", this.batchSize);
            logger.info(log);
            logService.sendLog(log);
        }
        if (config.containsKey("maxRetries")) {
            this.maxRetries = Integer.parseInt(config.get("maxRetries").toString());
            String log = String.format("✅ maxRetries actualizado a %d. Motivo: Configuración dinámica aplicada.", this.maxRetries);
            logger.info(log);
            logService.sendLog(log);
        }
        if (config.containsKey("retryWaitMinutes")) {
            this.retryWaitMinutes = Integer.parseInt(config.get("retryWaitMinutes").toString());
            String log = String.format("✅ retryWaitMinutes actualizado a %d. Motivo: Configuración dinámica aplicada.", this.retryWaitMinutes);
            logger.info(log);
            logService.sendLog(log);
        }
        emailService.sendNotification(notificationEmail, "Configuración Actualizada",
                String.format("Configuración de batch actualizada:\nbatchSize=%d\nmaxRetries=%d\nretryWaitMinutes=%d",
                        batchSize, maxRetries, retryWaitMinutes));
    }

    @Override
    public Map<String, Object> getBatchConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("batchSize", batchSize);
        config.put("maxRetries", maxRetries);
        config.put("retryWaitMinutes", retryWaitMinutes);
        config.put("apiUrl", apiUrl);
        String log = String.format("🔍 Configuración obtenida: %s", config);
        logger.info(log);
        logService.sendLog(log);
        return config;
    }

    /*@Override
    public void clearTipificacionClientesTable() {
        String log = String.format("⚠️ Método clearTipificacionClientesTable invocado pero no ejecutado. Motivo: No se usa para evitar pérdida de datos. Acción: Use con precaución en entornos de prueba.");
        logger.warn(log);
        logService.sendLog(log);
    }*/
}