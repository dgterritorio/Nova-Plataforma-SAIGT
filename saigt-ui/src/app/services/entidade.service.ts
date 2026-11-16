import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import {
  AtualizarContactoRequest,
  AtualizarEntidadeRequest,
  Contacto,
  ContactoRequest,
  CriarEntidadeRequest,
  Entidade,
  EntidadeEmitente,
  EntidadeFiltro,
  TipoEntidade,
} from '../models/entidade.model';

const BASE = '/api/v1/entidades';

@Injectable({ providedIn: 'root' })
export class EntidadeService {
  private readonly http = inject(HttpClient);

  listar(filtro: EntidadeFiltro, page: number, size: number): Observable<PageResponse<Entidade>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return this.http.get<PageResponse<Entidade>>(BASE, { params });
  }

  /** As entidades ativas do tipo, por ordem do nome, para escolher a emitente; qualquer sessão com entidade. */
  emitentes(tipoEntidade: TipoEntidade): Observable<EntidadeEmitente[]> {
    return this.http.get<EntidadeEmitente[]>(`${BASE}/emitentes`, { params: new HttpParams().set('tipoEntidade', tipoEntidade) });
  }

  criar(request: CriarEntidadeRequest): Observable<Entidade> {
    return this.http.post<Entidade>(BASE, request);
  }

  /** Só os campos alterados; texto vazio limpa o NIPC e o concelho. */
  atualizar(id: number, request: AtualizarEntidadeRequest): Observable<Entidade> {
    return this.http.patch<Entidade>(`${BASE}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`);
  }

  listarContactos(entidadeId: number): Observable<Contacto[]> {
    return this.http.get<Contacto[]>(`${BASE}/${entidadeId}/contactos`);
  }

  /** `principal` omitido cria-o não principal; `true` com outro principal ativo dá 409. */
  criarContacto(entidadeId: number, request: ContactoRequest): Observable<Contacto> {
    return this.http.post<Contacto>(`${BASE}/${entidadeId}/contactos`, request);
  }

  /** Só os campos alterados; texto vazio limpa a descrição ou o telefone. */
  editarContacto(entidadeId: number, contactoId: number, request: AtualizarContactoRequest): Observable<Contacto> {
    return this.http.patch<Contacto>(`${BASE}/${entidadeId}/contactos/${contactoId}`, request);
  }
}
