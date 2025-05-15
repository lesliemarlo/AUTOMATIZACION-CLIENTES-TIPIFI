package com.informaperu.cliente.service;


import java.util.List;

import com.informaperu.cliente.model.ClienteDTO;

public interface ClienteService {
	List<ClienteDTO> obtenerDatosDesdeAPI(int limit, int offset, String portfolio, String startDate, String endDate);
    void guardarDatosEnBD(List<ClienteDTO> datos);
}
