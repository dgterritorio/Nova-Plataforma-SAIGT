import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import {
  AtualizarDocumentoConfigPedido,
  CriarDocumentoConfigPedido,
  DinamicaConfig,
  DinamicaConfigRequest,
  DocumentoConfig,
  FiltroDinamicasConfig,
  FiltroDocumentoConfig,
  FiltroPermissoes,
  FiltroProcedimentosConfig,
  FiltroRegras,
  FiltroTipos,
  PermissaoConfig,
  PermissaoConfigRequest,
  ProcedimentoConfig,
  ProcedimentoConfigRequest,
  RedeDefesaConfig,
  RedeDefesaConfigRequest,
  RegraConfig,
  RegraConfigRequest,
  TipoAtoConfig,
  TipoAtoConfigRequest,
  TipoDinamicaConfig,
  TipoDinamicaConfigRequest,
  TipoInstrumentoConfig,
  TipoInstrumentoConfigRequest,
  TipoProcedimentoConfig,
  TipoProcedimentoConfigRequest,
} from '../models/configuracao.model';

const BASE = '/api/v1/configuracao';
const BASE_DOCUMENTO_CONFIG = '/api/v1/documento-config';

/** Configuração em backoffice dos tipos, Dinâmicas, Procedimentos, permissões, regras do motor, itens documentais, tipos de ato e redes de defesa (escrita só MASTER). */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoService {
  private readonly http = inject(HttpClient);

  listarTiposInstrumento(filtro: FiltroTipos, page: number, size: number): Observable<PageResponse<TipoInstrumentoConfig>> {
    return this.listar('tipos-instrumento', filtro, page, size);
  }

  criarTipoInstrumento(pedido: TipoInstrumentoConfigRequest): Observable<TipoInstrumentoConfig> {
    return this.http.post<TipoInstrumentoConfig>(`${BASE}/tipos-instrumento`, pedido);
  }

  atualizarTipoInstrumento(id: number, pedido: TipoInstrumentoConfigRequest): Observable<TipoInstrumentoConfig> {
    return this.http.put<TipoInstrumentoConfig>(`${BASE}/tipos-instrumento/${id}`, pedido);
  }

  listarTiposDinamica(filtro: FiltroTipos, page: number, size: number): Observable<PageResponse<TipoDinamicaConfig>> {
    return this.listar('tipos-dinamica', filtro, page, size);
  }

  criarTipoDinamica(pedido: TipoDinamicaConfigRequest): Observable<TipoDinamicaConfig> {
    return this.http.post<TipoDinamicaConfig>(`${BASE}/tipos-dinamica`, pedido);
  }

  atualizarTipoDinamica(id: number, pedido: TipoDinamicaConfigRequest): Observable<TipoDinamicaConfig> {
    return this.http.put<TipoDinamicaConfig>(`${BASE}/tipos-dinamica/${id}`, pedido);
  }

  listarTiposProcedimento(filtro: FiltroTipos, page: number, size: number): Observable<PageResponse<TipoProcedimentoConfig>> {
    return this.listar('tipos-procedimento', filtro, page, size);
  }

  criarTipoProcedimento(pedido: TipoProcedimentoConfigRequest): Observable<TipoProcedimentoConfig> {
    return this.http.post<TipoProcedimentoConfig>(`${BASE}/tipos-procedimento`, pedido);
  }

  atualizarTipoProcedimento(id: number, pedido: TipoProcedimentoConfigRequest): Observable<TipoProcedimentoConfig> {
    return this.http.put<TipoProcedimentoConfig>(`${BASE}/tipos-procedimento/${id}`, pedido);
  }

  listarDinamicasConfig(filtro: FiltroDinamicasConfig, page: number, size: number): Observable<PageResponse<DinamicaConfig>> {
    return this.listar('dinamicas-config', filtro, page, size);
  }

  criarDinamicaConfig(pedido: DinamicaConfigRequest): Observable<DinamicaConfig> {
    return this.http.post<DinamicaConfig>(`${BASE}/dinamicas-config`, pedido);
  }

  atualizarDinamicaConfig(id: number, pedido: DinamicaConfigRequest): Observable<DinamicaConfig> {
    return this.http.put<DinamicaConfig>(`${BASE}/dinamicas-config/${id}`, pedido);
  }

  listarProcedimentosConfig(filtro: FiltroProcedimentosConfig, page: number, size: number): Observable<PageResponse<ProcedimentoConfig>> {
    return this.listar('procedimentos-config', filtro, page, size);
  }

  criarProcedimentoConfig(pedido: ProcedimentoConfigRequest): Observable<ProcedimentoConfig> {
    return this.http.post<ProcedimentoConfig>(`${BASE}/procedimentos-config`, pedido);
  }

  atualizarProcedimentoConfig(id: number, pedido: ProcedimentoConfigRequest): Observable<ProcedimentoConfig> {
    return this.http.put<ProcedimentoConfig>(`${BASE}/procedimentos-config/${id}`, pedido);
  }

  listarPermissoes(filtro: FiltroPermissoes, page: number, size: number): Observable<PageResponse<PermissaoConfig>> {
    return this.listar('permissoes', filtro, page, size);
  }

  criarPermissao(pedido: PermissaoConfigRequest): Observable<PermissaoConfig> {
    return this.http.post<PermissaoConfig>(`${BASE}/permissoes`, pedido);
  }

  atualizarPermissao(id: number, pedido: PermissaoConfigRequest): Observable<PermissaoConfig> {
    return this.http.put<PermissaoConfig>(`${BASE}/permissoes/${id}`, pedido);
  }

  listarTiposAto(filtro: FiltroTipos, page: number, size: number): Observable<PageResponse<TipoAtoConfig>> {
    return this.listar('tipos-ato', filtro, page, size);
  }

  criarTipoAto(pedido: TipoAtoConfigRequest): Observable<TipoAtoConfig> {
    return this.http.post<TipoAtoConfig>(`${BASE}/tipos-ato`, pedido);
  }

  atualizarTipoAto(id: number, pedido: TipoAtoConfigRequest): Observable<TipoAtoConfig> {
    return this.http.put<TipoAtoConfig>(`${BASE}/tipos-ato/${id}`, pedido);
  }

  listarRedesDefesa(page: number, size: number): Observable<PageResponse<RedeDefesaConfig>> {
    return this.listar('redes-defesa', {}, page, size);
  }

  criarRedeDefesa(pedido: RedeDefesaConfigRequest): Observable<RedeDefesaConfig> {
    return this.http.post<RedeDefesaConfig>(`${BASE}/redes-defesa`, pedido);
  }

  atualizarRedeDefesa(id: number, pedido: RedeDefesaConfigRequest): Observable<RedeDefesaConfig> {
    return this.http.put<RedeDefesaConfig>(`${BASE}/redes-defesa/${id}`, pedido);
  }

  listarRegras(filtros: FiltroRegras, pagina: number, tamanho: number): Observable<PageResponse<RegraConfig>> {
    return this.listar('regras', filtros, pagina, tamanho);
  }

  criarRegra(pedido: RegraConfigRequest): Observable<RegraConfig> {
    return this.http.post<RegraConfig>(`${BASE}/regras`, pedido);
  }

  atualizarRegra(id: number, pedido: RegraConfigRequest): Observable<RegraConfig> {
    return this.http.put<RegraConfig>(`${BASE}/regras/${id}`, pedido);
  }

  listarDocumentoConfig(filtro: FiltroDocumentoConfig, page: number, size: number): Observable<PageResponse<DocumentoConfig>> {
    return this.http.get<PageResponse<DocumentoConfig>>(BASE_DOCUMENTO_CONFIG, { params: this.parametros(filtro, page, size) });
  }

  criarDocumentoConfig(pedido: CriarDocumentoConfigPedido): Observable<DocumentoConfig> {
    return this.http.post<DocumentoConfig>(BASE_DOCUMENTO_CONFIG, pedido);
  }

  atualizarDocumentoConfig(id: number, pedido: AtualizarDocumentoConfigPedido): Observable<DocumentoConfig> {
    return this.http.put<DocumentoConfig>(`${BASE_DOCUMENTO_CONFIG}/${id}`, pedido);
  }

  private listar<T>(recurso: string, filtro: object, page: number, size: number): Observable<PageResponse<T>> {
    return this.http.get<PageResponse<T>>(`${BASE}/${recurso}`, { params: this.parametros(filtro, page, size) });
  }

  private parametros(filtro: object, page: number, size: number): HttpParams {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null) {
        params = params.set(chave, String(valor));
      }
    }
    return params;
  }
}
