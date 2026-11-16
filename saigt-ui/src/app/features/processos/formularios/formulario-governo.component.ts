import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { InstrumentoAbrangido } from '../../../models/processo.model';

let proximoId = 0;

export type VarianteFormularioGoverno = 'PUBLICACAO' | 'PRORROGACAO';

const TITULOS: Record<VarianteFormularioGoverno, string> = {
  PUBLICACAO: 'Suspensão / Medidas Preventivas (Governo)',
  PRORROGACAO: 'Prorrogação da Suspensão / Medidas Preventivas (Governo)',
};

/**
 * Formulários fixos `SUSPENSAO_MP_GOVERNO` e `PRORROGACAO_GOVERNO`: a secção com o Tipo de instrumento (os tipos dos
 * Instrumentos associados), o Tipo de procedimento, a Designação (o descritivo da Dinâmica) e a lista dos Instrumentos
 * associados, só de leitura, e por baixo o cabeçalho comum da Informação Geral, que o ecrã projeta e onde estão os
 * campos (na Prorrogação sem a «Publicação»). O cartão projetado continua no ecrã que o declara.
 */
@Component({
  selector: 'app-formulario-governo',
  standalone: true,
  templateUrl: './formulario-governo.component.html',
  styleUrl: './formulario-governo.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioGovernoComponent {
  readonly variante = input<VarianteFormularioGoverno>('PUBLICACAO');
  /** Nome do tipo da configuração da Dinâmica, quando ainda não há Instrumentos associados. */
  readonly tipoInstrumento = input<string | null>(null);
  readonly tipoProcedimento = input<string | null>(null);
  readonly designacao = input<string | null>(null);
  readonly instrumentosAbrangidos = input<InstrumentoAbrangido[] | null>(null);
  /** Código → nome dos tipos de Instrumento; sem ele fica o código. */
  readonly nomesTipos = input<Record<string, string>>({});
  /** No assistente, a secção só aparece na página do cabeçalho; o cabeçalho projetado decide o resto. */
  readonly seccaoVisivel = input(true);

  readonly idTitulo = `governo-titulo-${++proximoId}`;
  readonly titulo = computed(() => TITULOS[this.variante()]);

  nomeTipo(codigo: string): string {
    return this.nomesTipos()[codigo] ?? codigo;
  }

  /** Os tipos dos Instrumentos associados, sem repetidos e pela ordem da lista; sem eles, o tipo da Dinâmica. */
  rotuloTiposInstrumento(): string {
    const codigos = [...new Set((this.instrumentosAbrangidos() ?? []).map((instrumento) => instrumento.tipoInstrumento))];
    if (codigos.length === 0) {
      return this.tipoInstrumento() || '—';
    }
    return codigos.map((codigo) => this.nomeTipo(codigo)).join(', ');
  }
}
