package com.informaperu.cliente.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClienteDTO {
	@JsonProperty("id")
    private Long id;
    @JsonProperty("empresa")
    private String empresa;
    @JsonProperty("campaña")
    private String campana;
    @JsonProperty("base")
    private String base;
    @JsonProperty("estrategia")
    private String estrategia;
    @JsonProperty("tipo_canal")
    private String tipoCanal;
    @JsonProperty("tipo_marcacion")
    private String tipoMarcacion;
    @JsonProperty("codigo_contacto")
    private String codigoContacto;
    @JsonProperty("contacto")
    private String contacto;
    @JsonProperty("fecha_de_creacion_del_contacto")
    private String fechaCreacionContacto;
    @JsonProperty("fecha_ultima_de_actualizacion_del_contacto")
    private String fechaUltimaActualizacionContacto;
    @JsonProperty("codigo_producto")
    private String codigoProducto;
    @JsonProperty("telefono_de_contacto")
    private String telefono;
    @JsonProperty("grupo")
    private String grupo;
    @JsonProperty("codigo_de_grupo")
    private String codigoGrupo;
    @JsonProperty("resultado")
    private String resultado;
    @JsonProperty("codigo_de_resultado")
    private String codigoResultado;
    @JsonProperty("motivo")
    private String motivo;
    @JsonProperty("codigo_de_motivo")
    private String codigoMotivo;
    @JsonProperty("submotivo")
    private String submotivo;
    @JsonProperty("codigo_de_submotivo")
    private String codigoSubmotivo;
    @JsonProperty("comentario_en_tipificacion")
    private String comentarioTipificacion;
    @JsonProperty("resultado_atk")
    private String resultadoAtk;
    @JsonProperty("zip_code")
    private String zipCode;
    @JsonProperty("fecha_de_asignacion_de_ticket")
    private String fechaAsignacionTicket;
    @JsonProperty("fecha_de_tipificacion")
    private String fechaTipificacion;
    @JsonProperty("fecha_de_resolucion_de_ticket")
    private String fechaResolucionTicket;
    @JsonProperty("fecha_pdp")
    private String fechaPdp;
    @JsonProperty("monto_pdp")
    private Double montoPdp;
    @JsonProperty("fecha_agenda")
    private String fechaAgenda;
    @JsonProperty("id_usuario")
    private Integer idUsuario;
    @JsonProperty("usuario_gestion")
    private String usuarioGestion;
    @JsonProperty("anexo")
    private String anexoUsuario;
    @JsonProperty("id_gestion")
    private Integer idGestion;
    @JsonProperty("id_llamada")
    private String idLlamada;
    @JsonProperty("id_ticket")
    private Integer idTicket;
    @JsonProperty("fecha_sincronizacion")
    private String fechaSincronizacion;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getCampana() { return campana; }
    public void setCampana(String campana) { this.campana = campana; }
    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }
    public String getEstrategia() { return estrategia; }
    public void setEstrategia(String estrategia) { this.estrategia = estrategia; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getSubmotivo() { return submotivo; }
    public void setSubmotivo(String submotivo) { this.submotivo = submotivo; }
    public String getTipoCanal() { return tipoCanal; }
    public void setTipoCanal(String tipoCanal) { this.tipoCanal = tipoCanal; }
    public String getTipoMarcacion() { return tipoMarcacion; }
    public void setTipoMarcacion(String tipoMarcacion) { this.tipoMarcacion = tipoMarcacion; }
    public String getCodigoContacto() { return codigoContacto; }
    public void setCodigoContacto(String codigoContacto) { this.codigoContacto = codigoContacto; }
    public String getFechaCreacionContacto() { return fechaCreacionContacto; }
    public void setFechaCreacionContacto(String fechaCreacionContacto) { this.fechaCreacionContacto = fechaCreacionContacto; }
    public String getFechaUltimaActualizacionContacto() { return fechaUltimaActualizacionContacto; }
    public void setFechaUltimaActualizacionContacto(String fechaUltimaActualizacionContacto) { this.fechaUltimaActualizacionContacto = fechaUltimaActualizacionContacto; }
    public String getCodigoProducto() { return codigoProducto; }
    public void setCodigoProducto(String codigoProducto) { this.codigoProducto = codigoProducto; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCodigoGrupo() { return codigoGrupo; }
    public void setCodigoGrupo(String codigoGrupo) { this.codigoGrupo = codigoGrupo; }
    public String getCodigoResultado() { return codigoResultado; }
    public void setCodigoResultado(String codigoResultado) { this.codigoResultado = codigoResultado; }
    public String getCodigoMotivo() { return codigoMotivo; }
    public void setCodigoMotivo(String codigoMotivo) { this.codigoMotivo = codigoMotivo; }
    public String getCodigoSubmotivo() { return codigoSubmotivo; }
    public void setCodigoSubmotivo(String codigoSubmotivo) { this.codigoSubmotivo = codigoSubmotivo; }
    public String getComentarioTipificacion() { return comentarioTipificacion; }
    public void setComentarioTipificacion(String comentarioTipificacion) { this.comentarioTipificacion = comentarioTipificacion; }
    public String getResultadoAtk() { return resultadoAtk; }
    public void setResultadoAtk(String resultadoAtk) { this.resultadoAtk = resultadoAtk; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getFechaAsignacionTicket() { return fechaAsignacionTicket; }
    public void setFechaAsignacionTicket(String fechaAsignacionTicket) { this.fechaAsignacionTicket = fechaAsignacionTicket; }
    public String getFechaTipificacion() { return fechaTipificacion; }
    public void setFechaTipificacion(String fechaTipificacion) { this.fechaTipificacion = fechaTipificacion; }
    public String getFechaResolucionTicket() { return fechaResolucionTicket; }
    public void setFechaResolucionTicket(String fechaResolucionTicket) { this.fechaResolucionTicket = fechaResolucionTicket; }
    public String getFechaPdp() { return fechaPdp; }
    public void setFechaPdp(String fechaPdp) { this.fechaPdp = fechaPdp; }
    public Double getMontoPdp() { return montoPdp; }
    public void setMontoPdp(Double montoPdp) { this.montoPdp = montoPdp; }
    public String getFechaAgenda() { return fechaAgenda; }
    public void setFechaAgenda(String fechaAgenda) { this.fechaAgenda = fechaAgenda; }
    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getUsuarioGestion() { return usuarioGestion; }
    public void setUsuarioGestion(String usuarioGestion) { this.usuarioGestion = usuarioGestion; }
    public String getAnexoUsuario() { return anexoUsuario; }
    public void setAnexoUsuario(String anexoUsuario) { this.anexoUsuario = anexoUsuario; }
    public Integer getIdGestion() { return idGestion; }
    public void setIdGestion(Integer idGestion) { this.idGestion = idGestion; }
    public String getIdLlamada() { return idLlamada; }
    public void setIdLlamada(String idLlamada) { this.idLlamada = idLlamada; }
    public Integer getIdTicket() { return idTicket; }
    public void setIdTicket(Integer idTicket) { this.idTicket = idTicket; }
    public String getFechaSincronizacion() { return fechaSincronizacion; }
    public void setFechaSincronizacion(String fechaSincronizacion) { this.fechaSincronizacion = fechaSincronizacion; }
}