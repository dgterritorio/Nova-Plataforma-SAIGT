import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import {
  AnularProcessoRequest,
  AtualizarDadosFormularioRequest,
  CamposResumoValidados,
  CriarProcessoRequest,
  DesbloquearCampoRequest,
  DinamicaAssociavel,
  DevolverDgtRequest,
  EnviarPcmRequest,
  EstatisticasProcesso,
  GravarValidacaoItensRequest,
  HistoricoProcessoEntrada,
  PainelProcessos,
  ItemValidacao,
  Processo,
  ProcessoFiltro,
  ProcessoDetalhe,
  ProcessoListagem,
  ReatribuirAutorRequest,
  RegistoExternoRequest,
  RejeitarProcessoRequest,
  SubmeterProcessoRequest,
  ValidacaoItensResponse,
  ValidarProcessoRequest,
  VersaoProcessoRequest,
} from '../models/processo.model';

export type FormatoExportacao = 'EXCEL' | 'PDF';

export interface ExportacaoProcessos {
  ficheiro: Blob;
  nomeFicheiro: string;
}

const NOME_POR_OMISSAO: Record<FormatoExportacao, string> = {
  EXCEL: 'processos.xlsx',
  PDF: 'processos.pdf',
};

/** A listagem e a exportação têm de levar exatamente os mesmos filtros, senão o ficheiro deixa de bater com o ecrã. */
function parametrosDoFiltro(filtro: ProcessoFiltro): HttpParams {
  let params = new HttpParams();
  for (const [chave, valor] of Object.entries(filtro)) {
    if (valor === undefined || valor === null || valor === '') {
      continue;
    }
    if (Array.isArray(valor)) {
      for (const item of valor) {
        params = params.append(chave, String(item));
      }
    } else {
      params = params.set(chave, String(valor));
    }
  }
  return params;
}

/** Aceita `filename="…"` e `filename*=UTF-8''…` (RFC 6266); este último ganha quando vêm os dois. */
function nomeDoContentDisposition(cabecalho: string | null): string | null {
  if (!cabecalho) {
    return null;
  }
  const codificado = /filename\*\s*=\s*[^']*'[^']*'([^;]+)/i.exec(cabecalho);
  if (codificado) {
    try {
      return decodeURIComponent(codificado[1].trim().replace(/^"|"$/g, ''));
    } catch {
      // Codificação inválida: tenta o `filename` simples.
    }
  }
  const simples = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(cabecalho);
  const nome = (simples?.[2] ?? simples?.[1])?.trim();
  return nome || null;
}

@Injectable({ providedIn: 'root' })
export class ProcessoService {
  private readonly http = inject(HttpClient);

  listar(filtro: ProcessoFiltro, page: number, size: number): Observable<PageResponse<ProcessoListagem>> {
    const params = parametrosDoFiltro(filtro).set('page', page).set('size', size);
    return this.http.get<PageResponse<ProcessoListagem>>('/api/v1/processos', { params });
  }

  /** Sem `page` nem `size`: a API recusa-os. O erro (em Blob) é lido pelo interceptor como os restantes. */
  exportar(filtro: ProcessoFiltro, formato: FormatoExportacao): Observable<ExportacaoProcessos> {
    const params = parametrosDoFiltro(filtro).set('formato', formato);
    return this.http.get('/api/v1/processos/exportacao', { params, responseType: 'blob', observe: 'response' }).pipe(
      map((resposta) => ({
        ficheiro: resposta.body ?? new Blob(),
        nomeFicheiro: nomeDoContentDisposition(resposta.headers.get('Content-Disposition')) ?? NOME_POR_OMISSAO[formato],
      })),
    );
  }

  /** Âmbito (global/entidade) resolvido no backend. */
  /** Contagens e primeira página de cada grupo do utilizador, numa só chamada. */
  painel(size: number): Observable<PainelProcessos> {
    const params = new HttpParams().set('size', size);
    return this.http.get<PainelProcessos>('/api/v1/processos/painel', { params });
  }

  obter(processoId: string): Observable<ProcessoDetalhe> {
    return this.http.get<ProcessoDetalhe>(`/api/v1/processos/${processoId}`);
  }

  /** Mais recente primeiro; os nomes de estado das entradas antigas passam por `rotuloEstado`. */
  historico(processoId: string, page: number, size: number): Observable<PageResponse<HistoricoProcessoEntrada>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<HistoricoProcessoEntrada>>(`/api/v1/processos/${processoId}/historico`, { params });
  }

  /** As Dinâmicas de IGT que o autor pode indicar em `dinamicaAssociadaId`, da mais recente para a mais antiga. */
  dinamicasAssociaveis(processoId: string, page: number, size: number): Observable<PageResponse<DinamicaAssociavel>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<DinamicaAssociavel>>(`/api/v1/processos/${processoId}/dinamicas-associaveis`, { params });
  }

  estatisticas(processoId: string): Observable<EstatisticasProcesso> {
    return this.http.get<EstatisticasProcesso>(`/api/v1/processos/${processoId}/estatisticas`);
  }

  criar(dinamicaId: string, request: CriarProcessoRequest): Observable<Processo> {
    return this.http.post<Processo>(`/api/v1/dinamicas/${dinamicaId}/processos`, request);
  }

  registoExterno(dinamicaId: string, request: RegistoExternoRequest): Observable<Processo> {
    return this.http.post<Processo>(`/api/v1/dinamicas/${dinamicaId}/processos/registo-externo`, request);
  }

  /** Nunca transiciona estado nem valida obrigatoriedade: isso fica no `submeter`. */
  guardarRascunho(processoId: string, request: AtualizarDadosFormularioRequest): Observable<Processo> {
    return this.http.put<Processo>(`/api/v1/processos/${processoId}/dados-formulario`, request);
  }

  submeter(processoId: string, request: SubmeterProcessoRequest): Observable<Processo> {
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/submeter`, request);
  }

  /** Só antes de a DGT capturar: volta a `EM_SUBMISSAO`. */
  resgatar(processoId: string, versao: number): Observable<Processo> {
    const request: VersaoProcessoRequest = { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/resgatar`, request);
  }

  /** Autor: `REJEITADO_DGT` ou `ANULADO_INCM` → `EM_SUBMISSAO`, onde o processo volta a ser editável. */
  corrigir(processoId: string, versao: number): Observable<Processo> {
    const request: VersaoProcessoRequest = { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/corrigir`, request);
  }

  /** DGT: `ENVIADO_DGT` → `EM_VALIDACAO_DGT`. Compete com `resgatar`. */
  capturar(processoId: string, versao: number): Observable<Processo> {
    const request: VersaoProcessoRequest = { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/capturar`, request);
  }

  /** DGT, em `EM_VALIDACAO_DGT`: `VALIDADO` ou `REJEITADO` por item. Não incrementa a `versao`. */
  gravarValidacaoItens(processoId: string, versao: number, itens: ItemValidacao[]): Observable<ValidacaoItensResponse> {
    const request: GravarValidacaoItensRequest = { versao, itens };
    return this.http.put<ValidacaoItensResponse>(`/api/v1/processos/${processoId}/validacao/itens`, request);
  }

  /** DGT: `EM_VALIDACAO_DGT` → `VALIDADO_DGT`. */
  validar(processoId: string, versao: number, campos?: CamposResumoValidados): Observable<Processo> {
    const request: VersaoProcessoRequest | ValidarProcessoRequest = campos ? { versao, ...campos } : { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/validar`, request);
  }

  /** DGT, «Confirmar Rejeição»: `EM_VALIDACAO_DGT` → `REJEITADO_DGT`, com a recusa feita dos itens rejeitados. */
  rejeitar(processoId: string, versao: number, notasGerais: string | null): Observable<Processo> {
    const request: RejeitarProcessoRequest = { versao, notasGerais };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/rejeitar`, request);
  }

  /**
   * DGT ou PCM: `VALIDADO_DGT` → `EM_VALIDACAO_INCM` ou `ANULADO_INCM`, conforme a resposta da INCM ao `SubmitNewAct`.
   * Com a INCM indisponível o backend responde 500 `INTEGRACAO_INDISPONIVEL` e o processo continua em `VALIDADO_DGT`.
   */
  enviarIncm(processoId: string, versao: number): Observable<Processo> {
    const request: VersaoProcessoRequest = { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/enviar-incm`, request);
  }

  /**
   * DGT, 1.ª série: atribui o processo `VALIDADO_DGT` ao utilizador da PCM, sem mudar o estado.
   * 409 `PCM_*` ou `VERSAO_DESATUALIZADA` quando já não é possível; 400 `PCM_UTILIZADOR_INVALIDO` em `utilizadorId`.
   */
  enviarPcm(processoId: string, versao: number, utilizadorId: string): Observable<Processo> {
    const request: EnviarPcmRequest = { versao, utilizadorId };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/enviar-pcm`, request);
  }

  /**
   * PCM, no processo que lhe foi atribuído: devolve-o à DGT sem mudar o estado (`VALIDADO_DGT`); a atribuição fica
   * nula. 409 `PCM_*` ou `VERSAO_DESATUALIZADA` quando já não é possível.
   */
  devolverDgt(processoId: string, versao: number, observacoes?: string | null): Observable<Processo> {
    const request: DevolverDgtRequest = observacoes ? { versao, observacoes } : { versao };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/devolver-dgt`, request);
  }

  /** `MASTER`: `EM_VALIDACAO_DGT` → `ANULADO`, estado final. `motivo` obrigatório. */
  /** O autor, na correção: o campo (e as chaves que só mudam com ele) sai de `camposBloqueados`, sem mudar o estado. */
  desbloquearCampo(processoId: string, request: DesbloquearCampoRequest): Observable<Processo> {
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/desbloquear-campo`, request);
  }

  anular(processoId: string, versao: number, motivo: string): Observable<Processo> {
    const request: AnularProcessoRequest = { versao, motivo };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/anular`, request);
  }

  /**
   * `MASTER`, sem motivo: muda `criadoPorId` em Em Submissão, Enviado DGT, Rejeitado DGT e Anulado INCM.
   * Com `pedidoApoioId`, a API deixa esse pedido de apoio resolvido; sem ele, o campo não vai no pedido.
   */
  reatribuirAutor(
    processoId: string,
    versao: number,
    novoUtilizadorId: string,
    pedidoApoioId?: string,
  ): Observable<Processo> {
    const request: ReatribuirAutorRequest = pedidoApoioId
      ? { versao, novoUtilizadorId, pedidoApoioId }
      : { versao, novoUtilizadorId };
    return this.http.patch<Processo>(`/api/v1/processos/${processoId}/reatribuir-autor`, request);
  }
}
