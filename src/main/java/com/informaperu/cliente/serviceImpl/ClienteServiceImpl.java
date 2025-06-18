package com.informaperu.cliente.serviceImpl;

import com.informaperu.cliente.config.ProgressBar;
import com.informaperu.cliente.entity.BatchState;
import com.informaperu.cliente.entity.Cliente;
import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.model.ResponseClienteWrapper;
import com.informaperu.cliente.repository.BatchStateRepository;
import com.informaperu.cliente.repository.ClienteRepository;
import com.informaperu.cliente.service.ClienteService;
import com.informaperu.cliente.service.EmailService;
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
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements ClienteService {

    private static final Logger logger = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClienteRepository repository;
    private final BatchStateRepository batchStateRepository;
    private final RestTemplate restTemplate;
    private final EmailService emailService;

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

    @Value("${batch.notification.email}")
    private String defaultNotificationEmail;

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

    @Autowired
    public ClienteServiceImpl(ClienteRepository repository, BatchStateRepository batchStateRepository,
                              RestTemplate restTemplate, EmailService emailService) {
        this.repository = repository;
        this.batchStateRepository = batchStateRepository;
        this.restTemplate = restTemplate;
        this.emailService = emailService;
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
        this.notificationEmail = notificationEmail != null && !notificationEmail.isEmpty() ? notificationEmail : defaultNotificationEmail;
        this.batchId = UUID.randomUUID().toString();
        this.batchRunning.set(true);
        this.currentRetryCount = 0;

        // Initialize batch state
        BatchState state = new BatchState();
        state.setBatchId(batchId);
        state.setCompleted(false);
        batchStateRepository.save(state);

        logger.info("╔══════════════════════════════════════════════════════════════════════╗");
        logger.info("║                      CONFIGURACIÓN DE BATCH                          ║");
        logger.info("╠══════════════════════════════════════════════════════════════════════╣");
        logger.info("║ Batch ID: {} ║", String.format("%-50s", batchId));
        logger.info("║ Fecha inicio: {} ║", String.format("%-50s", startDate));
        logger.info("║ Fecha fin: {} ║", String.format("%-52s", endDate));
        logger.info("║ Intervalo días: {} ║", String.format("%-48s", intervalDays));
        logger.info("║ Límite: {} ║", String.format("%-54s", limit));
        logger.info("║ Offset: {} ║", String.format("%-54s", offset));
        logger.info("║ Portfolio: {} ║", String.format("%-52s", portfolio));
        logger.info("║ Correo notificaciones: {} ║", String.format("%-40s", this.notificationEmail));
        logger.info("╚══════════════════════════════════════════════════════════════════════╝");

        emailService.sendNotification(this.notificationEmail, "Batch Configurado",
                String.format("Batch %s configurado con éxito.\nInicio: %s\nFin: %s\nIntervalo: %d días",
                        batchId, startDate, endDate, intervalDays));
    }

    @Override
    @Transactional
    public void procesarBatchConReintentos() {
        if (!batchRunning.get()) {
            logger.info("⏸️ Batch no iniciado. Esperando trigger manual.");
            return;
        }

        logger.info("╔═══════════════════════════════════════════════════════════════════╗");
        logger.info("║                INICIANDO PROCESAMIENTO DE BATCH                   ║");
        logger.info("╠═══════════════════════════════════════════════════════════════════╣");
        logger.info("║ Batch ID: {} ║", String.format("%-50s", batchId));
        logger.info("║ Periodo: {} a {} ║", String.format("%-25s", batchStartDate), String.format("%-25s", batchEndDate));
        logger.info("╚═══════════════════════════════════════════════════════════════════╝");

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
                logger.info("🔄 Reanudando desde el último intervalo procesado: {}", currentStart);
            } else {
                currentStart = start;
            }

            // Calculate total intervals for progress
            long totalIntervals = 0;
            LocalDateTime tempStart = start;
            while (!tempStart.isAfter(end)) {
                totalIntervals++;
                tempStart = tempStart.plusDays(batchIntervalDays);
            }

            long currentInterval = 0;
            ProgressBar progressBar = new ProgressBar();
            long intervalsProcessed = 0;
            LocalDateTime tempCurrent = start;
            while (!tempCurrent.isAfter(currentStart)) {
                intervalsProcessed++;
                tempCurrent = tempCurrent.plusDays(batchIntervalDays);
            }
            currentInterval = intervalsProcessed - 1;

            while (!currentStart.isAfter(end)) {
                currentInterval++;

                LocalDateTime currentEnd = currentStart.plusDays(batchIntervalDays).minusSeconds(1);
                if (currentEnd.isAfter(end)) {
                    currentEnd = end;
                }

                String formattedStart = currentStart.format(DATE_TIME_FORMATTER);
                String formattedEnd = currentEnd.format(DATE_TIME_FORMATTER);

                logger.info("📅 Procesando intervalo {} de {}: {} a {}", currentInterval, totalIntervals, formattedStart, formattedEnd);
                progressBar.update((int) (currentInterval * 100 / totalIntervals));

                try {
                    procesarIntervalo(formattedStart, formattedEnd);

                    // Update batch state
                    state.setLastProcessedStart(currentStart);
                    state.setLastProcessedEnd(currentEnd);
                    state.setBatchId(batchId);
                    batchStateRepository.save(state);

                    emailService.sendNotification(notificationEmail, "Intervalo Procesado",
                            String.format("Batch %s: Intervalo %d/%d procesado.\nDesde: %s\nHasta: %s",
                                    batchId, currentInterval, totalIntervals, formattedStart, formattedEnd));
                } catch (Exception e) {
                    logger.error("❌ Error al procesar intervalo {} a {}: {}", formattedStart, formattedEnd, e.getMessage());
                    throw e;
                }

                currentStart = currentEnd.plusSeconds(1);
            }

            progressBar.update(100);

            // Mark batch as completed
            state.setCompleted(true);
            batchStateRepository.save(state);
            batchRunning.set(false);
            currentRetryCount = 0;

            logger.info("╔═══════════════════════════════════════════════════════════════════╗");
            logger.info("║                      BATCH COMPLETADO                             ║");
            logger.info("╠═══════════════════════════════════════════════════════════════════╣");
            logger.info("║ Batch ID: {} ║", String.format("%-50s", batchId));
            logger.info("║ Periodo completado: {} a {} ║", String.format("%-20s", batchStartDate), String.format("%-20s", batchEndDate));
            logger.info("╚═══════════════════════════════════════════════════════════════════╝");

            emailService.sendNotification(notificationEmail, "Batch Completado",
                    String.format("Batch %s completado con éxito.\nPeriodo: %s a %s", batchId, batchStartDate, batchEndDate));
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar batch completo: " + e.getMessage(), e);
        }
    }

    private void procesarIntervalo(String formattedStart, String formattedEnd) {
        try {
            logger.info("🔄 Obteniendo datos de la API para el intervalo...");
            List<ClienteDTO> datos = obtenerDatosDesdeAPI(batchLimit, batchOffset, batchPortfolio, formattedStart, formattedEnd);
            logger.info("✅ Datos obtenidos correctamente: {} registros", datos.size());

            logger.info("💾 Guardando datos en la base de datos...");
            guardarDatosEnBD(datos);
            logger.info("✅ Datos guardados correctamente para el intervalo {} a {}", formattedStart, formattedEnd);
        } catch (Exception e) {
            logger.error("❌ Error al procesar intervalo: {}", e.getMessage());
            throw e;
        }
    }

    private void handleBatchError(Exception e) {
        currentRetryCount++;

        emailService.sendNotification(notificationEmail, "Error en Batch",
                String.format("Batch %s: Error en intento %d/%d.\nMensaje: %s", batchId, currentRetryCount, maxRetries, e.getMessage()));

        if (currentRetryCount > maxRetries) {
            logger.error("❌ Se ha alcanzado el número máximo de reintentos ({}). Abortando proceso batch.", maxRetries);
            batchRunning.set(false);
            emailService.sendNotification(notificationEmail, "Batch Abortado",
                    String.format("Batch %s abortado tras %d reintentos fallidos.", batchId, maxRetries));
            return;
        }

        logger.error("❌ Error en el proceso batch (intento {}/{}): {}", currentRetryCount, maxRetries, e.getMessage(), e);

        try {
            logger.info("⏱️ Esperando {} minutos antes de reintentar el proceso completo...", retryWaitMinutes);
            for (int i = retryWaitMinutes; i > 0; i--) {
                logger.info("⏳ Tiempo restante para reintento: {} minutos", i);
                Thread.sleep(60 * 1000);
            }
            logger.info("🔄 Reiniciando proceso batch (intento {}/{})", currentRetryCount, maxRetries);
            processFullBatch();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("⚠️ Interrupción durante la espera para reintento: {}", ie.getMessage());
            emailService.sendNotification(notificationEmail, "Interrupción en Batch",
                    String.format("Batch %s interrumpido durante espera de reintento: %s", batchId, ie.getMessage()));
        } catch (Exception retryException) {
            handleBatchError(retryException);
        }
    }

    @Scheduled(cron = "0 */3 * * * ?")
    public void triggerBatch() {
        logger.info("⏰ Ejecución programada activada");
        if (batchRunning.get()) {
            procesarBatchConReintentos();
        } else {
            logger.info("ℹ️ Batch no está activo. Esperando inicialización manual.");
        }
    }

    @Override
    public List<ClienteDTO> obtenerDatosDesdeAPI(int limit, int offset, String portfolio, String startDate, String endDate) {
        try {
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("start_date y end_date son obligatorios");
            }

            LocalDateTime startDateTime = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
            LocalDateTime endDateTime = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);

            String formattedStartDate = startDateTime.format(DATE_TIME_FORMATTER);
            String formattedEndDate = endDateTime.format(DATE_TIME_FORMATTER);

            String url = String.format("%s?limit=%d&offset=%d&portfolio=%s&start_date=%s&end_date=%s",
                    apiUrl, limit, offset, URLEncoder.encode(portfolio, StandardCharsets.UTF_8.name()),
                    formattedStartDate, formattedEndDate);

            logger.info("🔗 URL API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Token " + apiToken);
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.set("Accept-Encoding", "gzip, deflate, br");
            headers.set("Connection", "keep-alive");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Cache-Control", "no-cache");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            logger.debug("🔧 Headers configurados correctamente");

            logger.info("🔄 Enviando solicitud a API...");
            ResponseEntity<ResponseClienteWrapper> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ResponseClienteWrapper.class);

            logger.info("✅ Respuesta recibida. Status Code: {}", response.getStatusCode());

            if (response.getBody() != null && response.getBody().getResults() != null) {
                List<ClienteDTO> results = response.getBody().getResults();
                logger.info("📊 Registros recibidos de API: {}", results.size());

                results = results.stream()
                        .filter(dto -> {
                            boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                            if (!isValid) {
                                logger.debug("⚠️ Registro inválido: idGestion={}, fechaTipificacion={}",
                                        dto.getIdGestion(), dto.getFechaTipificacion());
                            }
                            return isValid;
                        })
                        .collect(Collectors.toList());

                logger.info("✅ Registros válidos: {} ({}%)", results.size(),
                        response.getBody().getResults().size() > 0 ?
                                Math.round(results.size() * 100.0 / response.getBody().getResults().size()) : 0);
                return results;
            } else {
                logger.warn("⚠️ La respuesta de la API está vacía");
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            logger.error("❌ Error en la API:\n Status: {}\n Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error en la API: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("❌ Error al consumir la API: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo obtener datos de la API: " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosEnBD(List<ClienteDTO> datos) {
        if (datos == null || datos.isEmpty()) {
            logger.info("ℹ️ No hay datos para guardar");
            return;
        }

        logger.info("🔄 Procesando {} registros para guardar en BD...", datos.size());

        List<Cliente> entidades = datos.stream()
                .filter(dto -> {
                    boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                    if (!isValid) {
                        logger.debug("⚠️ Registro inválido: idGestion={}, fechaTipificacion={}",
                                dto.getIdGestion(), dto.getFechaTipificacion());
                        return false;
                    }
                    LocalDateTime fechaTipificacion = parseDateTime(dto.getFechaTipificacion());
                    Optional<Cliente> existing = repository.findByIdGestionAndFechaTipificacion(dto.getIdGestion(), fechaTipificacion);
                    if (existing.isPresent()) {
                        logger.debug("⚠️ Registro duplicado: idGestion={}, fechaTipificacion={}",
                                dto.getIdGestion(), dto.getFechaTipificacion());
                        return false;
                    }
                    return true;
                })
                .map(this::mapToEntity)
                .collect(Collectors.toList());

        if (entidades.isEmpty()) {
            logger.info("ℹ️ No hay registros válidos o no duplicados para guardar");
            return;
        }

        logger.info("📊 Guardando {} registros en la base de datos", entidades.size());

        int totalBatches = (int) Math.ceil(entidades.size() / (double) batchSize);
        ProgressBar progressBar = new ProgressBar();

        for (int i = 0; i < entidades.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            List<Cliente> batch = entidades.subList(i, Math.min(i + batchSize, entidades.size()));

            logger.info("🔄 Guardando lote {}/{} ({} registros)...", batchNum, totalBatches, batch.size());

            try {
                repository.saveAll(batch);
                int progress = (int) ((batchNum * 100.0) / totalBatches);
                progressBar.update(progress);
                logger.info("✅ Lote {}/{} guardado exitosamente", batchNum, totalBatches);
            } catch (Exception e) {
                logger.error("❌ Error al insertar lote {}/{}: {}", batchNum, totalBatches, e.getMessage());
                throw new RuntimeException("Error al guardar datos en la BD: " + e.getMessage());
            }
        }

        progressBar.update(100);
        logger.info("✅ Todos los datos guardados exitosamente en la base de datos");
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
            } catch (Exception e2) {
                logger.error("❌ Error parseando fecha: {}", dateTime);
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
            logger.info("✅ batchSize actualizado a {}", this.batchSize);
        }
        if (config.containsKey("maxRetries")) {
            this.maxRetries = Integer.parseInt(config.get("maxRetries").toString());
            logger.info("✅ maxRetries actualizado a {}", this.maxRetries);
        }
        if (config.containsKey("retryWaitMinutes")) {
            this.retryWaitMinutes = Integer.parseInt(config.get("retryWaitMinutes").toString());
            logger.info("✅ retryWaitMinutes actualizado a {}", this.retryWaitMinutes);
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
        return config;
    }

    /*@Override
    public void clearTipificacionClientesTable() {
        // Método conservado pero no utilizado
        logger.warn("⚠️ Método clearTipificacionClientesTable invocado pero no ejecutado.");
    }*/
}