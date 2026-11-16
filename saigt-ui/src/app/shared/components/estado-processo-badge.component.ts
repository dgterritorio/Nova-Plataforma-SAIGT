import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SeloComponent, TomSelo } from '../ui/selo/selo.component';

type IconeEstado = 'relogio' | 'seta' | 'check' | 'x';

/**
 * `processo.estado` → rótulo/tom/ícone. O Figma só desenha Em submissão, Em Validação DGT, Em validação INCM e
 * Validado DGT; Rejeitado (erro), Rascunho (neutro) e Publicado (sucesso) são decisões nossas.
 */
const ESTADO_INFO: Record<string, { label: string; tom: TomSelo; icone: IconeEstado }> = {
  EM_SUBMISSAO: { label: 'Em submissão', tom: 'info', icone: 'seta' },
  ENVIADO_DGT: { label: 'Enviado DGT', tom: 'aviso', icone: 'seta' },
  EM_VALIDACAO_DGT: { label: 'Em Validação DGT', tom: 'aviso', icone: 'relogio' },
  EM_VALIDACAO_INCM: { label: 'Em validação INCM', tom: 'cinza', icone: 'relogio' },
  VALIDADO_DGT: { label: 'Validado DGT', tom: 'sucesso', icone: 'check' },
  REJEITADO_DGT: { label: 'Rejeitado DGT', tom: 'erro', icone: 'x' },
  ANULADO: { label: 'Anulado', tom: 'neutro', icone: 'x' },
  ANULADO_INCM: { label: 'Anulado INCM', tom: 'cinza', icone: 'x' },
  ENVIADO_INCM: { label: 'Enviado INCM', tom: 'neutro', icone: 'seta' },
  PUBLICADO: { label: 'Publicado', tom: 'sucesso', icone: 'check' },
};

/** A `auditoria` é imutável e o histórico guarda os nomes antigos: aparecem com o rótulo do estado que os substituiu. */
const ESTADO_LEGADO: Record<string, string> = {
  SUBMETIDO: 'ENVIADO_DGT',
  REJEITADO_INCM: 'ANULADO_INCM',
};

/** Rótulo sem badge: a pesquisa local filtra pelo que o utilizador lê, não pelo literal do backend. */
export function rotuloEstado(estado: string): string {
  const atual = ESTADO_LEGADO[estado] ?? estado;
  return ESTADO_INFO[atual]?.label ?? estado;
}

export const ESTADOS_PROCESSO = Object.keys(ESTADO_INFO);

/**
 * O `ESTADO_LABEL` do `painel-administrativo` é uma cópia destes rótulos que já divergiu ("Em validação DGT");
 * passa a `rotuloEstado` quando se decidir a grafia. Estado desconhecido mostra-se tal como veio, nunca em branco.
 */
@Component({
  selector: 'app-estado-processo-badge',
  imports: [SeloComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-selo [tom]="info().tom">
      @switch (info().icone) {
        @case ('relogio') {
          <svg icone viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              fill-rule="evenodd"
              d="M8 15A7 7 0 108 1a7 7 0 000 14zm.75-11a.75.75 0 00-1.5 0v4c0 .2.079.391.22.53l2.5 2.5a.75.75 0 101.06-1.06L8.75 7.69V4z"
              clip-rule="evenodd"
            />
          </svg>
        }
        @case ('seta') {
          <svg icone viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              fill-rule="evenodd"
              d="M4.22 11.78a.75.75 0 010-1.06L10.44 4.5H6a.75.75 0 010-1.5h6a.75.75 0 01.75.75v6a.75.75 0 01-1.5 0V5.56l-6.22 6.22a.75.75 0 01-1.06 0z"
              clip-rule="evenodd"
            />
          </svg>
        }
        @case ('check') {
          <svg icone viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              fill-rule="evenodd"
              d="M13.78 4.22a.75.75 0 010 1.06l-6.5 6.5a.75.75 0 01-1.06 0l-3-3a.75.75 0 111.06-1.06L6.75 10.19l5.97-5.97a.75.75 0 011.06 0z"
              clip-rule="evenodd"
            />
          </svg>
        }
        @case ('x') {
          <svg icone viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              fill-rule="evenodd"
              d="M8 6.94L11.06 3.88a.75.75 0 111.06 1.06L9.06 8l3.06 3.06a.75.75 0 11-1.06 1.06L8 9.06l-3.06 3.06a.75.75 0 01-1.06-1.06L6.94 8 3.88 4.94a.75.75 0 111.06-1.06L8 6.94z"
              clip-rule="evenodd"
            />
          </svg>
        }
      }
      {{ info().label }}
    </app-selo>
  `,
})
export class EstadoProcessoBadgeComponent {
  readonly estado = input.required<string>();

  readonly info = computed(() => {
    const estado = this.estado();
    return ESTADO_INFO[ESTADO_LEGADO[estado] ?? estado] ?? { label: estado, tom: 'neutro' as TomSelo, icone: 'seta' as IconeEstado };
  });
}
