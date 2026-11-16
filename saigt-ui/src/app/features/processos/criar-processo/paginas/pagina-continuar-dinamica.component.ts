import { formatDate } from '@angular/common';
import { ChangeDetectionStrategy, Component, LOCALE_ID, computed, inject, input, output, signal } from '@angular/core';
import { DinamicaEmCurso, ProcedimentoDisponivel, TentativaEmCurso } from '../../../../models/instrumento.model';
import { DataCaducidade, situacaoCaducidade, textoPrazo } from '../caducidade';
import { AlertaComponent } from '../../../../shared/ui/alerta/alerta.component';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';

@Component({
  selector: 'app-pagina-continuar-dinamica',
  imports: [AlertaComponent, ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <h2>Continuar Fase da Dinâmica</h2>
    <p class="subtitulo">Selecione a dinâmica em curso e o procedimento a iniciar.</p>

    <p class="grupo-rotulo">Dinâmicas em curso</p>
    @if (dinamicas().length > 0) {
      <app-lista-opcoes legenda="Dinâmica em curso" [opcoes]="opcoesDinamica()" [valor]="dinamicaId()" (valorChange)="escolherDinamica.emit($event)" />
      @if (caducidadeProximaEscolhida(); as data) {
        <app-alerta tipo="info">
          A Dinâmica caduca em <time [attr.datetime]="data.iso">{{ data.texto }}</time>. A caducidade não impede a submissão.
        </app-alerta>
      }
    } @else {
      <p class="vazio">Este plano não tem Dinâmicas em curso.</p>
    }

    @if (caducadas().length > 0) {
      <p class="grupo-rotulo grupo-rotulo-caducadas">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.33" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="8" cy="8" r="6" />
          <path d="M8 5v3l2 1.33" />
        </svg>
        Dinâmicas caducadas
      </p>
      <app-lista-opcoes legenda="Dinâmicas caducadas" [opcoes]="opcoesCaducadas()" [desativado]="true" />
    }

    @if (dinamicaId()) {
      <hr class="divisor" />
      <p class="grupo-rotulo">Procedimento a iniciar</p>
      @if (procedimentos().length > 0) {
        @if (opcoesProcedimentoSeguinte().length > 0) {
          <app-lista-opcoes
            legenda="Procedimento a iniciar"
            [opcoes]="opcoesProcedimentoSeguinte()"
            [valor]="procedimentoId()"
            (valorChange)="escolher($event, false)"
          />
        } @else {
          <p class="vazio">Sem procedimento seguinte disponível nesta Dinâmica.</p>
        }
        @if (opcoesOutrosAtos().length > 0) {
          <p class="grupo-rotulo">Outros atos e anexos</p>
          <app-lista-opcoes
            legenda="Outros atos e anexos"
            [opcoes]="opcoesOutrosAtos()"
            [valor]="procedimentoId()"
            (valorChange)="escolher($event, true)"
          />
        }
        <p class="visualmente-oculto" aria-live="polite">{{ anuncioTroca() }}</p>
      } @else {
        <p class="vazio">Sem Procedimentos disponíveis nesta Dinâmica.</p>
      }
      @if (terminadaEscolhida()) {
        <app-alerta tipo="info" class="alerta-terminada">
          Esta dinâmica já foi publicada. A prorrogação não a reabre: a dinâmica continua terminada.
        </app-alerta>
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
export class PaginaContinuarDinamicaComponent {
  private readonly localizacao = inject(LOCALE_ID);
  private readonly hoje = new Date();

  readonly dinamicas = input.required<readonly DinamicaEmCurso[]>();
  readonly caducadas = input<readonly DinamicaEmCurso[]>([]);
  /** Nome do último Procedimento publicado de cada Dinâmica, pelo `dinamicaId`. */
  readonly ultimosConcluidos = input<Readonly<Record<string, string>>>({});
  readonly dinamicaId = input<string | null>(null);
  /** Por `ordem`; os atos pontuais vão para «Outros atos e anexos». */
  readonly procedimentos = input<readonly ProcedimentoDisponivel[]>([]);
  readonly procedimentoId = input<string | null>(null);
  readonly tentativaEmCurso = input<TentativaEmCurso | null>(null);

  readonly escolherDinamica = output<string | null>();
  readonly escolherProcedimento = output<string | null>();

  /** Os dois grupos partilham uma só escolha: trocar de grupo anuncia a limpeza do outro, sem mover o foco. */
  readonly anuncioTroca = signal('');

  readonly opcoesDinamica = computed<OpcaoLista[]>(() =>
    this.dinamicas().map((dinamica) => {
      const ultimo = this.ultimosConcluidos()[dinamica.dinamicaId];
      return {
        valor: dinamica.dinamicaId,
        titulo: dinamica.nomeDinamica,
        descricao: dinamica.descritivo,
        citacao: true,
        data: this.data(dinamica.dataInicio),
        nota: ultimo ? { rotulo: 'Último concluído:', valor: ultimo, tom: 'sucesso' as const } : undefined,
        prazo: this.prazo(dinamica),
        ...(dinamica.estado === 'TERMINADA' ? { selo: 'Terminada', detalhe: 'Publicada; pode ser prorrogada.' } : {}),
      };
    }),
  );

  /** Uma Dinâmica terminada só aparece aqui com Procedimentos posteriores ao final (a Prorrogação). */
  readonly terminadaEscolhida = computed(
    () => this.dinamicas().find((item) => item.dinamicaId === this.dinamicaId())?.estado === 'TERMINADA',
  );

  readonly caducidadeProximaEscolhida = computed<DataCaducidade | null>(() => {
    const dinamica = this.dinamicas().find((item) => item.dinamicaId === this.dinamicaId());
    const situacao = dinamica ? situacaoCaducidade(dinamica.dataCaducidade, dinamica.estado, this.hoje) : null;
    return situacao?.tipo === 'PROXIMA' ? situacao.data : null;
  });

  readonly opcoesCaducadas = computed<OpcaoLista[]>(() =>
    this.caducadas().map((dinamica) => ({
      valor: dinamica.dinamicaId,
      titulo: dinamica.nomeDinamica,
      descricao: dinamica.descritivo,
      citacao: true,
      data: this.data(dinamica.dataInicio),
      nota: dinamica.dataCaducidade ? { rotulo: 'Data de caducidade', valor: this.data(dinamica.dataCaducidade), tom: 'aviso' as const } : undefined,
    })),
  );

  /** Os atos pontuais iniciam-se a qualquer altura, fora do sequenciamento da Dinâmica. */
  readonly opcoesProcedimentoSeguinte = computed(() => this.opcoes(this.procedimentos().filter((procedimento) => !procedimento.atoPontual)));
  readonly opcoesOutrosAtos = computed(() => this.opcoes(this.procedimentos().filter((procedimento) => procedimento.atoPontual)));

  escolher(valor: string | null, deOutrosAtos: boolean): void {
    const anterior = this.procedimentos().find((procedimento) => String(procedimento.instrumentoProcedimentoConfigId) === this.procedimentoId());
    this.anuncioTroca.set(
      anterior && anterior.atoPontual !== deOutrosAtos
        ? deOutrosAtos
          ? 'O procedimento seguinte deixou de estar escolhido.'
          : 'O ato de «Outros atos e anexos» e o ato a que respeita deixaram de estar escolhidos.'
        : '',
    );
    this.escolherProcedimento.emit(valor);
  }

  private opcoes(procedimentos: readonly ProcedimentoDisponivel[]): OpcaoLista[] {
    return procedimentos.map((procedimento) => ({
      valor: String(procedimento.instrumentoProcedimentoConfigId),
      titulo: procedimento.nomeProcedimento,
      descricao: procedimento.tentativaEmCurso ? `Retomar rascunho n.º ${procedimento.tentativaEmCurso.numeroTentativa}` : undefined,
    }));
  }

  private prazo(dinamica: DinamicaEmCurso): OpcaoLista['prazo'] {
    const situacao = situacaoCaducidade(dinamica.dataCaducidade, dinamica.estado, this.hoje);
    if (situacao.tipo !== 'NORMAL' && situacao.tipo !== 'PROXIMA') {
      return undefined;
    }
    return {
      rotulo: 'Caduca em',
      data: situacao.data.texto,
      datetime: situacao.data.iso,
      selo: situacao.tipo === 'PROXIMA' ? textoPrazo(situacao.dias) : undefined,
    };
  }

  private data(valor: string): string {
    return formatDate(valor, 'dd/MM/yyyy', this.localizacao);
  }
}
