import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import {
  AlterarPerfilInternoRequest,
  AtualizarPerfilRequest,
  RevogarSessoesResponse,
  Utilizador,
  UtilizadorFiltro,
  UtilizadorMe,
} from '../models/utilizador.model';

@Injectable({ providedIn: 'root' })
export class UtilizadorService {
  private readonly http = inject(HttpClient);

  listar(filtro: UtilizadorFiltro, page: number, size: number): Observable<PageResponse<Utilizador>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return this.http.get<PageResponse<Utilizador>>('/api/v1/utilizadores', { params });
  }

  alterarPerfilInterno(id: string, request: AlterarPerfilInternoRequest): Observable<Utilizador> {
    return this.http.patch<Utilizador>(`/api/v1/utilizadores/${id}/perfil-interno`, request);
  }

  /** Sem corpo. */
  revogarSessoes(id: string): Observable<RevogarSessoesResponse> {
    return this.http.patch<RevogarSessoesResponse>(`/api/v1/utilizadores/${id}/sessoes/revogar`, null);
  }

  obterMe(): Observable<UtilizadorMe> {
    return this.http.get<UtilizadorMe>('/api/v1/utilizadores/me');
  }

  /** Só AD_DGT: 403 para INCM, 400 com `nome`/`cargo`, 409 com o email de outra conta DGT. */
  atualizarMe(request: AtualizarPerfilRequest): Observable<UtilizadorMe> {
    return this.http.patch<UtilizadorMe>('/api/v1/utilizadores/me', request);
  }
}
