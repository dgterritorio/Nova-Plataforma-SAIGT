import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { DocumentoProcesso } from '../../models/documento.model';
import { Processo } from '../../models/processo.model';
import { BotaoComponent } from '../ui/botao/botao.component';
import { CartaoComponent } from '../ui/cartao/cartao.component';
import { FicheirosRemetidosComponent, textoTotalFicheiros } from './ficheiros-remetidos.component';

/** Recibo pós-submissão. Não há outro número de recibo além do n.º do processo SAIGT. */
@Component({
  selector: 'app-recibo-submissao',
  standalone: true,
  imports: [BotaoComponent, CartaoComponent, DatePipe, FicheirosRemetidosComponent],
  templateUrl: './recibo-submissao.component.html',
  styleUrl: './recibo-submissao.component.css',
})
export class ReciboSubmissaoComponent {
  /** `idus` e `submissao` só vêm no detalhe e mudam na submissão: o chamador passa o detalhe relido depois de submeter. */
  @Input({ required: true }) processo!: Processo & { idus?: string | null; submissao?: number };
  @Input() tipoProcedimento: string | null = null;
  @Input() designacao: string | null = null;
  @Input() tipoInstrumento: string | null = null;
  @Input() concelho: string | null = null;
  @Output() voltar = new EventEmitter<void>();

  readonly totalFicheiros = signal<string | null>(null);
  readonly avisoCopia = signal('');

  aoCarregarFicheiros(lista: DocumentoProcesso[]): void {
    this.totalFicheiros.set(textoTotalFicheiros(lista.length));
  }

  copiarNumero(): void {
    const numero = this.processo.numeroProcesso;
    if (!numero || !navigator.clipboard) {
      this.avisoCopia.set('Não foi possível copiar o número.');
      return;
    }
    navigator.clipboard.writeText(numero).then(
      () => this.avisoCopia.set('Número copiado.'),
      () => this.avisoCopia.set('Não foi possível copiar o número.'),
    );
  }

  imprimir(): void {
    window.print();
  }
}
