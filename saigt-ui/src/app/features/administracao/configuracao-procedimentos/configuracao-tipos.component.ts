import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import {
  CatalogoTipos,
  FiltroEstadoConfig,
  ROTULOS_CATEGORIA,
  ROTULOS_FORMULARIO,
  TipoConfig,
  TipoConfigRequest,
  TipoInstrumentoConfigRequest,
  ativoDoFiltro,
} from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { PosicaoMenu, posicaoMenu } from '../posicao-menu';
import { TipoConfigModalComponent } from './tipo-config-modal.component';

const TAMANHO_PAGINA = 20;
const DURACAO_MENSAGEM_MS = 4000;

const OPCOES_CATALOGO: readonly { valor: CatalogoTipos; rotulo: string }[] = [
  { valor: 'instrumento', rotulo: 'Tipos de Instrumento' },
  { valor: 'dinamica', rotulo: 'Tipos de Dinâmica' },
  { valor: 'procedimento', rotulo: 'Tipos de Procedimento' },
];

@Component({
  selector: 'app-configuracao-tipos',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    PaginacaoComponent,
    SeloComponent,
    TabelaComponent,
    TipoConfigModalComponent,
  ],
  templateUrl: './configuracao-tipos.component.html',
  styleUrl: './configuracao.css',
  host: {
    '(document:click)': 'fecharMenu()',
    '(document:keydown.escape)': 'fecharMenu(true)',
    '(window:scroll)': 'fecharMenu()',
    '(window:resize)': 'fecharMenu()',
  },
})
export class ConfiguracaoTiposComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly opcoesCatalogo = OPCOES_CATALOGO;
  readonly opcoesEstado: readonly { valor: FiltroEstadoConfig; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todos' },
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
  ];

  readonly catalogo = signal<CatalogoTipos>('instrumento');
  readonly filtroEstado = signal<FiltroEstadoConfig>('todos');
  readonly pagina = signal(0);
  readonly linhas = signal<TipoConfig[]>([]);
  readonly total = signal(0);
  readonly aCarregar = signal(true);
  /** Falha a listar: esconde a tabela. */
  readonly erro = signal<ApiError | null>(null);
  /** Falha de uma ação fora do modal (desativar, reativar). */
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly emEdicao = signal<TipoConfig | null>(null);
  readonly modalAberto = signal(false);
  readonly aGravar = signal(false);
  readonly erroModal = signal<ApiError | null>(null);
  readonly confirmacao = signal<TipoConfig | null>(null);
  readonly aProcessar = signal(false);
  readonly menuAbertoId = signal<number | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly nomeCatalogo = computed(() => OPCOES_CATALOGO.find((opcao) => opcao.valor === this.catalogo())!.rotulo);
  readonly colunas = computed<ColunaTabela[]>(() => {
    const catalogo = this.catalogo();
    return [
      { chave: 'codigo', titulo: 'Código', largura: '24%' },
      { chave: 'nome', titulo: 'Nome' },
      ...(catalogo === 'instrumento' ? [{ chave: 'categoria', titulo: 'Categoria', largura: '14%' }] : []),
      ...(catalogo === 'procedimento' ? [{ chave: 'formulario', titulo: 'Formulário', largura: '18%' }] : []),
      { chave: 'estado', titulo: 'Estado', largura: '12%' },
      { chave: 'acoes', titulo: 'Ações', largura: '8%' },
    ];
  });
  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar tipo' : 'Reativar tipo'));
  readonly mensagemConfirmacao = computed(() => {
    const tipo = this.confirmacao();
    if (!tipo) {
      return '';
    }
    return tipo.ativo
      ? `Desativar o tipo ${tipo.nome}? Deixa de ser oferecido em novos processos; os processos em curso não são afetados.`
      : `Reativar o tipo ${tipo.nome}? Volta a ser oferecido em novos processos.`;
  });

  private botaoMenu: HTMLElement | null = null;
  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private listagem: Subscription | undefined;

  ngOnInit(): void {
    this.carregar();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.listagem?.unsubscribe();
  }

  rotuloCategoria(tipo: TipoConfig): string {
    return 'categoria' in tipo ? ROTULOS_CATEGORIA[tipo.categoria] : '';
  }

  formulario(tipo: TipoConfig): string | null {
    const formulario = 'formulario' in tipo ? tipo.formulario : null;
    return formulario ? (ROTULOS_FORMULARIO[formulario as keyof typeof ROTULOS_FORMULARIO] ?? formulario) : null;
  }

  mudarCatalogo(catalogo: CatalogoTipos): void {
    if (catalogo !== this.catalogo()) {
      this.catalogo.set(catalogo);
      this.recarregar();
    }
  }

  mudarFiltroEstado(filtro: FiltroEstadoConfig): void {
    if (filtro !== this.filtroEstado()) {
      this.filtroEstado.set(filtro);
      this.recarregar();
    }
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar();
  }

  adicionar(): void {
    this.abrirModal(null);
  }

  alternarMenu(tipo: TipoConfig, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAbertoId() === tipo.id) {
      this.fecharMenu();
      return;
    }
    this.botaoMenu = evento.currentTarget as HTMLElement;
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(tipo.id);
    setTimeout(() => this.anfitriao.nativeElement.querySelector<HTMLElement>('.dropdown-menu button')?.focus());
  }

  /** Com `devolverFoco` (Esc), o foco volta ao botão "···". */
  fecharMenu(devolverFoco = false): void {
    if (this.menuAbertoId() === null) {
      return;
    }
    this.menuAbertoId.set(null);
    if (devolverFoco) {
      this.botaoMenu?.focus();
    }
  }

  editar(tipo: TipoConfig): void {
    this.voltarAoBotaoMenu();
    this.abrirModal(tipo);
  }

  pedirAlternarEstado(tipo: TipoConfig): void {
    this.voltarAoBotaoMenu();
    this.confirmacao.set(tipo);
  }

  fecharModal(): void {
    if (!this.aGravar()) {
      this.modalAberto.set(false);
    }
  }

  guardar(pedido: TipoConfigRequest): void {
    const tipo = this.emEdicao();
    this.aGravar.set(true);
    this.erroModal.set(null);
    this.gravar(tipo?.id ?? null, pedido).subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalAberto.set(false);
        this.mostrarMensagem(tipo ? 'Tipo atualizado.' : 'Tipo criado.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.erroModal.set(erro);
      },
    });
  }

  confirmarAlternarEstado(): void {
    const tipo = this.confirmacao();
    if (!tipo || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.gravar(tipo.id, this.pedidoDaLinha(tipo, !tipo.ativo)).subscribe({
      next: () => {
        this.aProcessar.set(false);
        this.confirmacao.set(null);
        this.mostrarMensagem(tipo.ativo ? 'Tipo desativado.' : 'Tipo reativado.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aProcessar.set(false);
        this.confirmacao.set(null);
        this.erroAcao.set(erro);
      },
    });
  }

  cancelarConfirmacao(): void {
    this.confirmacao.set(null);
  }

  private abrirModal(tipo: TipoConfig | null): void {
    this.erroModal.set(null);
    this.emEdicao.set(tipo);
    this.modalAberto.set(true);
  }

  /** O item do menu desaparece: o foco passa ao "···" para o modal o devolver a um botão que continua no ecrã. */
  private voltarAoBotaoMenu(): void {
    this.menuAbertoId.set(null);
    this.botaoMenu?.focus();
  }

  private pedidoDaLinha(tipo: TipoConfig, ativo: boolean): TipoConfigRequest {
    const base = { codigo: tipo.codigo, nome: tipo.nome, ativo };
    return 'categoria' in tipo ? ({ ...base, categoria: tipo.categoria } satisfies TipoInstrumentoConfigRequest) : base;
  }

  private gravar(id: number | null, pedido: TipoConfigRequest): Observable<TipoConfig> {
    const servico = this.configuracaoService;
    switch (this.catalogo()) {
      case 'instrumento': {
        const corpo = pedido as TipoInstrumentoConfigRequest;
        return id === null ? servico.criarTipoInstrumento(corpo) : servico.atualizarTipoInstrumento(id, corpo);
      }
      case 'dinamica':
        return id === null ? servico.criarTipoDinamica(pedido) : servico.atualizarTipoDinamica(id, pedido);
      case 'procedimento':
        return id === null ? servico.criarTipoProcedimento(pedido) : servico.atualizarTipoProcedimento(id, pedido);
    }
  }

  private listar(): Observable<PageResponse<TipoConfig>> {
    const filtro = { ativo: ativoDoFiltro(this.filtroEstado()) };
    switch (this.catalogo()) {
      case 'instrumento':
        return this.configuracaoService.listarTiposInstrumento(filtro, this.pagina(), TAMANHO_PAGINA);
      case 'dinamica':
        return this.configuracaoService.listarTiposDinamica(filtro, this.pagina(), TAMANHO_PAGINA);
      case 'procedimento':
        return this.configuracaoService.listarTiposProcedimento(filtro, this.pagina(), TAMANHO_PAGINA);
    }
  }

  private mostrarMensagem(mensagem: string): void {
    clearTimeout(this.temporizadorMensagem);
    this.mensagemSucesso.set(mensagem);
    this.temporizadorMensagem = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_MENSAGEM_MS);
  }

  private recarregar(): void {
    this.pagina.set(0);
    this.carregar();
  }

  /**
   * Uma resposta atrasada de outro catálogo ou filtro não pode substituir a atual.
   * `manterLinhas` (depois de gravar): sem o esqueleto, o botão "···" continua no ecrã e o modal devolve-lhe o foco.
   */
  private carregar(manterLinhas = false): void {
    this.listagem?.unsubscribe();
    this.aCarregar.set(!manterLinhas);
    this.erro.set(null);
    this.listagem = this.listar().subscribe({
      next: (resposta) => {
        this.linhas.set(resposta.content);
        this.total.set(resposta.totalElements);
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }
}
