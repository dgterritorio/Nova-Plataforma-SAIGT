import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

export type LarguraModal = 'sm' | 'md' | 'lg' | 'xl';
export type VarianteModal = 'normal' | 'confirmacao';

const FOCAVEIS = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

let proximoId = 0;

/** Um modal aberto por cima de outro (ex.: editar um contacto na lista de contactos) fica com o Esc e o foco. */
const pilha: ModalComponent[] = [];

/**
 * Diálogo modal (WAI-ARIA "dialog"): o foco fica preso na caixa, Esc fecha, o foco volta a quem o abriu
 * e a página por trás não faz scroll. Quem o usa controla `aberto` e fecha-o ao receber `fechar`.
 */
@Component({
  selector: 'app-modal',
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // No documento e não na caixa: se o botão com foco ficar desativado (ex.: "A processar…"), o foco cai no body.
  host: {
    '(document:keydown)': 'teclar($event)',
    '(document:focusin)': 'aoFocar($event)',
  },
})
export class ModalComponent implements OnDestroy {
  private readonly documento = inject(DOCUMENT);
  private readonly detecao = inject(ChangeDetectorRef);

  readonly aberto = input(false);
  readonly titulo = input.required<string>();
  /** Por baixo do título; anunciado como descrição do diálogo. */
  readonly subtitulo = input<string>();
  /** `id` de um elemento do conteúdo que descreve o diálogo, quando não há subtítulo. */
  readonly descritoPor = input<string>();
  readonly largura = input<LarguraModal>('md');
  readonly variante = input<VarianteModal>('normal');
  readonly fecharComFundo = input(true);
  readonly fechar = output<void>();

  readonly idTitulo = `modal-titulo-${++proximoId}`;
  readonly idSubtitulo = `${this.idTitulo}-subtitulo`;
  private readonly caixa = viewChild<ElementRef<HTMLElement>>('caixa');
  private readonly corpo = viewChild<ElementRef<HTMLElement>>('corpo');
  private readonly conteudo = viewChild<ElementRef<HTMLElement>>('conteudo');
  /** Com scroll, o corpo entra no Tab para quem usa só o teclado o conseguir percorrer com as setas. */
  readonly comScroll = signal(false);

  private estaAberto = false;
  private abridor: HTMLElement | null = null;
  private overflowAnterior = '';

  constructor() {
    effect(() => {
      if (this.aberto()) {
        this.aoAbrir();
      } else {
        this.aoFechar();
      }
    });
    // O conteúdo é observado à parte: quando já passa da altura máxima, o corpo deixa de mudar de tamanho.
    effect((aoLimpar) => {
      const corpo = this.corpo()?.nativeElement;
      const conteudo = this.conteudo()?.nativeElement;
      if (!corpo || !conteudo || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observador = new ResizeObserver(() => this.medirScroll());
      observador.observe(corpo);
      observador.observe(conteudo);
      aoLimpar(() => observador.disconnect());
    });
  }

  ngOnDestroy(): void {
    this.aoFechar();
  }

  clicarFundo(evento: MouseEvent): void {
    if (this.fecharComFundo() && evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  teclar(evento: KeyboardEvent): void {
    if (!this.estaAberto || !this.noTopo()) {
      return;
    }
    if (evento.key === 'Escape') {
      evento.stopPropagation();
      this.fechar.emit();
    } else if (evento.key === 'Tab') {
      this.prenderFoco(evento);
    }
  }

  /** O foco que sai da caixa (por programa ou rato) volta a ela. */
  aoFocar(evento: FocusEvent): void {
    const caixa = this.caixa()?.nativeElement;
    if (this.estaAberto && this.noTopo() && caixa && evento.target instanceof Node && !caixa.contains(evento.target)) {
      caixa.focus();
    }
  }

  private aoAbrir(): void {
    if (this.estaAberto) {
      return;
    }
    this.estaAberto = true;
    pilha.push(this);
    const ativo = this.documento.activeElement;
    this.abridor = ativo instanceof HTMLElement ? ativo : null;
    this.overflowAnterior = this.documento.body.style.overflow;
    this.documento.body.style.overflow = 'hidden';
    // A caixa só existe depois da deteção de alterações que o `aberto` dispara.
    setTimeout(() => {
      if (!this.estaAberto) {
        return;
      }
      this.medirScroll();
      // O tabindex do corpo tem de estar no DOM antes do focus(), senão o fallback para o corpo não faz nada.
      this.detecao.detectChanges();
      this.focarInicio();
    });
  }

  private aoFechar(): void {
    if (!this.estaAberto) {
      return;
    }
    this.estaAberto = false;
    pilha.splice(pilha.indexOf(this), 1);
    this.comScroll.set(false);
    this.documento.body.style.overflow = this.overflowAnterior;
    if (this.abridor?.isConnected) {
      this.abridor.focus();
    }
    this.abridor = null;
  }

  /** O primeiro campo ou botão do corpo ou do rodapé; o × só se não houver outro; o corpo com scroll só se não houver nenhum. */
  private noTopo(): boolean {
    return pilha[pilha.length - 1] === this;
  }

  private focarInicio(): void {
    const caixa = this.caixa()?.nativeElement;
    if (!caixa) {
      return;
    }
    const corpo = this.corpo()?.nativeElement;
    const focaveis = this.focaveis().filter((el) => el !== corpo);
    const alvo = focaveis.find((el) => el.closest('.modal-corpo, .modal-rodape'));
    (alvo ?? focaveis[0] ?? (this.comScroll() ? corpo : undefined) ?? caixa).focus();
  }

  private medirScroll(): void {
    const corpo = this.corpo()?.nativeElement;
    this.comScroll.set(!!corpo && corpo.scrollHeight > corpo.clientHeight);
  }

  private prenderFoco(evento: KeyboardEvent): void {
    const focaveis = this.focaveis();
    if (focaveis.length === 0) {
      evento.preventDefault();
      return;
    }
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    const ativo = this.documento.activeElement;
    if (!this.caixa()?.nativeElement.contains(ativo)) {
      evento.preventDefault();
      (evento.shiftKey ? ultimo : primeiro).focus();
    } else if (evento.shiftKey && (ativo === primeiro || ativo === this.caixa()?.nativeElement)) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && ativo === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }

  private focaveis(): HTMLElement[] {
    return [...(this.caixa()?.nativeElement.querySelectorAll<HTMLElement>(FOCAVEIS) ?? [])];
  }
}
