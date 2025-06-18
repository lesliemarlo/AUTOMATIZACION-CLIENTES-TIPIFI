package com.informaperu.cliente.service;


import java.util.List;
import java.util.Map;

import com.informaperu.cliente.model.ClienteDTO;

public interface ClienteService {
	 List<ClienteDTO> obtenerDatosDesdeAPI(int limit, int offset, String portfolio, String startDate, String endDate);
	    void guardarDatosEnBD(List<ClienteDTO> datos);
	    void setBatchParameters(String startDate, String endDate, int intervalDays, int limit, int offset, String portfolio, String notificationEmail);
	    void procesarBatchConReintentos();
	    void updateBatchConfig(Map<String, Object> config);
	    Map<String, Object> getBatchConfig();
}
