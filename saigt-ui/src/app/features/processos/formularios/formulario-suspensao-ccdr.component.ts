import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { InstrumentoAbrangido } from '../../../models/processo.model';

let proximoId = 0;

/**
 * Formulário fixo `SUSPENSAO_CCDR`: a secção «Suspensão da iniciativa da CCDR», com o Tipo de instrumento (os tipos
 * dos planos associados), o Tipo de procedimento e a Designação (o descritivo da Dinâmica) só de leitura, e por baixo
 * o cabeçalho comum da Informação Geral, que o ecrã projeta e onde estão a entidade emitente da lista das CCDR e a
 * data de assinatura. O cartão projetado continua no ecrã que o declara.
 */
@Component({
  selector: 'app-formulario-suspensao-ccdr',
  standalone: true,
  templateUrl: './formulario-suspensao-ccdr.component.html',
  styleUrl: './formulario-suspensao-ccdr.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioSuspensaoCcdrComponent {
  /** Nome do tipo da configuração da Dinâmica, quando ainda não há planos associados. */
  readonly tipoInstrumento = input<string | null>(null);
  readonly tipoProcedimento = input<string | null>(null);
  readonly designacao = input<string | null>(null);
  readonly instrumentosAbrangidos = input<InstrumentoAbrangido[] | null>(null);
  /** Código → nome dos tipos de Instrumento; sem ele fica o código. */
  readonly nomesTipos = input<Record<string, string>>({});
  /** No assistente, a secção só aparece na página do cabeçalho; o cabeçalho projetado decide o resto. */
  readonly seccaoVisivel = input(true);

  readonly idTitulo = `suspensao-ccdr-titulo-${++proximoId}`;

  /** Os tipos dos planos associados, sem repetidos e pela ordem da lista; sem planos, o tipo da Dinâmica. */
  rotuloTiposInstrumento(): string {
    const codigos = [...new Set((this.instrumentosAbrangidos() ?? []).map((instrumento) => instrumento.tipoInstrumento))];
    if (codigos.length === 0) {
      return this.tipoInstrumento() || '—';
    }
    const nomes = this.nomesTipos();
    return codigos.map((codigo) => nomes[codigo] ?? codigo).join(', ');
  }
}
