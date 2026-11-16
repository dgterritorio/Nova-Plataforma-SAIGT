import { ChangeDetectionStrategy, Component, ElementRef, input, model, output, viewChildren } from '@angular/core';

export type VarianteSeparadores = 'sublinhado' | 'pilula';

export interface Separador {
  id: string;
  etiqueta: string;
  contador?: number;
  /** Caminho de um SVG em `public/`; é usado como máscara e pinta-se com a cor do texto. */
  icone?: string;
}

let proximoId = 0;

/**
 * Padrão WAI-ARIA "tabs" com ativação manual: as setas, Home e End só mudam o foco; Enter, Espaço ou
 * clique ativam. O painel fica no ecrã, com `idPainel(id)` e `aria-labelledby` = `idSeparador(id)`.
 */
@Component({
  selector: 'app-separadores',
  templateUrl: './separadores.component.html',
  styleUrl: './separadores.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'separadores-' + variante()" },
})
export class SeparadoresComponent {
  readonly separadores = input.required<readonly Separador[]>();
  readonly ativo = model.required<string>();
  readonly rotulo = input<string | null>(null);
  /** `pilula`: separadores em botão com fundo, sem linha por baixo (Figma UI-056). */
  readonly variante = input<VarianteSeparadores>('sublinhado');
  readonly mudou = output<string>();

  private readonly base = `separadores-${++proximoId}`;
  private readonly botoes = viewChildren<ElementRef<HTMLButtonElement>>('botao');

  idSeparador(id: string): string {
    return `${this.base}-separador-${id}`;
  }

  idPainel(id: string): string {
    return `${this.base}-painel-${id}`;
  }

  ativar(id: string): void {
    if (id !== this.ativo()) {
      this.ativo.set(id);
      this.mudou.emit(id);
    }
  }

  teclar(evento: KeyboardEvent, indice: number): void {
    if (evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      this.ativar(this.separadores()[indice].id);
      return;
    }
    const total = this.separadores().length;
    const destino = {
      ArrowRight: (indice + 1) % total,
      ArrowLeft: (indice - 1 + total) % total,
      Home: 0,
      End: total - 1,
    }[evento.key];
    if (destino !== undefined) {
      evento.preventDefault();
      this.botoes()[destino]?.nativeElement.focus();
    }
  }
}
