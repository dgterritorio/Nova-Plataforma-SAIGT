import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, of } from 'rxjs';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../shared/components/error-banner.component';
import { ESTADOS_PROCESSO, EstadoProcessoBadgeComponent, rotuloEstado } from '../../shared/components/estado-processo-badge.component';
import { BotaoComponent } from '../../shared/ui/botao/botao.component';
import { CampoPesquisaComponent } from '../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { PaginacaoComponent } from '../../shared/ui/paginacao/paginacao.component';
import { SeloComponent } from '../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../shared/ui/tabela/tabela.component';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import {
  ESTADOS_DO_GRUPO,
  GRUPOS_PROCESSO,
  GrupoProcesso,
  OpcaoLista,
  ProcessoFiltro,
  ProcessoListagem,
  SituacaoPcm,
  TipoMotivoAlteracao,
  devolvidoPelaPcm,
} from '../../models/processo.model';
import { ListaValoresService } from '../../services/lista-valores.service';
import { FormatoExportacao, ProcessoService } from '../../services/processo.service';
import { MenuExportarComponent } from './menu-exportar/menu-exportar.component';
import { ROUTES } from '../../shared/routes';
import { formatarNomeConcelho } from '../../shared/nome-concelho';

const TAMANHO_PAGINA = 20;
const DEBOUNCE_REFERENCIA_MS = 400;
const ESTADOS_DA_VISTA_DGT: readonly string[] = ['EM_VALIDACAO_DGT', 'VALIDADO_DGT'];

/** Ordem e títulos do Figma (UI-016/019/022). */
const COLUNAS: ColunaTabela[] = [
  { chave: 'numeroProcesso', titulo: 'Referência', largura: '10%' },
  { chave: 'tipoInstrumento', titulo: 'Tipo IGT', largura: '8%' },
  { chave: 'designacaoInstrumento', titulo: 'Designação', largura: '20%' },
  { chave: 'nomeDinamica', titulo: 'Dinâmica', largura: '12%' },
  { chave: 'nomeProcedimento', titulo: 'Procedimento', largura: '14%' },
  { chave: 'concelho', titulo: 'Concelho', largura: '8%' },
  { chave: 'estado', titulo: 'Estado', largura: '14%' },
  { chave: 'autorNome', titulo: 'Utilizador', largura: '14%' },
];

const MENSAGEM_VAZIO_GRUPO: Partial<Record<GrupoProcesso, string>> = {
  EM_CURSO: 'Não tem processos em curso.',
  PARA_CORRECAO: 'Não tem processos para corrigir.',
};

/** Um `grupo` desconhecido no URL não filtra nada, em vez de a API responder 400 logo ao abrir o ecrã. */
function grupoDoParametro(valor: string | null): GrupoProcesso | null {
  return GRUPOS_PROCESSO.find((grupo) => grupo === valor) ?? null;
}

const TIPOS_MOTIVO: readonly { tipo: TipoMotivoAlteracao; rotulo: string }[] = [
  { tipo: 'MOTIVO_ALTERACAO_LEI', rotulo: 'Lei' },
  { tipo: 'MOTIVO_ALTERACAO_PROGRAMA', rotulo: 'Programa' },
  { tipo: 'MOTIVO_ALTERACAO_PLANO', rotulo: 'Plano' },
];

const OPCOES_SITUACAO_PCM: readonly { valor: SituacaoPcm; rotulo: string }[] = [
  { valor: 'COM_PCM', rotulo: 'Com a PCM' },
  { valor: 'DEVOLVIDO', rotulo: 'Devolvidos pela PCM' },
  { valor: 'SEM_PCM', rotulo: 'Sem passagem pela PCM' },
];

/** Valores da query string que são uma situação PCM; os outros ignoram-se em vez de a API responder 400. */
function situacoesPcmDosParametros(valores: readonly string[]): SituacaoPcm[] {
  return valores.filter((valor): valor is SituacaoPcm => OPCOES_SITUACAO_PCM.some((opcao) => opcao.valor === valor));
}

interface GrupoMotivo {
  tipo: TipoMotivoAlteracao;
  rotulo: string;
  opcoes: OpcaoLista[];
}

const MAXIMO_CONCELHOS_VISIVEIS = 3;

export interface CelulaConcelhos {
  visiveis: string;
  /** Quantos ficam fora de `visiveis`. */
  mais: number;
  /** Todos, para o `title` e para os leitores de ecrã; vazio sem concelhos. */
  completa: string;
}

export function celulaConcelhos(concelhos: readonly string[] | undefined): CelulaConcelhos {
  const nomes = (concelhos ?? []).map(formatarNomeConcelho);
  return {
    visiveis: nomes.slice(0, MAXIMO_CONCELHOS_VISIVEIS).join(', '),
    mais: Math.max(0, nomes.length - MAXIMO_CONCELHOS_VISIVEIS),
    completa: nomes.join(', '),
  };
}

function valoresDistintos(valores: string[]): string[] {
  return [...new Set(valores)].sort((a, b) => a.localeCompare(b, 'pt'));
}

/**
 * Mesmo componente para o ecrã pessoal e para a visão transversal da DGT: `GET /api/v1/processos` já
 * devolve todas as entidades a `MASTER`/`TECNICO`.
 *
 * "Exportar" usa os filtros do último pedido da listagem, não o que está escrito no formulário nesse
 * instante: assim o ficheiro tem as linhas que a tabela mostra. Os filtros locais não vão no pedido.
 *
 * Sem ícone de atraso porque a caducidade é da Dinâmica e não vem na listagem.
 *
 * Tipo IGT/Dinâmica/Procedimento filtram localmente, sobre a página carregada: o backend não tem estes
 * parâmetros e `/catalogos` é só DGT, incompatível com o ecrã pessoal. Estado filtra no servidor, mas
 * aplica-se logo ao escolher, como os outros três e como a pesquisa por designação.
 */
@Component({
  selector: 'app-processos',
  standalone: true,
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    ReactiveFormsModule,
    EstadoProcessoBadgeComponent,
    RouterLink,
    BotaoComponent,
    CampoPesquisaComponent,
    TabelaComponent,
    CelulaTabelaDirective,
    PaginacaoComponent,
    SeloComponent,
    MenuExportarComponent,
  ],
  styleUrls: ['./processos.component.css'],
  template: `
    <app-shell breadcrumb="Processos">
      <div class="conteudo">
        <h1>{{ titulo }}</h1>

        <div class="barra">
          <app-campo-pesquisa
            placeholder="Pesquisar por designação…"
            largura="total"
            [formControl]="form.controls.designacaoInstrumento"
          />
          <button
            type="button"
            app-botao
            variante="contorno"
            class="botao-filtros"
            [attr.aria-expanded]="filtrosAbertos()"
            (click)="filtrosAbertos.set(!filtrosAbertos())"
          >
            <svg icone viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" aria-hidden="true">
              <line x1="3" y1="6" x2="17" y2="6" />
              <circle cx="12" cy="6" r="1.6" fill="currentColor" stroke="none" />
              <line x1="3" y1="10" x2="17" y2="10" />
              <circle cx="7" cy="10" r="1.6" fill="currentColor" stroke="none" />
              <line x1="3" y1="14" x2="17" y2="14" />
              <circle cx="14" cy="14" r="1.6" fill="currentColor" stroke="none" />
            </svg>
            Filtros
          </button>
          <app-menu-exportar [aExportar]="aExportar()" (formatoEscolhido)="exportar($event)" />
        </div>

          @if (filtrosAbertos()) {
            <div class="filtros">
              <form class="filtros-form" [formGroup]="form" (ngSubmit)="pesquisar()">
                <select aria-label="Tipo IGT" [value]="filtroTipoIgtLocal()" (change)="filtroTipoIgtLocal.set($any($event.target).value)">
                  <option value="">Tipo IGT (Todos)</option>
                  @for (tipo of tiposIgtDisponiveis(); track tipo) {
                    <option [value]="tipo">{{ tipo }}</option>
                  }
                </select>
                <select aria-label="Dinâmica" [value]="filtroDinamicaLocal()" (change)="filtroDinamicaLocal.set($any($event.target).value)">
                  <option value="">Dinâmica (Todos)</option>
                  @for (dinamica of dinamicasDisponiveis(); track dinamica) {
                    <option [value]="dinamica">{{ dinamica }}</option>
                  }
                </select>
                <select aria-label="Procedimento" [value]="filtroProcedimentoLocal()" (change)="filtroProcedimentoLocal.set($any($event.target).value)">
                  <option value="">Procedimento (Todos)</option>
                  @for (procedimento of procedimentosDisponiveis(); track procedimento) {
                    <option [value]="procedimento">{{ procedimento }}</option>
                  }
                </select>
                <select aria-label="Estado" formControlName="estado">
                  <option value="">Estado (Todos)</option>
                  @for (codigo of estadosSeletor; track codigo) {
                    <option [value]="codigo">{{ rotuloEstado(codigo) }}</option>
                  }
                </select>
                @if (perfilInterno) {
                  <label class="filtro-situacao-pcm" for="filtro-situacao-pcm">
                    Situação PCM
                    <select id="filtro-situacao-pcm" formControlName="situacaoPcm">
                      <option value="">Todas</option>
                      @for (opcao of opcoesSituacaoPcm; track opcao.valor) {
                        <option [value]="opcao.valor">{{ opcao.rotulo }}</option>
                      }
                    </select>
                  </label>
                }
                @if (gruposMotivo().length > 0) {
                  <select aria-label="Motivo da alteração" formControlName="motivoAlteracao">
                    <option value="">Motivo da alteração (Todos)</option>
                    @for (grupo of gruposMotivo(); track grupo.tipo) {
                      <optgroup [label]="grupo.rotulo">
                        @for (opcao of grupo.opcoes; track opcao.codigo) {
                          <option [value]="grupo.tipo + ':' + opcao.codigo">{{ opcao.descricao }}</option>
                        }
                      </optgroup>
                    }
                  </select>
                }
                <button type="submit" app-botao tamanho="pequeno">Filtrar</button>
                <button type="button" app-botao variante="texto" tamanho="pequeno" (click)="limparTudo()">Limpar tudo</button>
              </form>
            </div>
          }

        @if (erroExportacao()) {
          <app-error-banner [erro]="erroExportacao()" [mensagemDoServidor]="true" />
        }

        @if (erro()) {
          <app-error-banner [erro]="erro()" [mensagemDoServidor]="true" />
        } @else {
          <app-tabela
            [colunas]="colunas"
            [linhas]="processosFiltrados()"
            [aCarregar]="loading()"
            larguraMinima="960px"
            [mensagemVazia]="mensagemVazia()"
          >
            <ng-template appCelula="numeroProcesso" let-processo>
              <a class="ligacao-processo" [routerLink]="destino(processo)">#{{ processo.numeroProcesso }}</a>
            </ng-template>
            <ng-template appCelula="designacaoInstrumento" let-processo>
              <span class="designacao-instrumento">{{ processo.designacaoInstrumento }}</span>
              @if (processo.numeroInstrumento) {
                <small class="numero-instrumento">n.º {{ processo.numeroInstrumento }}</small>
              }
            </ng-template>
            <ng-template appCelula="tipoInstrumento" let-processo>
              <span class="tipo-igt">{{ processo.tipoInstrumento }}</span>
            </ng-template>
            <ng-template appCelula="concelho" let-processo>
              @let celula = celulaConcelhos(processo.concelhos);
              @if (celula.completa) {
                <span class="concelhos" [attr.title]="celula.completa" data-campo="concelhos">
                  @if (celula.mais) {
                    <span aria-hidden="true">{{ celula.visiveis }} <span class="concelhos-mais">+{{ celula.mais }}</span></span>
                    <span class="visualmente-oculto">{{ celula.completa }}</span>
                  } @else {
                    {{ celula.completa }}
                  }
                </span>
              } @else {
                —
              }
            </ng-template>
            <ng-template appCelula="estado" let-processo>
              <span class="estado-celula">
                <app-estado-processo-badge [estado]="processo.estado" />
                @if (devolvidoPelaPcm(processo)) {
                  <app-selo tom="info" forma="pill" data-campo="devolvido-pcm">Devolvido pela PCM</app-selo>
                }
              </span>
            </ng-template>
            <app-paginacao
              rodape
              [pagina]="pagina() + 1"
              [tamanhoPagina]="tamanhoPagina"
              [total]="totalElementos()"
              [mostrados]="processosFiltrados().length"
              etiquetaItens="procedimentos"
              (paginaMudou)="irParaPagina($event - 1)"
            />
          </app-tabela>
        }
      </div>
    </app-shell>
  `,
})
export class ProcessosComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  private readonly listaValoresService = inject(ListaValoresService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly documento = inject(DOCUMENT);
  readonly rotas = ROUTES;
  readonly colunas = COLUNAS;
  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly estadosProcesso = ESTADOS_PROCESSO;
  readonly rotuloEstado = rotuloEstado;
  readonly devolvidoPelaPcm = devolvidoPelaPcm;
  readonly celulaConcelhos = celulaConcelhos;
  readonly opcoesSituacaoPcm = OPCOES_SITUACAO_PCM;
  /** Só os perfis internos filtram pela situação PCM (a API ignora-a aos outros). */
  readonly perfilInterno = this.authService.perfilInterno() !== null;

  private readonly dadosRota = this.route.snapshot.data ?? {};
  /** Nos ecrãs "Continuar" e "Corrigir" o grupo vem da rota e vai em todos os pedidos. */
  private readonly grupoRota = grupoDoParametro(this.dadosRota['grupo'] ?? null);
  readonly titulo: string = this.dadosRota['titulo'] ?? 'Consultar Processos';
  readonly estadosSeletor: readonly string[] = (this.grupoRota && ESTADOS_DO_GRUPO[this.grupoRota]) || ESTADOS_PROCESSO;

  /** Vindo dos cartões do Painel Principal: só se aplica no carregamento inicial, `pesquisar()` substitui-o. */
  private readonly grupoInicial = grupoDoParametro(this.route.snapshot.queryParamMap.get('grupo'));
  /**
   * Da query string, só para perfis internos: um valor fica no filtro; vários (o cartão da fila da DGT) aplicam-se só
   * no carregamento inicial, como o `grupo`.
   */
  private readonly situacoesPcmIniciais = this.perfilInterno
    ? situacoesPcmDosParametros(this.route.snapshot.queryParamMap.getAll('situacaoPcm'))
    : [];
  private filtroManual = false;

  readonly form = inject(FormBuilder).nonNullable.group({
    designacaoInstrumento: [''],
    estado: [''],
    motivoAlteracao: [''],
    situacaoPcm: [this.situacoesPcmIniciais.length === 1 ? this.situacoesPcmIniciais[0] : ''],
  });

  /** Só os perfis internos filtram por motivo (a API ignora-o aos outros); listas vazias escondem o filtro. */
  readonly gruposMotivo = signal<GrupoMotivo[]>([]);

  readonly processos = signal<ProcessoListagem[]>([]);
  readonly pagina = signal(0);
  readonly totalElementos = signal(0);
  readonly loading = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly aExportar = signal(false);
  readonly erroExportacao = signal<ApiError | null>(null);
  private filtroAplicado: ProcessoFiltro = {};
  readonly filtrosAbertos = signal(this.situacoesPcmIniciais.length === 1);
  readonly termoPesquisaLocal = signal('');

  /** `''` é "sem filtro" e o valor da opção "(Todos)": com `null` o `<select>` nativo ficava sem nada selecionado. */
  readonly filtroTipoIgtLocal = signal('');
  readonly filtroDinamicaLocal = signal('');
  readonly filtroProcedimentoLocal = signal('');

  /** Valores distintos da página carregada. */
  readonly tiposIgtDisponiveis = computed(() => valoresDistintos(this.processos().map((p) => p.tipoInstrumento)));
  readonly dinamicasDisponiveis = computed(() => valoresDistintos(this.processos().map((p) => p.nomeDinamica)));
  readonly procedimentosDisponiveis = computed(() => valoresDistintos(this.processos().map((p) => p.nomeProcedimento)));

  readonly processosFiltrados = computed(() => {
    const termo = this.termoPesquisaLocal().trim().toLowerCase();
    const tipoIgt = this.filtroTipoIgtLocal();
    const dinamica = this.filtroDinamicaLocal();
    const procedimento = this.filtroProcedimentoLocal();

    return this.processos().filter((processo) => {
      if (tipoIgt && processo.tipoInstrumento !== tipoIgt) return false;
      if (dinamica && processo.nomeDinamica !== dinamica) return false;
      if (procedimento && processo.nomeProcedimento !== procedimento) return false;
      if (!termo) return true;
      return (
        processo.designacaoInstrumento.toLowerCase().includes(termo) ||
        processo.tipoInstrumento.toLowerCase().includes(termo) ||
        rotuloEstado(processo.estado).toLowerCase().includes(termo)
      );
    });
  });

  ngOnInit(): void {
    this.carregar();

    /** Pesquisa à medida que escreve, sem esperar pelo "Filtrar". */
    this.form.controls.designacaoInstrumento.valueChanges
      .pipe(debounceTime(DEBOUNCE_REFERENCIA_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.pesquisar());

    this.form.controls.estado.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.pesquisar());

    this.form.controls.motivoAlteracao.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.pesquisar());

    this.form.controls.situacaoPcm.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.pesquisar());

    this.carregarMotivos();
  }

  private carregarMotivos(): void {
    if (!this.authService.perfilInterno()) {
      return;
    }
    forkJoin(TIPOS_MOTIVO.map(({ tipo }) => this.listaValoresService.opcoes(tipo).pipe(catchError(() => of([])))))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((listas) =>
        this.gruposMotivo.set(
          TIPOS_MOTIVO.map((grupo, i) => ({ ...grupo, opcoes: listas[i] })).filter((grupo) => grupo.opcoes.length > 0),
        ),
      );
  }


  /**
   * A DGT abre em validação a vista que lhe deixa validar item a item e, depois de validado, o Resumo Validados com
   * «Publicar»; os restantes casos leem o detalhe.
   */
  destino(processo: ProcessoListagem): string {
    return this.authService.ator() === 'DGT' && ESTADOS_DA_VISTA_DGT.includes(processo.estado)
      ? this.rotas.PROCESSO_VALIDACAO(processo.id)
      : this.rotas.PROCESSO_DETALHE(processo.id);
  }

  mensagemVazia(): string {
    const valores = this.form.getRawValue();
    const comFiltros =
      !!valores.designacaoInstrumento ||
      !!valores.estado ||
      !!valores.motivoAlteracao ||
      !!valores.situacaoPcm ||
      !!this.filtroTipoIgtLocal() ||
      !!this.filtroDinamicaLocal() ||
      !!this.filtroProcedimentoLocal();
    const propria = this.grupoRota && !comFiltros ? MENSAGEM_VAZIO_GRUPO[this.grupoRota] : undefined;
    return propria ?? 'Sem processos para os filtros indicados.';
  }

  pesquisar(): void {
    if (this.form.invalid) {
      return;
    }
    this.filtroManual = true;
    this.pagina.set(0);
    this.carregar();
  }

  limparTudo(): void {
    // O estado não emite: `pesquisar()` abaixo já recarrega, e emitir duplicava o pedido.
    this.form.controls.estado.reset('', { emitEvent: false });
    this.form.controls.motivoAlteracao.reset('', { emitEvent: false });
    this.form.controls.situacaoPcm.reset('', { emitEvent: false });
    this.form.controls.designacaoInstrumento.reset('');
    this.filtroTipoIgtLocal.set('');
    this.filtroDinamicaLocal.set('');
    this.filtroProcedimentoLocal.set('');
    this.pesquisar();
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar();
  }

  private carregar(): void {
    this.loading.set(true);
    this.erro.set(null);
    this.erroExportacao.set(null);
    const filtro: ProcessoFiltro = this.construirFiltro();
    this.filtroAplicado = filtro;

    this.processoService.listar(filtro, this.pagina(), TAMANHO_PAGINA).subscribe({
      next: (paginaResposta) => {
        this.processos.set(paginaResposta.content);
        this.totalElementos.set(paginaResposta.totalElements);
        this.loading.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.loading.set(false);
      },
    });
  }

  exportar(formato: FormatoExportacao): void {
    if (this.aExportar()) {
      return;
    }
    this.aExportar.set(true);
    this.erroExportacao.set(null);
    this.processoService
      .exportar(this.filtroAplicado, formato)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ ficheiro, nomeFicheiro }) => {
          this.descarregar(ficheiro, nomeFicheiro);
          this.aExportar.set(false);
        },
        error: (erro: ApiError) => {
          this.erroExportacao.set(erro);
          this.aExportar.set(false);
        },
      });
  }

  private descarregar(ficheiro: Blob, nomeFicheiro: string): void {
    const endereco = URL.createObjectURL(ficheiro);
    const ligacao = this.documento.createElement('a');
    ligacao.href = endereco;
    ligacao.download = nomeFicheiro;
    ligacao.hidden = true;
    this.documento.body.appendChild(ligacao);
    ligacao.click();
    ligacao.remove();
    setTimeout(() => URL.revokeObjectURL(endereco));
  }

  private construirFiltro(): ProcessoFiltro {
    const valores = this.form.getRawValue();
    const filtro: ProcessoFiltro = {};
    if (valores.designacaoInstrumento) filtro.designacaoInstrumento = valores.designacaoInstrumento;
    if (valores.estado) {
      filtro.estado = valores.estado;
    }
    if (valores.motivoAlteracao) {
      filtro.motivoAlteracao = valores.motivoAlteracao;
    }
    if (valores.situacaoPcm) {
      filtro.situacaoPcm = valores.situacaoPcm as SituacaoPcm;
    } else if (!this.filtroManual && this.situacoesPcmIniciais.length > 1) {
      filtro.situacaoPcm = this.situacoesPcmIniciais;
    }
    if (this.grupoRota) {
      filtro.grupo = this.grupoRota;
    } else if (!this.filtroManual && this.grupoInicial) {
      filtro.grupo = this.grupoInicial;
    }
    return filtro;
  }
}
