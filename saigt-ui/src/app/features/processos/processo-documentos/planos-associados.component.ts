import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import { Instrumento } from '../../../models/instrumento.model';
import { InstrumentoAbrangido, ItemTerritorio, ProcessoDetalhe } from '../../../models/processo.model';
import { DinamicaService } from '../../../services/dinamica.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { BotaoComponent, CampoTextoComponent, OpcaoSeletor, SeletorComponent } from '../../../shared/ui';

const TAMANHO_PESQUISA = 20;
const MENSAGEM_SEM_RESPOSTA = 'Não foi possível concluir a operação. Tente novamente.';
// Recusas do conjunto, mostradas junto da lista; 403/404/409 vão para o banner.
const CODIGOS_ERRO_DA_LISTA = ['ASSOCIACAO_INVALIDA', 'VALIDACAO'];

function mesmosIds(a: InstrumentoAbrangido[], b: InstrumentoAbrangido[]): boolean {
  const ids = new Set(a.map((instrumento) => instrumento.id));
  return a.length === b.length && b.every((instrumento) => ids.has(instrumento.id));
}

/**
 * «Planos ou programas associados» da Suspensão e das Medidas Preventivas (associação `UM_OU_MAIS` ou `OPCIONAL`):
 * o autor em `EM_SUBMISSAO` pesquisa os Instrumentos em vigor dos tipos admitidos e grava o conjunto completo; os outros
 * só leem. Sem desenho no Figma: usa os componentes e os tokens do ecrã dos documentos.
 */
@Component({
  selector: 'app-planos-associados',
  standalone: true,
  imports: [ReactiveFormsModule, BotaoComponent, CampoTextoComponent, ErrorBannerComponent, SeletorComponent],
  styleUrls: ['./planos-associados.component.css'],
  template: `
    @if (visivel()) {
      <section class="planos" aria-labelledby="planos-associados-titulo">
        <h3 id="planos-associados-titulo">{{ titulo() }}</h3>
        <app-error-banner [erro]="erroBanner()" />

        @if (editavel()) {
          <form class="pesquisa" role="search" aria-label="Pesquisar planos ou programas em vigor" [formGroup]="filtros" (ngSubmit)="pesquisar()">
            <app-seletor rotulo="Tipo" placeholder="Todos os tipos admitidos" [opcoes]="opcoesTipo()" formControlName="tipo" [compacto]="true" />
            <app-seletor rotulo="Concelho" placeholder="Todos os concelhos" [opcoes]="opcoesConcelho()" formControlName="concelho" [compacto]="true" />
            <app-campo-texto rotulo="Designação" formControlName="designacao" [compacto]="true" />
            <button type="submit" app-botao variante="secundario" [aCarregar]="aPesquisar()">Pesquisar</button>
          </form>
          <p class="nota">Só aparecem planos ou programas em vigor, dos tipos que esta dinâmica admite.</p>

          @if (resultados(); as encontrados) {
            @if (encontrados.length === 0) {
              <p class="estado-vazio" role="status">Nenhum plano ou programa em vigor encontrado.</p>
            } @else {
              <table class="tabela-planos">
                <caption>Resultados da pesquisa</caption>
                <thead>
                  <tr>
                    <th scope="col">Designação</th>
                    <th scope="col">Tipo</th>
                    <th scope="col">Concelhos</th>
                    <th scope="col"><span class="visualmente-oculto">Ação</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (instrumento of encontrados; track instrumento.id) {
                    <tr>
                      <td>{{ instrumento.designacao }}</td>
                      <td>{{ codigoDoTipo(instrumento.tipoInstrumentoId) }}</td>
                      <td>{{ nomesDosConcelhos(instrumento) }}</td>
                      <td class="acao">
                        @if (naLista(instrumento.id)) {
                          <span class="ja-associado">Na lista</span>
                        } @else {
                          <button type="button" app-botao variante="contorno" tamanho="pequeno" [attr.aria-label]="'Adicionar ' + instrumento.designacao" (click)="adicionar(instrumento)">
                            Adicionar
                          </button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          }
        }

        <p class="contagem" aria-live="polite">{{ textoContagem() }}</p>
        @if (lista().length === 0) {
          <p class="estado-vazio">Sem IGT associado</p>
        } @else {
          <table class="tabela-planos" [attr.aria-describedby]="erroLista() ? 'planos-associados-erro' : null">
            <caption class="visualmente-oculto">{{ titulo() }}</caption>
            <thead>
              <tr>
                <th scope="col">Designação</th>
                <th scope="col">Tipo</th>
                @if (editavel()) {
                  <th scope="col"><span class="visualmente-oculto">Ação</span></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (instrumento of lista(); track instrumento.id) {
                <tr>
                  <td>{{ instrumento.designacao }}</td>
                  <td>{{ instrumento.tipoInstrumento }}</td>
                  @if (editavel()) {
                    <td class="acao">
                      <button type="button" app-botao variante="texto" tamanho="pequeno" [attr.aria-label]="'Remover ' + instrumento.designacao" (click)="remover(instrumento.id)">
                        Remover
                      </button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        }
        @if (erroLista(); as mensagem) {
          <p class="erro" id="planos-associados-erro" role="alert">{{ mensagem }}</p>
        }

        @if (editavel()) {
          <div class="acoes">
            <button type="button" app-botao [desativado]="!alterada() || aGravar()" [aCarregar]="aGravar()" (click)="gravar()">Gravar associação</button>
            @if (mensagemGravada(); as mensagem) {
              <span class="sucesso" role="status">{{ mensagem }}</span>
            }
          </div>
        }
      </section>
    }
  `,
})
export class PlanosAssociadosComponent {
  private readonly instrumentoService = inject(InstrumentoService);
  private readonly dinamicaService = inject(DinamicaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly processo = input.required<ProcessoDetalhe>();
  readonly associacaoGravada = output<InstrumentoAbrangido[]>();

  readonly visivel = computed(() => ['UM_OU_MAIS', 'OPCIONAL'].includes(this.processo().associacaoInstrumentos));
  readonly editavel = computed(() => this.visivel() && this.processo().podeAssociarInstrumentos);
  /** A Suspensão da CCDR só associa planos (EF v1.5 §3.1.2). */
  readonly titulo = computed(() =>
    this.processo().formulario === 'SUSPENSAO_CCDR' ? 'Planos associados' : 'Planos ou programas associados',
  );

  readonly lista = signal<InstrumentoAbrangido[]>([]);
  private readonly gravada = signal<InstrumentoAbrangido[]>([]);
  readonly alterada = computed(() => !mesmosIds(this.lista(), this.gravada()));
  readonly textoContagem = computed(() => {
    const total = this.lista().length;
    return total === 1 ? '1 plano ou programa associado' : `${total} planos ou programas associados`;
  });

  readonly resultados = signal<Instrumento[] | null>(null);
  readonly aPesquisar = signal(false);
  readonly aGravar = signal(false);
  readonly erroLista = signal<string | null>(null);
  readonly erroBanner = signal<ApiError | null>(null);
  readonly mensagemGravada = signal<string | null>(null);
  private readonly concelhos = signal<ItemTerritorio[]>([]);
  private concelhosPedidos = false;
  private processoId: string | null = null;

  readonly filtros = inject(FormBuilder).group({
    tipo: [null as string | null],
    concelho: [null as string | null],
    designacao: [''],
  });

  readonly opcoesTipo = computed<OpcaoSeletor[]>(() =>
    this.processo().tiposInstrumentoAdmitidos.map((tipo) => ({ valor: String(tipo.id), rotulo: `${tipo.codigo} — ${tipo.nome}` })),
  );
  readonly opcoesConcelho = computed<OpcaoSeletor[]>(() =>
    this.concelhos().map((concelho) => ({ valor: concelho.codigo, rotulo: concelho.nome ?? concelho.codigo })),
  );

  constructor() {
    effect(() => {
      const processo = this.processo();
      const editavel = this.editavel();
      untracked(() => {
        this.lista.set([...processo.instrumentosAbrangidos]);
        this.gravada.set([...processo.instrumentosAbrangidos]);
        // O mesmo processo relido depois de gravar mantém a pesquisa e a mensagem.
        if (processo.id !== this.processoId) {
          this.processoId = processo.id;
          this.resultados.set(null);
          this.mensagemGravada.set(null);
        }
        this.erroLista.set(null);
        this.erroBanner.set(null);
        if (editavel) {
          this.carregarConcelhos();
        }
      });
    });
  }

  naLista(id: string): boolean {
    return this.lista().some((instrumento) => instrumento.id === id);
  }

  codigoDoTipo(tipoInstrumentoId: number): string {
    return this.processo().tiposInstrumentoAdmitidos.find((tipo) => tipo.id === tipoInstrumentoId)?.codigo ?? '';
  }

  nomesDosConcelhos(instrumento: Instrumento): string {
    const nomes = new Map(this.concelhos().map((concelho) => [concelho.codigo, concelho.nome ?? concelho.codigo]));
    return (instrumento.concelhos ?? []).map((codigo) => nomes.get(codigo) ?? codigo).join(', ') || '—';
  }

  pesquisar(): void {
    const admitidos = this.processo().tiposInstrumentoAdmitidos.map((tipo) => tipo.id);
    const { tipo, concelho, designacao } = this.filtros.getRawValue();
    const tiposInstrumentoId = tipo ? [Number(tipo)] : admitidos;
    this.erroBanner.set(null);
    if (tiposInstrumentoId.length === 0) {
      this.resultados.set([]);
      return;
    }
    this.aPesquisar.set(true);
    this.instrumentoService
      .pesquisarEmVigor({ tiposInstrumentoId, concelho, designacao }, 0, TAMANHO_PESQUISA)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pagina) => {
          this.aPesquisar.set(false);
          this.resultados.set(pagina.content.filter((instrumento) => instrumento.emVigor && admitidos.includes(instrumento.tipoInstrumentoId)));
        },
        error: (erro: unknown) => {
          this.aPesquisar.set(false);
          this.erroBanner.set(this.apiError(erro));
        },
      });
  }

  adicionar(instrumento: Instrumento): void {
    if (this.naLista(instrumento.id)) {
      return;
    }
    this.mensagemGravada.set(null);
    this.lista.update((lista) => [
      ...lista,
      { id: instrumento.id, designacao: instrumento.designacao, tipoInstrumento: this.codigoDoTipo(instrumento.tipoInstrumentoId) },
    ]);
  }

  remover(id: string): void {
    this.mensagemGravada.set(null);
    this.lista.update((lista) => lista.filter((instrumento) => instrumento.id !== id));
  }

  gravar(): void {
    const enviada = this.lista();
    this.aGravar.set(true);
    this.erroLista.set(null);
    this.erroBanner.set(null);
    this.mensagemGravada.set(null);
    this.dinamicaService
      .associarInstrumentos(this.processo().dinamicaId, enviada.map((instrumento) => instrumento.id))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aGravar.set(false);
          this.lista.set(resposta.instrumentos);
          this.gravada.set(resposta.instrumentos);
          this.mensagemGravada.set('Associação gravada.');
          this.associacaoGravada.emit(resposta.instrumentos);
        },
        error: (erro: unknown) => {
          this.aGravar.set(false);
          const apiError = this.apiError(erro);
          if (CODIGOS_ERRO_DA_LISTA.includes(apiError.codigo)) {
            this.erroLista.set(this.mensagemDaLista(apiError, enviada));
          } else {
            this.erroBanner.set(apiError);
          }
        },
      });
  }

  /** Cada recusa por índice ganha a designação do Instrumento enviado nessa posição. */
  private mensagemDaLista(erro: ApiError, enviada: InstrumentoAbrangido[]): string {
    const detalhes = (erro.campos ?? []).map((campo) => {
      const indice = /\[(\d+)\]$/.exec(campo.path);
      const instrumento = indice ? enviada[Number(indice[1])] : undefined;
      return instrumento ? `${instrumento.designacao}: ${campo.erro}` : campo.erro;
    });
    return detalhes.length > 1 ? detalhes.join(' ') : detalhes[0] ?? erro.mensagem;
  }

  private carregarConcelhos(): void {
    if (this.concelhosPedidos) {
      return;
    }
    this.concelhosPedidos = true;
    this.instrumentoService
      .listarConcelhos()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (concelhos) => this.concelhos.set(concelhos), error: () => this.concelhos.set([]) });
  }

  private apiError(erro: unknown): ApiError {
    return erro instanceof ApiError ? erro : new ApiError('ERRO', MENSAGEM_SEM_RESPOSTA, '', null);
  }
}
