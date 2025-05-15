package com.informaperu.cliente.serviceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.informaperu.cliente.entity.Cliente;
import com.informaperu.cliente.model.ClienteDTO;
import com.informaperu.cliente.model.ResponseClienteWrapper;
import com.informaperu.cliente.repository.ClienteRepository;
import com.informaperu.cliente.service.ClienteService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements ClienteService {

    private static final Logger logger = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClienteRepository repository;
    private final RestTemplate restTemplate;

    @Value("${api.url}")
    private String apiUrl;

    @Value("${api.token}")
    private String apiToken;

    public ClienteServiceImpl(ClienteRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<ClienteDTO> obtenerDatosDesdeAPI(int limit, int offset, String portfolio, String startDate, String endDate) {
    	try {
            // Validar parámetros
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("start_date y end_date son obligatorios");
            }

            // Validar y formatear fechas
            LocalDateTime startDateTime = LocalDateTime.parse(startDate, DATE_TIME_FORMATTER);
            LocalDateTime endDateTime = LocalDateTime.parse(endDate, DATE_TIME_FORMATTER);
            
            // Formatear fechas exactamente como la API las espera
         // Formatear fechas exactamente como la API las espera
            String formattedStartDate = startDateTime.format(DATE_TIME_FORMATTER); // Keep space as-is
            String formattedEndDate = endDateTime.format(DATE_TIME_FORMATTER); // Keep space as-is
            
            String url = String.format("%s?limit=%d&offset=%d&portfolio=%s&start_date=%s&end_date=%s",
                    apiUrl,
                    limit,
                    offset,
                    URLEncoder.encode(portfolio, StandardCharsets.UTF_8.name()), // Encode portfolio as well
                    formattedStartDate,
                    formattedEndDate);
            logger.info("URL construida: {}", url);
            System.out.println("URL de la API: " + url);

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

            logger.info("Headers enviados: {}", headers);

            ResponseEntity<ResponseClienteWrapper> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ResponseClienteWrapper.class
            );

            logger.info("Status Code: {}", response.getStatusCode());
            logger.debug("Cuerpo de la respuesta: {}", response.getBody());

            if (response.getBody() != null && response.getBody().getResults() != null) {
                List<ClienteDTO> results = response.getBody().getResults();
                logger.info("Datos obtenidos de la API: {} registros", results.size());

                // Validar campos críticos
                results = results.stream()
                        .filter(dto -> {
                            boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                            if (!isValid) {
                                logger.warn("Registro descartado por campos nulos: idGestion={}, fechaTipificacion={}",
                                        dto.getIdGestion(), dto.getFechaTipificacion());
                            }
                            return isValid;
                        })
                        .collect(Collectors.toList());

                logger.info("Registros válidos después de filtrado: {}", results.size());
                return results;
            } else {
                logger.warn("La respuesta de la API está vacía");
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            logger.error("Error completo de la API:\nStatus: {}\nHeaders: {}\nBody: {}",
                    e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsString());
            throw new RuntimeException("Error en la API: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Error al consumir la API: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo obtener datos de la API: " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosEnBD(List<ClienteDTO> datos) {
        if (datos == null || datos.isEmpty()) {
            logger.info("No hay datos para guardar");
            return;
        }

        // Convertir DTO a entidad, solo validando campos críticos
        List<Cliente> entidades = datos.stream()
                .filter(dto -> {
                    boolean isValid = dto.getIdGestion() != null && dto.getFechaTipificacion() != null;
                    if (!isValid) {
                        logger.warn("Registro descartado por campos nulos: idGestion={}, fechaTipificacion={}",
                                dto.getIdGestion(), dto.getFechaTipificacion());
                    }
                    return isValid;
                })
                .map(this::mapToEntity)
                .collect(Collectors.toList());

        if (entidades.isEmpty()) {
            logger.info("No hay registros válidos para guardar después de la validación");
            return;
        }

        // Insertar por lotes (tamaño del lote: 1000)
        int batchSize = 1000;
        for (int i = 0; i < entidades.size(); i += batchSize) {
            List<Cliente> batch = entidades.subList(i, Math.min(i + batchSize, entidades.size()));
            try {
                repository.saveAll(batch);
                logger.info("Insertados {} registros en lote", batch.size());
            } catch (Exception e) {
                logger.error("Error al insertar lote de {} registros: {}", batch.size(), e.getMessage(), e);
                throw new RuntimeException("Error al guardar datos en la BD: " + e.getMessage());
            }
        }
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
                logger.error("Error parseando fecha: {}", dateTime, e2);
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