import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, viewChild } from '@angular/core';
import { DestinoProcesso, PaginaPendencia, PendenciaSubmissao, ROTULO_DESTINO } from '../../../../models/processo.model';
import { FicheirosRemetidosComponent } from '../../../../shared/components/ficheiros-remetidos.component';
import { AlertaComponent } from '../../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../../shared/ui/cartao/cartao.component';

const ORDEM_PAGINAS: PaginaPendencia[] = ['FORMULARIO', 'PLANTAS', 'DOCUMENTOS', 'METADADOS'];
const ROTULO_PAGINA: Record<PaginaPendencia, string> = {
  FORMULARIO: 'Formulário',
  PLANTAS: 'Plantas',
  DOCUMENTOS: 'Documentos',
  METADADOS: 'Metadados',
};

@Component({
  selector: 'app-pagina-resumo',
  imports: [AlertaComponent, BotaoComponent, CartaoComponent, FicheirosRemetidosComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina-resumo.css',
  template: `
    <h2>Resumo</h2>

    <app-cartao titulo="Dados do processo">
      <dl class="dados">
        <div><dt>Designação</dt><dd class="valor-forte">{{ designacao() }}</dd></div>
        <div><dt>Tipo de instrumento</dt><dd>{{ tipoInstrumento() }}</dd></div>
        <div><dt>Tipo de procedimento</dt><dd>{{ tipoProcedimento() }}</dd></div>
        @if (atoReferido(); as ato) {
          <div class="ato-referido"><dt>Ato a que respeita</dt><dd>{{ ato }}</dd></div>
        }
        <div><dt>Concelho</dt><dd>{{ concelho() || '—' }}</dd></div>
        @if (destino(); as valorDestino) {
          <div class="destino-processo"><dt>Destino</dt><dd>{{ rotulosDestino[valorDestino] }}</dd></div>
        }
        <div><dt>Submissão do processo n.º</dt><dd class="valor-mono">{{ numeroSubmissao() ?? '—' }}</dd></div>
      </dl>
    </app-cartao>

    @if (processoId(); as id) {
      <app-ficheiros-remetidos [processoId]="id" />
    }

    <section class="confirmacao" aria-label="Confirmação da submissão">
      <ng-content />

      @if (submeterDisponivel()) {
        <label class="caixa-confirmacao" [class.com-erro]="erroConfirmacao()">
          <input
            #caixaConfirmacao
            id="confirmacao-conteudo-processo"
            type="checkbox"
            [checked]="confirmado()"
            [attr.aria-invalid]="erroConfirmacao() ? 'true' : null"
            [attr.aria-describedby]="erroConfirmacao() ? 'erro-confirmacao-conteudo-processo' : null"
            (change)="aoAlternar($event)"
          />
          @if (destino() === 'SO_DEPOSITO') {
            <span>Confirmo o conteúdo do processo e submeto-o para depósito na DG Território.</span>
          } @else {
            <span>Confirmo o conteúdo do processo e submeto-o para publicação no Diário da República e depósito na DG Território.</span>
          }
        </label>
        @if (erroConfirmacao(); as mensagem) {
          <p class="erro-campo" id="erro-confirmacao-conteudo-processo" role="alert">{{ mensagem }}</p>
        }
        <p class="nota">
          <strong>Nota:</strong><br />
          Para finalizar a instrução, assinale a caixa de confirmação e selecione «Submeter».<br />
          Se ainda não quer finalizar, saia desta página: o processo fica guardado em «Continuar Processo».
        </p>
        @if (!podeSubmeter()) {
          @if (gruposPendencias().length > 0) {
            <app-alerta tipo="aviso" id="aviso-pode-submeter" titulo="Para submeter, falta:" [urgente]="true">
              @for (grupo of gruposPendencias(); track grupo.pagina) {
                <div class="grupo-pendencias">
                  <p class="grupo-titulo" [id]="'pendencias-' + grupo.pagina">{{ grupo.rotulo }}</p>
                  <ul class="lista-pendencias" [attr.aria-labelledby]="'pendencias-' + grupo.pagina">
                    @for (pendencia of grupo.pendencias; track $index) {
                      <li>{{ pendencia.erro }}</li>
                    }
                  </ul>
                  <button type="button" app-botao variante="texto" tamanho="pequeno" class="ir-para" (click)="irParaPagina.emit(grupo.pagina)">
                    Ir para {{ grupo.rotulo }}
                  </button>
                </div>
              }
            </app-alerta>
          } @else {
            <app-alerta tipo="aviso" id="aviso-pode-submeter">Preencha os dados obrigatórios do formulário antes de submeter.</app-alerta>
          }
        }
      } @else {
        <app-alerta tipo="aviso">Não pode submeter este processo.</app-alerta>
      }
    </section>
  `,
})
export class PaginaResumoComponent {
  readonly designacao = input('');
  readonly tipoInstrumento = input('');
  readonly tipoProcedimento = input('');
  readonly concelho = input<string | null>(null);
  /** As submissões já feitas (`ProcessoDetalhe.submissao`): o Resumo mostra a que vai ser feita. */
  readonly submissao = input<number | null>(null);
  readonly numeroSubmissao = computed(() => {
    const feitas = this.submissao();
    return feitas === null ? null : feitas + 1;
  });
  /** Nos atos pontuais, o ato publicado que o processo visa. */
  readonly atoReferido = input<string | null>(null);
  readonly destino = input<DestinoProcesso | null>(null);
  readonly rotulosDestino = ROTULO_DESTINO;
  readonly processoId = input<string | null>(null);
  readonly submeterDisponivel = input(true);
  readonly podeSubmeter = input(true);
  readonly pendencias = input<PendenciaSubmissao[]>([]);
  readonly confirmado = input(false);
  readonly erroConfirmacao = input<string | null>(null);
  readonly confirmacaoAlterada = output<boolean>();
  readonly irParaPagina = output<PaginaPendencia>();

  readonly gruposPendencias = computed(() =>
    ORDEM_PAGINAS.map((pagina) => ({
      pagina,
      rotulo: ROTULO_PAGINA[pagina],
      pendencias: this.pendencias().filter((pendencia) => pendencia.pagina === pagina),
    })).filter((grupo) => grupo.pendencias.length > 0),
  );

  private readonly caixaConfirmacao = viewChild<ElementRef<HTMLInputElement>>('caixaConfirmacao');

  constructor() {
    effect(() => {
      const caixa = this.caixaConfirmacao();
      if (this.erroConfirmacao() && caixa) {
        caixa.nativeElement.focus();
      }
    });
  }

  aoAlternar(evento: Event): void {
    this.confirmacaoAlterada.emit((evento.target as HTMLInputElement).checked);
  }
}
