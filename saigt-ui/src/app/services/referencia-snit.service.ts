import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Concelho } from '../models/entidade.model';

/** NUTS II da réplica do SNIT. */
export interface NutII {
  codigo: string;
  nome: string;
}

/** Listas de referência do SNIT, lidas da réplica local da API (nunca do SNIT). */
@Injectable({ providedIn: 'root' })
export class ReferenciaSnitService {
  private readonly http = inject(HttpClient);

  /** Ordenados pelo nome; `nut2` restringe à NUTS II. */
  concelhos(nut2?: string): Observable<Concelho[]> {
    const params = nut2 ? new HttpParams().set('nut2', nut2) : undefined;
    return this.http.get<Concelho[]>('/api/v1/referencias-snit/MUNICIPALITIES', { params });
  }

  nutsII(): Observable<NutII[]> {
    return this.http.get<NutII[]>('/api/v1/referencias-snit/NUT2');
  }
}
