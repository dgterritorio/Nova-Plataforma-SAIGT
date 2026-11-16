import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenciaKpi, PreferenciaKpiPedido, ValorKpi } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  kpis(): Observable<ValorKpi[]> {
    return this.http.get<ValorKpi[]>('/api/v1/dashboard/kpis');
  }

  preferencias(): Observable<PreferenciaKpi[]> {
    return this.http.get<PreferenciaKpi[]>('/api/v1/dashboard/preferencias');
  }

  /** Substitui todas as preferências do utilizador da sessão; a lista vazia volta à configuração por omissão. */
  gravarPreferencias(lista: PreferenciaKpiPedido[]): Observable<PreferenciaKpi[]> {
    return this.http.put<PreferenciaKpi[]>('/api/v1/dashboard/preferencias', lista);
  }
}
