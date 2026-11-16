import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject, signal } from '@angular/core';

export const LOCALIZACAO = new InjectionToken<Pick<Location, 'assign'>>('LOCALIZACAO', {
  factory: () => inject(DOCUMENT).location,
});

export const MENSAGEM_ERRO_DESCARGA = 'Não foi possível descarregar o ficheiro. Tente novamente.';

interface DescargaResponse {
  url: string;
}

@Injectable({ providedIn: 'root' })
export class DescargaService {
  private readonly http = inject(HttpClient);
  private readonly localizacao = inject(LOCALIZACAO);

  readonly erro = signal<string | null>(null);
  /** O `documentoId` cujo ticket está a ser pedido; nulo quando não há pedido em curso. */
  readonly emCurso = signal<string | null>(null);

  /** O `url` devolvido é um ticket de uso curto: navega-se logo para ele, sem ler os bytes aqui. */
  descarregar(processoId: string, documentoId: string): void {
    this.erro.set(null);
    this.emCurso.set(documentoId);
    this.http.post<DescargaResponse>(`/api/v1/processos/${processoId}/documentos/${documentoId}/descarga`, null).subscribe({
      next: (resposta) => {
        this.emCurso.set(null);
        this.localizacao.assign(resposta.url);
      },
      error: () => {
        this.emCurso.set(null);
        this.erro.set(MENSAGEM_ERRO_DESCARGA);
      },
    });
  }
}
