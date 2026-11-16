package com.conkord.saigt.controller.documento;

import com.conkord.saigt.dto.documento.GravarPlantasRequest;
import com.conkord.saigt.dto.documento.MetadadosProcesso;
import com.conkord.saigt.dto.documento.MetadadosResponse;
import com.conkord.saigt.dto.documento.PlantasResponse;
import com.conkord.saigt.service.documento.MetadadosService;
import com.conkord.saigt.service.documento.PlantasService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Páginas das Plantas e dos Metadados de um processo sem GeoConform; os ficheiros sobem pelo upload dos documentos. */
@RestController
public class PlantasMetadadosController {

    private final PlantasService plantasService;
    private final MetadadosService metadadosService;

    public PlantasMetadadosController(PlantasService plantasService, MetadadosService metadadosService) {
        this.plantasService = plantasService;
        this.metadadosService = metadadosService;
    }

    @GetMapping("/api/v1/processos/{id}/plantas")
    public PlantasResponse obterPlantas(@PathVariable UUID id) {
        return plantasService.obter(id);
    }

    @PutMapping("/api/v1/processos/{id}/plantas")
    public PlantasResponse gravarPlantas(@PathVariable UUID id, @Valid @RequestBody GravarPlantasRequest request) {
        return plantasService.gravar(id, request);
    }

    @GetMapping("/api/v1/processos/{id}/plantas/cartograma.pdf")
    public ResponseEntity<byte[]> cartogramaPdf(@PathVariable UUID id) {
        PlantasService.PdfCartograma pdf = plantasService.cartogramaPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(pdf.nomeFicheiro(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(pdf.conteudo());
    }

    @GetMapping("/api/v1/processos/{id}/metadados")
    public MetadadosResponse obterMetadados(@PathVariable UUID id) {
        return metadadosService.obter(id);
    }

    @PutMapping("/api/v1/processos/{id}/metadados")
    public MetadadosResponse gravarMetadados(@PathVariable UUID id, @Valid @RequestBody MetadadosProcesso request) {
        return metadadosService.gravar(id, request);
    }
}
