import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfiguracaoSemIgt, TipoDinamicaSemIgt } from '../../../../models/dinamica.model';
import { CampoTextoComponent } from '../../../../shared/ui/campo-texto/campo-texto.component';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';

/** Nestes tipos o descritivo é a Designação da Dinâmica e não se repete no mesmo tipo (EF v1.5: «Escrita Livre, mas deverá ser única»). */
const TIPOS_COM_DESIGNACAO: readonly string[] = ['SUSPENSAO_CCDR', 'SUSPENSAO_GOVERNO', 'MEDIDAS_PREVENTIVAS_GOVERNO'];

export const LIMITE_DESCRITIVO_DINAMICA = 50;

/**
 * Só pede o tipo de instrumento quando a Dinâmica abrange exatamente um Instrumento (`UM`, a Ratificação): nos outros
 * tipos é valor fixo e segue a primeira configuração. Os Instrumentos associam-se depois do formulário inicial.
 */
export function pedeTipoInstrumento(tipo: TipoDinamicaSemIgt | null): boolean {
  return !!tipo && tipo.configuracoes.length > 0 && tipo.configuracoes.every((configuracao) => configuracao.associacaoInstrumentos === 'UM');
}

export function configuracaoFixa(tipo: TipoDinamicaSemIgt | null): ConfiguracaoSemIgt | null {
  return tipo && !pedeTipoInstrumento(tipo) ? (tipo.configuracoes[0] ?? null) : null;
}

export function rotuloDescritivoSemIgt(codigoTipoDinamica: string | null | undefined): string {
  return codigoTipoDinamica && TIPOS_COM_DESIGNACAO.includes(codigoTipoDinamica) ? 'Designação' : 'Descritivo da dinâmica';
}

@Component({
  selector: 'app-pagina-dados-dinamica-sem-igt',
  imports: [CampoTextoComponent, FormsModule, ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <div class="cabecalho-pagina">
      <h2 #titulo tabindex="-1">{{ tipo().nome }}</h2>
      <p class="grupo-rotulo" aria-hidden="true">Processo sem associação a IGT</p>
    </div>
    <p class="subtitulo">
      Os planos ou programas abrangidos associam-se depois do formulário inicial, no ecrã dos documentos.
    </p>

    @if (pedeTipo()) {
      <p class="grupo-rotulo" aria-hidden="true">Tipo de instrumento</p>
      <app-lista-opcoes
        legenda="Tipo de instrumento"
        [opcoes]="opcoesTipoInstrumento()"
        [valor]="valorConfiguracao()"
        (valorChange)="escolherConfiguracao.emit($event)"
      />
    }

    <app-campo-texto
      [rotulo]="rotuloDescritivo()"
      ajuda="Até 50 caracteres. Aparece em todos os processos da Dinâmica."
      [obrigatorio]="true"
      [maxlength]="limite"
      [erro]="erroDescritivo()"
      [ngModel]="descritivo()"
      (ngModelChange)="alterarDescritivo.emit($event)"
    />
    <p class="visualmente-oculto" role="alert">{{ erroDescritivo() ?? '' }}</p>
  `,
})
export class PaginaDadosDinamicaSemIgtComponent {
  private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

  readonly tipo = input.required<TipoDinamicaSemIgt>();
  readonly configuracaoId = input<number | null>(null);
  readonly descritivo = input('');
  /** `DESIGNACAO_DUPLICADA` e outros erros do servidor no campo `descritivo`. */
  readonly erroDescritivo = input<string | null>(null);
  readonly escolherConfiguracao = output<string | null>();
  readonly alterarDescritivo = output<string>();

  readonly limite = LIMITE_DESCRITIVO_DINAMICA;
  readonly valorConfiguracao = computed(() => (this.configuracaoId() === null ? null : String(this.configuracaoId())));
  readonly pedeTipo = computed(() => pedeTipoInstrumento(this.tipo()));
  readonly rotuloDescritivo = computed(() => rotuloDescritivoSemIgt(this.tipo().codigo));
  readonly opcoesTipoInstrumento = computed<OpcaoLista[]>(() =>
    this.tipo().configuracoes.map((configuracao) => ({
      valor: String(configuracao.instrumentoDinamicaConfigId),
      titulo: configuracao.tipoInstrumentoNome,
      etiqueta: configuracao.tipoInstrumentoCodigo,
    })),
  );

  constructor() {
    afterNextRender(() => this.titulo().nativeElement.focus());
  }
}
