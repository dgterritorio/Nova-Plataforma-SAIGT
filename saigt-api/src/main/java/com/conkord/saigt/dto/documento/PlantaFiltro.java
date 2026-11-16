package com.conkord.saigt.dto.documento;

/** Filtros da lista de plantas do BackOffice; nulo não filtra. */
public record PlantaFiltro(String numeroProcesso, String numeroDeposito, Boolean emVigor) {}
