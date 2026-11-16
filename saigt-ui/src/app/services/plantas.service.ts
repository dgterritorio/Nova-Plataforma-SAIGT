import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  GravarConjuntoMotu,
  GravarPlantasRequest,
  Metadados,
  MetadadosMotu,
  MetadadosProcesso,
  Plantas,
  RegistoMotu,
  SerieMotuDados,
} from '../models/plantas.model';

/** Páginas das Plantas e dos Metadados (sem GeoConform). Os ficheiros sobem pelo `DocumentoService`. */
@Injectable({ providedIn: 'root' })
export class PlantasService {
  private readonly http = inject(HttpClient);

  obterPlantas(processoId: string): Observable<Plantas> {
    return this.http.get<Plantas>(`/api/v1/processos/${processoId}/plantas`);
  }

  gravarPlantas(processoId: string, request: GravarPlantasRequest): Observable<Plantas> {
    return this.http.put<Plantas>(`/api/v1/processos/${processoId}/plantas`, request);
  }

  /** PDF do cartograma das folhas, gerado pela API a partir do cartograma, do TXT e da associação gravada. */
  cartogramaPdf(processoId: string): Observable<Blob> {
    return this.http.get(`/api/v1/processos/${processoId}/plantas/cartograma.pdf`, { responseType: 'blob' });
  }

  obterMetadados(processoId: string): Observable<Metadados> {
    return this.http.get<Metadados>(`/api/v1/processos/${processoId}/metadados`);
  }

  gravarMetadados(processoId: string, dados: MetadadosProcesso): Observable<Metadados> {
    return this.http.put<Metadados>(`/api/v1/processos/${processoId}/metadados`, dados);
  }

  obterMetadadosMotu(processoId: string): Observable<MetadadosMotu> {
    return this.http.get<MetadadosMotu>(`/api/v1/processos/${processoId}/metadados-motu`);
  }

  gravarSerieMotu(processoId: string, motuId: string, dados: SerieMotuDados): Observable<RegistoMotu> {
    return this.http.put<RegistoMotu>(`/api/v1/processos/${processoId}/metadados-motu/${motuId}`, dados);
  }

  gravarConjuntoMotu(processoId: string, motuId: string, corpo: GravarConjuntoMotu): Observable<RegistoMotu> {
    return this.http.put<RegistoMotu>(`/api/v1/processos/${processoId}/metadados-motu/${motuId}`, corpo);
  }

  criarFichaConjuntoOpcional(processoId: string, reproduzirUltima: boolean): Observable<RegistoMotu> {
    return this.http.post<RegistoMotu>(`/api/v1/processos/${processoId}/metadados-motu`, { reproduzirUltima });
  }

  removerFichaConjuntoOpcional(processoId: string, motuId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/processos/${processoId}/metadados-motu/${motuId}`);
  }
}
