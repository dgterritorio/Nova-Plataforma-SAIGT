import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Catalogo } from '../models/catalogo.model';

/** Catálogos globais (`tipo_instrumento`/`tipo_procedimento`). */
@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Catalogo[]> {
    return this.http.get<Catalogo[]>('/api/v1/catalogos');
  }
}
