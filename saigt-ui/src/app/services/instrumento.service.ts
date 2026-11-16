import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import { TiposProcesso } from '../models/dinamica.model';
import { ItemTerritorio } from '../models/processo.model';
import {
  CriarDinamicaRequest,
  CriarElaboracaoRequest,
  CriarInstrumentoRequest,
  Dinamica,
  ElaboracaoCriada,
  ElaboracaoNova,
  EstatisticasDinamica,
  FiltroInstrumentosEmVigor,
  IgtSnit,
  Instrumento,
  InstrumentoDisponibilidade,
  InstrumentoPainel,
  TipoInstrumento,
} from '../models/instrumento.model';

@Injectable({ providedIn: 'root' })
export class InstrumentoService {
  private readonly http = inject(HttpClient);

  listar(page: number, size: number): Observable<PageResponse<Instrumento>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Instrumento>>('/api/v1/instrumentos', { params });
  }

  /** Sempre `emVigor=true`; com vários tipos, qualquer um deles. */
  pesquisarEmVigor(filtro: FiltroInstrumentosEmVigor, page: number, size: number): Observable<PageResponse<Instrumento>> {
    let params = new HttpParams().set('emVigor', true).set('page', page).set('size', size);
    for (const tipo of filtro.tiposInstrumentoId) {
      params = params.append('tipoInstrumentoId', tipo);
    }
    if (filtro.concelho) {
      params = params.set('concelho', filtro.concelho);
    }
    if (filtro.designacao?.trim()) {
      params = params.set('designacao', filtro.designacao.trim());
    }
    return this.http.get<PageResponse<Instrumento>>('/api/v1/instrumentos', { params });
  }

  /** IGT em vigor no SNIT do tipo indicado (API-PROC-030); o SNIT é consultado em cada pedido. */
  pesquisarSnit(tipoInstrumentoId: number, page: number, size: number): Observable<PageResponse<IgtSnit>> {
    const params = new HttpParams().set('tipoInstrumentoId', tipoInstrumentoId).set('page', page).set('size', size);
    return this.http.get<PageResponse<IgtSnit>>('/api/v1/instrumentos/snit', { params });
  }

  criar(request: CriarInstrumentoRequest): Observable<Instrumento> {
    return this.http.post<Instrumento>('/api/v1/instrumentos', request);
  }

  /** Concelhos que o `POST` aceita: a mesma lista da Elaboração. */
  listarConcelhos(): Observable<ItemTerritorio[]> {
    return this.http.get<ItemTerritorio[]>('/api/v1/instrumentos/concelhos');
  }

  /** Os dois grupos do primeiro passo de Criar Processo; 403 numa sessão sem entidade. */
  tiposProcesso(): Observable<TiposProcesso> {
    return this.http.get<TiposProcesso>('/api/v1/criacao/tipos-processo');
  }

  /** Com id, para `tipoInstrumentoId`. Não confundir com `/catalogos`, que só devolve nomes. */
  listarTipos(): Observable<TipoInstrumento[]> {
    return this.http.get<TipoInstrumento[]>('/api/v1/tipos-instrumento');
  }

  /** Sem cache: quem chama garante uma vez por navegação, nunca polling. */
  disponivel(instrumentoId: string): Observable<InstrumentoDisponibilidade> {
    return this.http.get<InstrumentoDisponibilidade>(`/api/v1/instrumentos/${instrumentoId}/disponivel`);
  }

  painel(instrumentoId: string): Observable<InstrumentoPainel> {
    return this.http.get<InstrumentoPainel>(`/api/v1/instrumentos/${instrumentoId}`);
  }

  obterEstatisticasDinamica(dinamicaId: string): Observable<EstatisticasDinamica> {
    return this.http.get<EstatisticasDinamica>(`/api/v1/dinamicas/${dinamicaId}/estatisticas`);
  }

  iniciarDinamica(instrumentoId: string, request: CriarDinamicaRequest): Observable<Dinamica> {
    return this.http.post<Dinamica>(`/api/v1/instrumentos/${instrumentoId}/dinamicas`, request);
  }

  /** 404 quando o tipo não tem Elaboração. */
  elaboracaoNova(tipoInstrumentoId: number): Observable<ElaboracaoNova> {
    return this.http.get<ElaboracaoNova>(`/api/v1/tipos-instrumento/${tipoInstrumentoId}/elaboracao`);
  }

  /** Cria o IGT, a Elaboração e a primeira tentativa de uma só vez. */
  criarElaboracao(request: CriarElaboracaoRequest): Observable<ElaboracaoCriada> {
    return this.http.post<ElaboracaoCriada>('/api/v1/instrumentos/elaboracao', request);
  }
}
