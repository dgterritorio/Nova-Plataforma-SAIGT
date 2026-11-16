import { Component, ElementRef, Injector, afterNextRender, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
import { FormatoExportacao } from '../../../services/processo.service';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';

interface OpcaoFormato {
  formato: FormatoExportacao;
  rotulo: string;
}

const OPCOES: readonly OpcaoFormato[] = [
  { formato: 'EXCEL', rotulo: 'Excel' },
  { formato: 'PDF', rotulo: 'PDF' },
];

@Component({
  selector: 'app-menu-exportar',
  imports: [BotaoComponent],
  styleUrl: './menu-exportar.component.css',
  host: { '(document:click)': 'aoClicarFora($event)' },
  template: `
    <button
      #botao
      type="button"
      app-botao
      variante="contorno"
      aria-label="Exportar"
      aria-haspopup="menu"
      aria-controls="menu-exportar"
      [attr.aria-expanded]="aberto()"
      [aCarregar]="aExportar()"
      (click)="alternar()"
      (keydown.arrowDown)="abrir(0, $event)"
      (keydown.arrowUp)="abrir(opcoes.length - 1, $event)"
    >
      <svg icone viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path
          fill-rule="evenodd"
          d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z"
          clip-rule="evenodd"
        />
        <path
          d="M3.5 16.75a.75.75 0 00-1.5 0v.5A2.75 2.75 0 004.75 20h10.5a2.75 2.75 0 002.75-2.75v-.5a.75.75 0 00-1.5 0v.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-.5z"
        />
      </svg>
      Exportar
    </button>

    <ul id="menu-exportar" class="menu" role="menu" aria-label="Formato de exportação" [hidden]="!aberto()">
      @for (opcao of opcoes; track opcao.formato) {
        <li role="none">
          <button
            #item
            type="button"
            class="item"
            role="menuitem"
            tabindex="-1"
            (click)="escolher(opcao.formato)"
            (keydown)="aoTeclarNoMenu($event)"
          >
            {{ opcao.rotulo }}
          </button>
        </li>
      }
    </ul>
  `,
})
export class MenuExportarComponent {
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly botao = viewChild.required('botao', { read: ElementRef<HTMLButtonElement> });
  private readonly itens = viewChildren<ElementRef<HTMLButtonElement>>('item');

  readonly aExportar = input(false);
  readonly formatoEscolhido = output<FormatoExportacao>();

  readonly opcoes = OPCOES;
  readonly aberto = signal(false);

  alternar(): void {
    if (this.aberto()) {
      this.fechar(false);
    } else {
      this.abrir(0);
    }
  }

  abrir(indice: number, evento?: Event): void {
    evento?.preventDefault();
    if (this.aExportar()) {
      return;
    }
    this.aberto.set(true);
    afterNextRender(() => this.focar(indice), { injector: this.injector });
  }

  fechar(devolverFoco: boolean): void {
    if (!this.aberto()) {
      return;
    }
    this.aberto.set(false);
    if (devolverFoco) {
      this.botao().nativeElement.focus();
    }
  }

  escolher(formato: FormatoExportacao): void {
    this.fechar(true);
    this.formatoEscolhido.emit(formato);
  }

  aoTeclarNoMenu(evento: KeyboardEvent): void {
    const total = this.opcoes.length;
    const atual = this.itens().findIndex((item) => item.nativeElement === this.elemento.nativeElement.ownerDocument.activeElement);
    switch (evento.key) {
      case 'ArrowDown':
        this.focar((atual + 1) % total);
        break;
      case 'ArrowUp':
        this.focar((atual - 1 + total) % total);
        break;
      case 'Home':
        this.focar(0);
        break;
      case 'End':
        this.focar(total - 1);
        break;
      case 'Escape':
        this.fechar(true);
        break;
      case 'Tab':
        this.fechar(false);
        return;
      default:
        return;
    }
    evento.preventDefault();
  }

  aoClicarFora(evento: MouseEvent): void {
    if (this.aberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.fechar(false);
    }
  }

  private focar(indice: number): void {
    this.itens()[indice]?.nativeElement.focus();
  }
}
