import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let proximoId = 0;

/**
 * Formulário fixo `SRUP` (restantes servidões): a secção «Servidão», só de leitura, e por baixo o cabeçalho comum
 * da Informação Geral, que o ecrã projeta. O cartão projetado continua no ecrã que o declara, e por isso a gravação
 * segue pelo mesmo `dados()` do formulário `INFORMACAO_GERAL`; este formulário não tem campos próprios.
 */
@Component({
  selector: 'app-processo-formulario-srup',
  standalone: true,
  templateUrl: './processo-formulario-srup.component.html',
  styleUrl: './processo-formulario-srup.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessoFormularioSrupComponent {
  /** Nome do tipo do Instrumento do processo. */
  readonly tipoInstrumento = input<string | null>(null);
  /** Nome da Dinâmica do processo. */
  readonly dinamica = input<string | null>(null);
  /** No assistente, a secção só aparece na página do cabeçalho; o cabeçalho projetado decide o resto. */
  readonly servidaoVisivel = input(true);

  readonly idTitulo = `servidao-titulo-${++proximoId}`;
}
