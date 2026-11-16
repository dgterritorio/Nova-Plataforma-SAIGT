import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  AtualizarListaValoresRequest,
  CriarListaValoresRequest,
  ListaValoresItem,
  TIPOS_LISTA_VALORES,
  TipoListaValores,
} from '../models/lista-valores.model';
import { OpcaoLista, TipoListaFormulario } from '../models/processo.model';

@Injectable({ providedIn: 'root' })
export class ListaValoresService {
  private readonly http = inject(HttpClient);

  listar(tipo?: string): Observable<ListaValoresItem[]> {
    const params = tipo ? new HttpParams().set('tipo', tipo) : undefined;
    return this.http.get<ListaValoresItem[]>('/api/v1/lista-valores', { params });
  }

  /** Lista local até a API expor os tipos com o rótulo; quem chama já recebe um `Observable` para a troca não lhe mexer. */
  tipos(): Observable<TipoListaValores[]> {
    return of([...TIPOS_LISTA_VALORES]);
  }

  opcoes(tipo: TipoListaFormulario): Observable<OpcaoLista[]> {
    return this.http.get<OpcaoLista[]>('/api/v1/lista-valores/opcoes', { params: new HttpParams().set('tipo', tipo) });
  }

  criar(request: CriarListaValoresRequest): Observable<ListaValoresItem> {
    return this.http.post<ListaValoresItem>('/api/v1/lista-valores', request);
  }

  atualizar(id: number, request: AtualizarListaValoresRequest): Observable<ListaValoresItem> {
    return this.http.patch<ListaValoresItem>(`/api/v1/lista-valores/${id}`, request);
  }
}
