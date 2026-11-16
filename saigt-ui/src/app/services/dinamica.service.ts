import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AssociacaoInstrumentosResponse,
  AssociarInstrumentosRequest,
  DinamicasSemIgtDisponiveis,
  IniciarDinamicaRequest,
} from '../models/dinamica.model';
import { Dinamica } from '../models/instrumento.model';

/** Dinâmicas iniciadas sem IGT de partida; as iniciadas a partir de um IGT ficam no `InstrumentoService`. */
@Injectable({ providedIn: 'root' })
export class DinamicaService {
  private readonly http = inject(HttpClient);

  /** 409 `DESIGNACAO_DUPLICADA` no campo `descritivo`, 409 `DINAMICA_NAO_ELEGIVEL`, 403 `SEM_PERMISSAO`. */
  iniciar(request: IniciarDinamicaRequest): Observable<Dinamica> {
    return this.http.post<Dinamica>('/api/v1/dinamicas', request);
  }

  /** Sem cache: uma vez por navegação. */
  disponivel(): Observable<DinamicasSemIgtDisponiveis> {
    return this.http.get<DinamicasSemIgtDisponiveis>('/api/v1/dinamicas/disponivel');
  }

  /** Substitui o conjunto: 400 `ASSOCIACAO_INVALIDA`, 403 `SEM_PERMISSAO`, 404, 409 `ESTADO_INVALIDO`. */
  associarInstrumentos(dinamicaId: string, instrumentoIds: string[]): Observable<AssociacaoInstrumentosResponse> {
    const pedido: AssociarInstrumentosRequest = { instrumentoIds };
    return this.http.put<AssociacaoInstrumentosResponse>(`/api/v1/dinamicas/${dinamicaId}/instrumentos`, pedido);
  }
}
