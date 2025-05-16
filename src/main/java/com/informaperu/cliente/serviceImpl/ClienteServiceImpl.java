package com.informaperu.cliente.serviceImpl;

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

import com.informaperu.cliente.entity.Cliente;
import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.model.ResponseClienteWrapper;
import com.informaperu.cliente.repository.ClienteRepository;
import com.informaperu.cliente.service.ClienteService;
import com.informaperu.cliente.config.ProgressBar;

import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements ClienteService {

    private static final Logger logger = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RETRY_WAIT_MINUTES = 5; // Tiempo de espera para reintentos (5 minutos)
    private static final int MAX_RETRIES = 10; // Número máximo de reintentos antes de abortar

    
    private final ClienteRepository repository;
    private final RestTemplate restTemplate;
    private final DataSource dataSource;

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.token}")
    private String apiToken;

    // Batch parameters
    private String batchStartDate;
    private String batchEndDate;
    private int batchIntervalDays;
    private int batchLimit;
    private int batchOffset;
    private String batchPortfolio;

    // Flag to control scheduled execution
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);
    private int currentRetryCount = 0;
    @Autowired
    public ClienteServiceImpl(ClienteRepository repository, RestTemplate restTemplate, DataSource dataSource) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void setBatchParameters(String startDate, String endDate, int intervalDays, int limit, int offset, String portfolio) {
        this.batchStartDate = startDate;
        this.batchEndDate = endDate;
        this.batchIntervalDays = intervalDays;
        this.batchLimit = limit;
        this.batchOffset = offset;
        this.batchPortfolio = portfolio;
        this.batchRunning.set(true); // Enable scheduled execution after initial trigger
        this.currentRetryCount = 0; // Reset retry counter on new parameters
        
        logger.info("╔══════════════════════════════════════════════════════════════════════╗");
        logger.info("║                      CONFIGURACIÓN DE BATCH                          ║");
        logger.info("╠══════════════════════════════════════════════════════════════════════╣");
        logger.info("║ Fecha inicio: {} ║", String.format("%-50s", startDate));
        logger.info("║ Fecha fin: {} ║", String.format("%-52s", endDate));
        logger.info("║ Intervalo días: {} ║", String.format("%-48s", intervalDays));
        logger.info("║ Límite: {} ║", String.format("%-54s", limit));
        logger.info("║ Offset: {} ║", String.format("%-54s", offset));
        logger.info("║ Portfolio: {} ║", String.format("%-52s", portfolio));
        logger.info("╚══════════════════════════════════════════════════════════════════════╝");
    }


    @Override
    @Transactional
    public void procesarBatchConReintentos() {
        if (!batchRunning.get()) {
            logger.info("⏸️  Batch no iniciado. Esperando trigger manual.");
            return;
        }

        logger.info("╔═══════════════════════════════════════════════════════════════════╗");
        logger.info("║                INICIANDO PROCESAMIENTO DE BATCH                   ║");
        logger.info("╠═══════════════════════════════════════════════════════════════════╣");
        logger.info("║ Periodo: {} a {} ║", 
                String.format("%-25s", batchStartDate), 
                String.format("%-25s", batchEndDate));
        logger.info("╚═══════════════════════════════════════════════════════════════════╝");

        try {
            processFullBatch();
        } catch (Exception e) {
            handleBatchError(e);
        }
    }
    
    /**
     * Método para procesar el batch completo con manejo de errores mejorado
     */
    private void processFullBatch() {
        try {
            // Limpiar tabla antes de procesar
            logger.info("🗑️  Limpiando tabla de tipificaciones...");
            clearTipificacionClientesTable();
            logger.info("✅ Tabla limpiada exitosamente");

            LocalDateTime start = LocalDateTime.parse(batchStartDate, DATE_TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(batchEndDate, DATE_TIME_FORMATTER);
            LocalDateTime currentStart = start;
            
            // Calcular el número total de intervalos para la barra de progreso
            long totalIntervals = 0;
            LocalDateTime tempStart = start;
            while (!tempStart.isAfter(end)) {
                totalIntervals++;
                tempStart = tempStart.plusDays(batchIntervalDays);
            }
            
            long currentInterval = 0;
            ProgressBar progressBar = new ProgressBar();

            while (!currentStart.isAfter(end)) {
                currentInterval++;
                
                LocalDateTime currentEnd = currentStart.plusDays(batchIntervalDays).minusSeconds(1);
                if (currentEnd.isAfter(end)) {
                    currentEnd = end;
                }

                String formattedStart = currentStart.format(DATE_TIME_FORMATTER);
                String formattedEnd = currentEnd.format(DATE_TIME_FORMATTER);

                logger.info("📅 Procesando intervalo {} de {}: {} a {}", 
                    currentInterval, totalIntervals, formattedStart, formattedEnd);
                
                progressBar.update((int)(currentInterval * 100 / totalIntervals));

                try {
                    procesarIntervalo(formattedStart, formattedEnd);
                } catch (Exception e) {
                    // Si hay un error en el intervalo, lanzamos la excepción para que se maneje en el nivel superior
                    logger.error("❌ Error al procesar intervalo {} a {}: {}", 
                        formattedStart, formattedEnd, e.getMessage());
                    throw e;
                }

                currentStart = currentEnd.plusSeconds(1);
            }

            progressBar.update(100);
            
            // Reset batchRunning if end date is reached
            if (currentStart.isAfter(end)) {
                currentRetryCount = 0; // Reset retry counter on successful completion
                batchRunning.set(false);
                logger.info("╔═══════════════════════════════════════════════════════════════════╗");
                logger.info("║                      BATCH COMPLETADO                             ║");
                logger.info("╠═══════════════════════════════════════════════════════════════════╣");
                logger.info("║ Periodo completado: {} a {} ║", 
                        String.format("%-20s", batchStartDate), 
                        String.format("%-20s", batchEndDate));
                logger.info("╚═══════════════════════════════════════════════════════════════════╝");
            }
        } catch (Exception e) {
            // Si hay un error, se propagará para ser manejado por el método que llamó a este
            throw new RuntimeException("Error al procesar batch completo: " + e.getMessage(), e);
        }
    }
    
    /**
     * Método para procesar un intervalo específico
     */
    private void procesarIntervalo(String formattedStart, String formattedEnd) {
        try {
            logger.info("🔄 Obteniendo datos de la API para el intervalo...");
            List<ClienteDTO> datos = obtenerDatosDesdeAPI(batchLimit, batchOffset, batchPortfolio, formattedStart, formattedEnd);
            logger.info("✅ Datos obtenidos correctamente: {} registros", datos.size());
            
            logger.info("💾 Guardando datos en la base de datos...");
            guardarDatosEnBD(datos);
            logger.info("✅ Datos guardados correctamente para el intervalo {} a {}", formattedStart, formattedEnd);
        } catch (Exception e) {
            // Relanzamos la excepción para que sea manejada en el nivel superior
            logger.error("❌ Error al procesar intervalo: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Método para manejar errores del batch con reintentos automáticos
     */
    private void handleBatchError(Exception e) {
        currentRetryCount++;
        
        if (currentRetryCount > MAX_RETRIES) {
            logger.error("❌ Se ha alcanzado el número máximo de reintentos ({}). Abortando proceso batch.", MAX_RETRIES);
            batchRunning.set(false);
            return;
        }
        
        logger.error("❌ Error en el proceso batch (intento {}/{}): {}", 
            currentRetryCount, MAX_RETRIES, e.getMessage(), e);
        
        try {
            // Muestra el temporizador de cuenta regresiva
            logger.info("⏱️ Esperando {} minutos antes de reintentar el proceso completo...", RETRY_WAIT_MINUTES);
            
            for (int i = RETRY_WAIT_MINUTES; i > 0; i--) {
                logger.info("⏳ Tiempo restante para reintento: {} minutos", i);
                Thread.sleep(60 * 1000); // Esperar 1 minuto
            }
            
            logger.info("🔄 Reiniciando proceso batch desde el principio (intento {}/{})", 
                currentRetryCount, MAX_RETRIES);
            
            // Intentamos procesar el batch nuevamente
            processFullBatch();
            
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("⚠️ Interrupción durante la espera para reintento: {}", ie.getMessage());
        } catch (Exception retryException) {
            // Si hay un error durante el reintento, lo manejamos recursivamente
            handleBatchError(retryException);
        }
    }


    @Scheduled(cron = "0 */3 * * * ?") // Ejecutar cada 3 minutos para pruebas
    public void triggerBatch() {
        logger.info("⏰ Ejecución programada activada");
        if (batchRunning.get()) {
            procesarBatchConReintentos();
        } else {
            logger.info("ℹ️ Batch no está activo. Esperando inicialización manual.");
        }
    }


    public void clearTipificacionClientesTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE [InformaPeru].[datos].[tipificaciones_por_cliente]");
            logger.info("✅ Tabla tipificaciones_por_cliente truncada exitosamente");
        } catch (SQLException e) {
            logger.error("❌ Error al truncar la tabla tipificaciones_por_cliente: {}", e.getMessage(), e);
            throw new RuntimeException("Error al truncar la tabla: " + e.getMessage());
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
                    apiUrl,
                    limit,
                    offset,
                    URLEncoder.encode(portfolio, StandardCharsets.UTF_8.name()),
                    formattedStartDate,
                    formattedEndDate);
            
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
                    url,
                    HttpMethod.GET,
                    entity,
                    ResponseClienteWrapper.class
            );

            logger.info("✅ Respuesta recibida. Status Code: {}", response.getStatusCode());

            if (response.getBody() != null && response.getBody().getResults() != null) {
                List<ClienteDTO> results = response.getBody().getResults();
                logger.info("📊 Registros recibidos de API: {}", results.size());

                results = results.stream()
                        .filter(dto -> {
                            boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                            if (!isValid) {
                                logger.debug("⚠️  Registro inválido: idGestion={}, fechaTipificacion={}",
                                        dto.getIdGestion(), dto.getFechaTipificacion());
                            }
                            return isValid;
                        })
                        .collect(Collectors.toList());

                logger.info("✅ Registros válidos: {} ({}%)", 
                    results.size(), 
                    response.getBody().getResults().size() > 0 ? 
                        Math.round(results.size() * 100.0 / response.getBody().getResults().size()) : 0);
                return results;
            } else {
                logger.warn("⚠️  La respuesta de la API está vacía");
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            logger.error("❌ Error en la API:\n Status: {}\n Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error en la API: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("❌ Error al consumir la API: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo obtener datos de la API: " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosEnBD(List<ClienteDTO> datos) {
        if (datos == null || datos.isEmpty()) {
            logger.info("ℹ️  No hay datos para guardar");
            return;
        }

        logger.info("🔄 Procesando {} registros para guardar en BD...", datos.size());

        List<Cliente> entidades = datos.stream()
                .filter(dto -> {
                    boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                    if (!isValid) {
                        logger.debug("⚠️  Registro inválido: idGestion={}, fechaTipificacion={}",
                                dto.getIdGestion(), dto.getFechaTipificacion());
                    }
                    return isValid;
                })
                .map(this::mapToEntity)
                .collect(Collectors.toList());

        if (entidades.isEmpty()) {
            logger.info("ℹ️  No hay registros válidos para guardar después de la validación");
            return;
        }

        logger.info("📊 Guardando {} registros en la base de datos", entidades.size());
        
        int batchSize = 1000;
        int totalBatches = (int) Math.ceil(entidades.size() / (double) batchSize);
        ProgressBar progressBar = new ProgressBar();
        
        for (int i = 0; i < entidades.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            List<Cliente> batch = entidades.subList(i, Math.min(i + batchSize, entidades.size()));
            
            logger.info("🔄 Guardando lote {}/{} ({} registros)...", 
                batchNum, totalBatches, batch.size());
            
            try {
                repository.saveAll(batch);
                int progress = (int)((batchNum * 100.0) / totalBatches);
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
}