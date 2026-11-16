import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResponse } from '../core/models/page-response.model';
import { AtualizarPlantaRequest, Planta, PlantaFiltro } from '../models/planta.model';

/** Plantas SAIGT/SNIT do backoffice (Master e Técnico). A página das Plantas de um processo é o `PlantasService`. */
@Injectable({ providedIn: 'root' })
export class PlantaService {
  private readonly http = inject(HttpClient);

  listar(filtro: PlantaFiltro, page: number, size: number): Observable<PageResponse<Planta>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [chave, valor] of Object.entries(filtro)) {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return this.http.get<PageResponse<Planta>>('/api/v1/plantas', { params });
  }

  atualizar(id: string, alteracoes: AtualizarPlantaRequest): Observable<Planta> {
    return this.http.patch<Planta>(`/api/v1/plantas/${id}`, alteracoes);
  }
}
