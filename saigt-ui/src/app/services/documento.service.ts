import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, defer, switchMap, throwError } from 'rxjs';
import { RENOVACAO_PELO_CHAMADOR } from '../core/auth/auth.interceptor';
import { AuthService } from '../core/auth/auth.service';
import { ApiError, semLigacao } from '../core/models/error-response.model';
import { PageResponse } from '../core/models/page-response.model';
import {
  CategoriaDocumento,
  DocumentoProcesso,
  EstruturaDocumental,
  FiltroDocumentos,
  HistoricoDocumental,
  ObservacoesDocumento,
  ReclassificarDocumentoPedido,
  VerificacaoDocumental,
} from '../models/documento.model';

const MARGEM_RENOVACAO_SEGUNDOS = 60;

/** Só do cliente: o carregamento não chegou à API (sem ligação ou API em baixo), por isso o item continua sem ficheiro. */
export const CODIGO_CARREGAMENTO_SEM_LIGACAO = 'CARREGAMENTO_SEM_LIGACAO';
const MENSAGEM_CARREGAMENTO_SEM_LIGACAO = 'Não foi possível carregar o ficheiro. Tente novamente.';

@Injectable({ providedIn: 'root' })
export class DocumentoService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  estrutura(processoId: string): Observable<EstruturaDocumental> {
    return this.http.get<EstruturaDocumental>(`/api/v1/processos/${processoId}/estrutura-documental`);
  }

  /** Uma página dos ficheiros já enviados, por ordem de upload; `size` máximo 100. */
  listar(processoId: string, filtro: FiltroDocumentos = {}, page = 0, size = 20): Observable<PageResponse<DocumentoProcesso>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null) {
        params = params.set(chave, String(valor));
      }
    }
    return this.http.get<PageResponse<DocumentoProcesso>>(`/api/v1/processos/${processoId}/documentos`, { params });
  }

  /** Os ficheiros de todos os processos do mesmo Procedimento na Dinâmica, incluindo o do pedido. */
  historico(processoId: string): Observable<HistoricoDocumental> {
    return this.http.get<HistoricoDocumental>(`/api/v1/processos/${processoId}/documentos/historico`);
  }

  /**
   * Sem `documentoConfigId` o ficheiro vai para o balde "outros", só em `DEPOSITO`. `tags`: outros itens de depósito
   * obrigatório que o mesmo ficheiro também satisfaz.
   */
  carregar(
    processoId: string,
    ficheiro: File,
    categoria: CategoriaDocumento,
    documentoConfigId?: number,
    tags: number[] = [],
  ): Observable<DocumentoProcesso> {
    // O servidor lê o corpo em streaming: as partes de texto têm de vir antes do ficheiro.
    const corpo = new FormData();
    corpo.append('categoria', categoria);
    if (documentoConfigId !== undefined) {
      corpo.append('documentoConfigId', String(documentoConfigId));
    }
    for (const tag of tags) {
      corpo.append('tags', String(tag));
    }
    corpo.append('ficheiro', ficheiro);
    return this.enviarFicheiro(() =>
      this.http.post<DocumentoProcesso>(`/api/v1/processos/${processoId}/documentos`, corpo, { context: contextoCarregamento() }),
    );
  }

  /** Mantém a categoria e o item do documento. */
  substituir(processoId: string, documentoId: string, ficheiro: File): Observable<DocumentoProcesso> {
    const corpo = new FormData();
    corpo.append('ficheiro', ficheiro);
    return this.enviarFicheiro(() =>
      this.http.put<DocumentoProcesso>(`/api/v1/processos/${processoId}/documentos/${documentoId}`, corpo, {
        context: contextoCarregamento(),
      }),
    );
  }

  /** Cria ou substitui as observações de um item; não há forma de as apagar. */
  gravarObservacoes(processoId: string, documentoConfigId: number, observacoes: string): Observable<ObservacoesDocumento> {
    return this.http.put<ObservacoesDocumento>(`/api/v1/processos/${processoId}/documentos/observacoes/${documentoConfigId}`, {
      observacoes,
    });
  }

  gravarObservacoesOutros(processoId: string, observacoes: string): Observable<ObservacoesDocumento> {
    return this.http.put<ObservacoesDocumento>(`/api/v1/processos/${processoId}/documentos/observacoes/outros`, { observacoes });
  }

  /** «Desbloquear ficheiro» na correção (API-DOC-016): a justificação fica visível à DGT na validação. */
  desbloquear(processoId: string, documentoId: string, justificacao: string): Observable<DocumentoProcesso> {
    return this.http.patch<DocumentoProcesso>(`/api/v1/processos/${processoId}/documentos/${documentoId}/desbloquear`, {
      justificacao,
    });
  }

  /** Muda o item, a caixa e as tags de um ficheiro já gravado (API-DOC-004). */
  reclassificar(processoId: string, documentoId: string, pedido: ReclassificarDocumentoPedido): Observable<DocumentoProcesso> {
    return this.http.patch<DocumentoProcesso>(`/api/v1/processos/${processoId}/documentos/${documentoId}/classificacao`, pedido);
  }

  /** A checklist de validação DGT (API-DOC-012), calculada a pedido sobre os ficheiros atuais; nada fica gravado. */
  verificacoes(processoId: string): Observable<VerificacaoDocumental[]> {
    return this.http.get<VerificacaoDocumental[]>(`/api/v1/processos/${processoId}/documentos/verificacoes`);
  }

  remover(processoId: string, documentoId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/processos/${processoId}/documentos/${documentoId}`);
  }

  /**
   * Renova o token antes do envio se estiver a menos de 60 s do fim e, se o envio terminar com 401 ou sem resposta
   * (o 401 que chega a meio do corpo), renova e repete uma só vez com o mesmo ficheiro.
   */
  private enviarFicheiro<T>(pedido: () => Observable<T>): Observable<T> {
    const enviar = (podeRepetir: boolean): Observable<T> =>
      defer(pedido).pipe(
        catchError((erro: unknown) =>
          podeRepetir && erro instanceof ApiError && (erro.estado === 401 || erro.estado === 0)
            ? this.renovar(() => this.authService.refresh()).pipe(switchMap(() => enviar(false)))
            : throwError(() => erro),
        ),
      );

    return this.renovar(() => this.authService.renovarSeExpirarEm(MARGEM_RENOVACAO_SEGUNDOS)).pipe(
      switchMap(() => enviar(true)),
      catchError((erro: unknown) =>
        throwError(() =>
          semLigacao(erro)
            ? new ApiError(CODIGO_CARREGAMENTO_SEM_LIGACAO, MENSAGEM_CARREGAMENTO_SEM_LIGACAO, (erro as ApiError).traceId, null, (erro as ApiError).estado)
            : erro,
        ),
      ),
    );
  }

  /** Como o `authInterceptor`: uma renovação recusada termina a sessão e manda para o login; sem ligação, a sessão fica. */
  private renovar(renovacao: () => Observable<unknown>): Observable<unknown> {
    return defer(renovacao).pipe(
      catchError((erro: unknown) => {
        if (!semLigacao(erro)) {
          this.authService.limparSessao();
          void this.router.navigate(['/login']);
        }
        return throwError(() => erro);
      }),
    );
  }
}

function contextoCarregamento(): HttpContext {
  return new HttpContext().set(RENOVACAO_PELO_CHAMADOR, true);
}
