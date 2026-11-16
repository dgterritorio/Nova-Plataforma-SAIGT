import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EstatisticasAdmin } from '../models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  estatisticas(): Observable<EstatisticasAdmin> {
    return this.http.get<EstatisticasAdmin>('/api/v1/admin/estatisticas');
  }
}
