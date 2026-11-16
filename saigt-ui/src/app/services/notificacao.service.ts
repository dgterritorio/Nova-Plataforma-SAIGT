import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import {
  CriarMensagemNotificacaoResposta,
  CriarNotificacaoRequest,
  CriarNotificacaoResponse,
  DocumentoNotificacao,
  DownloadAnexoNotificacao,
  NotificacaoDetalhe,
  NotificacaoFiltro,
  NotificacaoResumo,
  RemocaoNotificacao,
} from '../models/notificacao.model';

export const FILTRO_NAO_LIDAS: NotificacaoFiltro = { estado: ['ATIVA'], tipo: ['TEMPORARIA', 'ALERTA'] };

@Injectable({ providedIn: 'root' })
export class NotificacaoService {
  private readonly http = inject(HttpClient);

  listar(filtro: NotificacaoFiltro, page: number, size: number): Observable<PageResponse<NotificacaoResumo>> {
    let params = new HttpParams();
    for (const [chave, valor] of Object.entries(filtro)) {
      for (const item of Array.isArray(valor) ? valor : [valor]) {
        if (item !== undefined && item !== null && item !== '') {
          params = params.append(chave, String(item));
        }
      }
    }
    params = params.set('page', page).set('size', size);
    return this.http.get<PageResponse<NotificacaoResumo>>('/api/v1/notificacoes', { params });
  }

  obter(id: string): Observable<NotificacaoDetalhe> {
    return this.http.get<NotificacaoDetalhe>(`/api/v1/notificacoes/${encodeURIComponent(id)}`);
  }

  criar(pedido: CriarNotificacaoRequest): Observable<CriarNotificacaoResponse> {
    return this.http.post<CriarNotificacaoResponse>('/api/v1/notificacoes', pedido);
  }

  responder(id: string, texto: string): Observable<CriarMensagemNotificacaoResposta> {
    return this.http.post<CriarMensagemNotificacaoResposta>(`/api/v1/notificacoes/${encodeURIComponent(id)}/mensagens`, {
      texto,
    });
  }

  reabrir(id: string): Observable<NotificacaoResumo> {
    return this.http.patch<NotificacaoResumo>(`/api/v1/notificacoes/${encodeURIComponent(id)}/reabrir`, null);
  }

  ler(id: string): Observable<NotificacaoResumo> {
    return this.http.patch<NotificacaoResumo>(`/api/v1/notificacoes/${encodeURIComponent(id)}/ler`, null);
  }

  resolver(id: string): Observable<NotificacaoResumo> {
    return this.http.patch<NotificacaoResumo>(`/api/v1/notificacoes/${encodeURIComponent(id)}/resolver`, null);
  }

  remover(id: string): Observable<RemocaoNotificacao> {
    return this.http.patch<RemocaoNotificacao>(`/api/v1/notificacoes/${encodeURIComponent(id)}/remover`, null);
  }

  anexar(id: string, ficheiro: File): Observable<DocumentoNotificacao> {
    const corpo = new FormData();
    corpo.append('ficheiro', ficheiro);
    return this.http.post<DocumentoNotificacao>(`/api/v1/notificacoes/${encodeURIComponent(id)}/documentos`, corpo);
  }

  urlDownload(id: string, docId: string): Observable<DownloadAnexoNotificacao> {
    return this.http.post<DownloadAnexoNotificacao>(
      `/api/v1/notificacoes/${encodeURIComponent(id)}/documentos/${encodeURIComponent(docId)}/descarga`,
      null,
    );
  }

  contarNaoLidas(): Observable<number> {
    return this.listar(FILTRO_NAO_LIDAS, 0, 1).pipe(map((pagina) => pagina.totalElements));
  }
}
