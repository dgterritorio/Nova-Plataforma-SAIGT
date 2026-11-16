import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TipoDinamicaSemIgt } from '../../../../models/dinamica.model';
import { TipoInstrumento } from '../../../../models/instrumento.model';
import { BotaoComponent } from '../../../../shared/ui/botao/botao.component';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';
import { DESCRICOES_TIPO_IGT, ORDEM_TIPOS_IGT } from './descricoes-tipo-igt';

/** Os dois grupos do Figma (UI-002/UI-045 e UI-018); um grupo vazio não aparece. Cada grupo é um `fieldset` com `legend`. */
@Component({
  selector: 'app-pagina-tipo-igt',
  imports: [BotaoComponent, ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <div class="cabecalho-pagina">
      <h2>Tipo de Processo</h2>
      @if (podeContinuarSemIgt()) {
        <p class="continuar-sem-igt">
          <button type="button" app-botao variante="texto" tamanho="pequeno" (click)="continuarSemIgt.emit()">
            Continuar uma dinâmica sem associação a IGT ›
          </button>
        </p>
      }
      @if (tipos().length > 0) {
        <p class="grupo-rotulo" aria-hidden="true">Processo com associação a IGT</p>
      }
    </div>
    <p class="subtitulo">Indique o IGT associado ao processo.</p>
    @if (tipos().length > 0) {
      <app-lista-opcoes
        legenda="Processo com associação a IGT"
        [opcoes]="opcoes()"
        [valor]="valor()"
        [desativado]="aCarregar()"
        (valorChange)="escolher.emit($event)"
      />
    }
    @if (semIgt().length > 0) {
      <p class="grupo-rotulo" aria-hidden="true">Processo sem associação a IGT</p>
      <app-lista-opcoes
        legenda="Processo sem associação a IGT"
        [opcoes]="opcoesSemIgt()"
        [valor]="semIgtEscolhido()"
        [desativado]="aCarregar()"
        (valorChange)="escolherSemIgt.emit($event)"
      />
    }
    @if (tipos().length === 0 && semIgt().length === 0) {
      @if (aCarregar()) {
        <p class="estado">A carregar…</p>
      } @else {
        <p class="vazio">A sua entidade não pode iniciar nenhum tipo de processo.</p>
      }
    }
  `,
})
export class PaginaTipoIgtComponent {
  readonly tipos = input.required<readonly TipoInstrumento[]>();
  readonly semIgt = input<readonly TipoDinamicaSemIgt[]>([]);
  readonly tipoEscolhidoId = input<number | null>(null);
  /** Código do tipo de Dinâmica escolhido no grupo sem IGT. */
  readonly semIgtEscolhido = input<string | null>(null);
  readonly podeContinuarSemIgt = input(false);
  readonly aCarregar = input(false);
  readonly escolher = output<string | null>();
  readonly escolherSemIgt = output<string | null>();
  readonly continuarSemIgt = output<void>();

  readonly valor = computed(() => (this.tipoEscolhidoId() === null ? null : String(this.tipoEscolhidoId())));
  readonly opcoes = computed<OpcaoLista[]>(() =>
    [...this.tipos()].sort((a, b) => posicao(a.codigo) - posicao(b.codigo)).map((tipo) => ({
      valor: String(tipo.id),
      titulo: tipo.nome,
      etiqueta: tipo.codigo,
      descricao: DESCRICOES_TIPO_IGT[tipo.codigo],
    })),
  );
  readonly opcoesSemIgt = computed<OpcaoLista[]>(() => this.semIgt().map((tipo) => ({ valor: tipo.codigo, titulo: tipo.nome })));
}

function posicao(codigo: string): number {
  const indice = ORDEM_TIPOS_IGT.indexOf(codigo);
  return indice === -1 ? ORDEM_TIPOS_IGT.length : indice;
}
