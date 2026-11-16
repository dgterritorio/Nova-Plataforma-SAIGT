import { formatDate } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  LOCALE_ID,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DinamicaSemIgtEmCurso } from '../../../../models/dinamica.model';
import { ProcedimentoDisponivel, TentativaEmCurso } from '../../../../models/instrumento.model';
import { ItemTerritorio } from '../../../../models/processo.model';
import { AlertaComponent } from '../../../../shared/ui/alerta/alerta.component';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';
import { OpcaoSeletor, SeletorComponent } from '../../../../shared/ui/seletor/seletor.component';

/**
 * Continuar uma Dinâmica iniciada sem IGT (ex.: a Prorrogação de uma Suspensão): o «Concelho» filtra pelos concelhos dos
 * Instrumentos associados e cada Dinâmica mostra o ato publicado, que é o «Selecionar ato» da EF.
 */
@Component({
  selector: 'app-pagina-continuar-sem-igt',
  imports: [AlertaComponent, FormsModule, ListaOpcoesComponent, SeletorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <div class="cabecalho-pagina">
      <h2 #titulo tabindex="-1">Continuar Dinâmica sem associação a IGT</h2>
      <p class="grupo-rotulo" aria-hidden="true">Processo sem associação a IGT</p>
    </div>
    <p class="subtitulo">Selecione a dinâmica em curso e o procedimento a iniciar.</p>

    @if (opcoesConcelho().length > 0) {
      <app-seletor
        rotulo="Concelho"
        placeholder="Todos os concelhos"
        [opcoes]="opcoesConcelho()"
        [ngModel]="concelho()"
        (ngModelChange)="filtrarPorConcelho($event)"
      />
    }

    <p class="grupo-rotulo" aria-hidden="true">Dinâmicas em curso</p>
    @if (opcoesDinamica().length > 0) {
      <app-lista-opcoes
        legenda="Dinâmicas em curso sem associação a IGT"
        [opcoes]="opcoesDinamica()"
        [valor]="dinamicaId()"
        (valorChange)="escolherDinamica.emit($event)"
      />
    } @else {
      <p class="vazio">{{ concelho() ? 'Nenhuma dinâmica em curso abrange este concelho.' : 'Não há dinâmicas sem associação a IGT em curso.' }}</p>
    }

    @if (dinamicaId()) {
      <hr class="divisor" />
      <p class="grupo-rotulo" aria-hidden="true">Procedimento a iniciar</p>
      @if (procedimentos().length > 0) {
        <app-lista-opcoes
          legenda="Procedimento a iniciar"
          [opcoes]="opcoesProcedimento()"
          [valor]="procedimentoId()"
          (valorChange)="escolherProcedimento.emit($event)"
        />
      } @else {
        <p class="vazio">Sem Procedimentos disponíveis nesta Dinâmica.</p>
      }
    }

    @if (procedimentoId()) {
      @if (tentativaEmCurso(); as tentativa) {
        <app-alerta tipo="aviso">
          Este Procedimento já tem um rascunho gravado (tentativa n.º {{ tentativa.numeroTentativa }}). Escolhê-lo retoma-o no passo seguinte
          — não cria uma tentativa nova.
        </app-alerta>
      }
    }
  `,
})
export class PaginaContinuarSemIgtComponent {
  private readonly localizacao = inject(LOCALE_ID);
  private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

  readonly dinamicas = input.required<readonly DinamicaSemIgtEmCurso[]>();
  /** Nomes dos concelhos pelo código DTCC; um código sem nome mostra-se tal como vem. */
  readonly nomesConcelhos = input<readonly ItemTerritorio[]>([]);
  readonly dinamicaId = input<string | null>(null);
  readonly procedimentos = input<readonly ProcedimentoDisponivel[]>([]);
  readonly procedimentoId = input<string | null>(null);
  readonly tentativaEmCurso = input<TentativaEmCurso | null>(null);

  readonly escolherDinamica = output<string | null>();
  readonly escolherProcedimento = output<string | null>();

  readonly concelho = signal<string | null>(null);

  readonly opcoesConcelho = computed<OpcaoSeletor[]>(() => {
    const codigos = new Set(this.dinamicas().flatMap((dinamica) => dinamica.instrumentos.flatMap((instrumento) => instrumento.concelhos)));
    return [...codigos]
      .map((codigo) => ({ valor: codigo, rotulo: this.nomeConcelho(codigo) }))
      .sort((a, b) => a.rotulo.localeCompare(b.rotulo, 'pt'));
  });

  readonly dinamicasFiltradas = computed(() => {
    const concelho = this.concelho();
    return concelho
      ? this.dinamicas().filter((dinamica) => dinamica.instrumentos.some((instrumento) => instrumento.concelhos.includes(concelho)))
      : this.dinamicas();
  });

  readonly opcoesDinamica = computed<OpcaoLista[]>(() =>
    this.dinamicasFiltradas().map((dinamica) => ({
      valor: dinamica.dinamicaId,
      titulo: dinamica.nomeDinamica,
      descricao: dinamica.descritivo,
      citacao: true,
      detalhe: this.instrumentos(dinamica),
      data: this.data(dinamica.dataInicio),
      nota: this.atoPublicado(dinamica),
    })),
  );

  readonly opcoesProcedimento = computed<OpcaoLista[]>(() =>
    this.procedimentos().map((procedimento) => ({
      valor: String(procedimento.instrumentoProcedimentoConfigId),
      titulo: procedimento.nomeProcedimento,
      descricao: procedimento.tentativaEmCurso ? `Retomar rascunho n.º ${procedimento.tentativaEmCurso.numeroTentativa}` : undefined,
    })),
  );

  constructor() {
    afterNextRender(() => this.titulo().nativeElement.focus());
  }

  filtrarPorConcelho(valor: string | null): void {
    this.concelho.set(valor || null);
    const escolhida = this.dinamicaId();
    if (escolhida && !this.dinamicasFiltradas().some((dinamica) => dinamica.dinamicaId === escolhida)) {
      this.escolherDinamica.emit(null);
    }
  }

  private nomeConcelho(codigo: string): string {
    return this.nomesConcelhos().find((item) => item.codigo === codigo)?.nome ?? codigo;
  }

  private instrumentos(dinamica: DinamicaSemIgtEmCurso): string {
    return dinamica.instrumentos.length === 0
      ? 'Sem IGT associado'
      : `Instrumentos: ${dinamica.instrumentos.map((instrumento) => `${instrumento.designacao} (${instrumento.tipoInstrumento})`).join('; ')}`;
  }

  private atoPublicado(dinamica: DinamicaSemIgtEmCurso): OpcaoLista['nota'] {
    const ato = dinamica.atoPublicado;
    if (!ato) {
      return { rotulo: 'Ato publicado:', valor: 'nenhum', tom: 'aviso' };
    }
    const data = ato.dataPublicacaoDr ? ` de ${this.data(ato.dataPublicacaoDr)}` : '';
    return { rotulo: 'Ato publicado:', valor: `${ato.numeroDr ?? 'DR sem número'}${data}`, tom: 'sucesso' };
  }

  private data(valor: string): string {
    return formatDate(valor, 'dd/MM/yyyy', this.localizacao);
  }
}
