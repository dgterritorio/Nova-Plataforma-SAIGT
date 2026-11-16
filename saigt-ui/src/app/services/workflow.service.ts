import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Workflow } from '../models/workflow.model';

/** Workflows configurados (`instrumento_dinamica_config`). */
@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Workflow[]> {
    return this.http.get<Workflow[]>('/api/v1/workflows');
  }
}
