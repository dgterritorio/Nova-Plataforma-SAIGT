import { Component, ElementRef, OnDestroy, OnInit, computed, inject, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { FormularioFixo } from '../../../models/processo.model';
import {
  DinamicaConfig,
  FiltroEstadoConfig,
  ProcedimentoConfig,
  ProcedimentoConfigRequest,
  ROTULOS_FORMULARIO,
  ROTULOS_SERIE_DR,
  TipoInstrumentoConfig,
  TipoProcedimentoConfig,
  ativoDoFiltro,
  formatarLimite,
} from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { PosicaoMenu, posicaoMenu } from '../posicao-menu';
import { SelecaoProcedimento } from './configuracao-itens-documentais.component';
import { PermissoesProcedimentoModalComponent } from './permissoes-procedimento-modal.component';
import { ProcedimentoConfigModalComponent, ValorProcedimentoConfig } from './procedimento-config-modal.component';

const TAMANHO_PAGINA = 20;
const TAMANHO_CATALOGO = 100;
const DURACAO_MENSAGEM_MS = 4000;

/** Procedimentos de uma Dinâmica de um tipo de Instrumento, pela ordem da sequência. */
@Component({
  selector: 'app-configuracao-procedimentos-dinamica',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    PaginacaoComponent,
    PermissoesProcedimentoModalComponent,
    ProcedimentoConfigModalComponent,
    ReactiveFormsModule,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './configuracao-procedimentos-dinamica.component.html',
  styleUrl: './configuracao.css',
  host: {
    '(document:click)': 'fecharMenu()',
    '(document:keydown.escape)': 'fecharMenu(true)',
    '(window:scroll)': 'fecharMenu()',
    '(window:resize)': 'fecharMenu()',
  },
})
export class ConfiguracaoProcedimentosDinamicaComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly itensDocumentais = output<SelecaoProcedimento>();

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly opcoesEstado: readonly { valor: FiltroEstadoConfig; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todos' },
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
  ];
  readonly colunas: ColunaTabela[] = [
    { chave: 'ordem', titulo: 'Ordem', largura: '6%' },
    { chave: 'procedimento', titulo: 'Procedimento' },
    { chave: 'obrigatorio', titulo: 'Obrigatório', largura: '8%' },
    { chave: 'serie', titulo: 'Série do DR', largura: '13%' },
    { chave: 'repetivel', titulo: 'Repetível', largura: '8%' },
    { chave: 'atoPontual', titulo: 'Ato pontual', largura: '8%' },
    { chave: 'limite', titulo: 'Limite do processo', largura: '10%' },
    { chave: 'formulario', titulo: 'Formulário', largura: '12%' },
    { chave: 'estado', titulo: 'Estado', largura: '9%' },
    { chave: 'acoes', titulo: 'Ações', largura: '6%' },
  ];

  readonly tiposInstrumento = signal<TipoInstrumentoConfig[]>([]);
  readonly tiposProcedimento = signal<TipoProcedimentoConfig[]>([]);
  readonly tipoInstrumento = new FormControl<string | null>(null);
  readonly tipoInstrumentoId = signal<number | null>(null);
  readonly dinamicas = signal<DinamicaConfig[]>([]);
  readonly dinamica = new FormControl<string | null>(null);
  readonly dinamicaConfigId = signal<number | null>(null);
  readonly filtroEstado = signal<FiltroEstadoConfig>('todos');
  readonly pagina = signal(0);
  readonly linhas = signal<ProcedimentoConfig[]>([]);
  readonly total = signal(0);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly emEdicao = signal<ProcedimentoConfig | null>(null);
  readonly modalAberto = signal(false);
  readonly aGravar = signal(false);
  readonly erroModal = signal<ApiError | null>(null);
  readonly permissoesDe = signal<ProcedimentoConfig | null>(null);
  readonly confirmacao = signal<ProcedimentoConfig | null>(null);
  readonly aProcessar = signal(false);
  readonly menuAbertoId = signal<number | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly opcoesTipoInstrumento = computed<OpcaoSeletor[]>(() =>
    this.tiposInstrumento().map((tipo) => ({
      valor: String(tipo.id),
      rotulo: `${tipo.codigo} — ${tipo.nome}${tipo.ativo ? '' : ' (inativo)'}`,
    })),
  );
  readonly opcoesDinamica = computed<OpcaoSeletor[]>(() =>
    this.dinamicas().map((dinamica) => ({
      valor: String(dinamica.id),
      rotulo: `${dinamica.tipoDinamica.nome}${dinamica.ativo ? '' : ' (inativa)'}`,
    })),
  );
  readonly nomeDinamica = computed(() => {
    const dinamica = this.dinamicas().find((d) => d.id === this.dinamicaConfigId());
    return dinamica ? `${dinamica.tipoInstrumento.codigo} — ${dinamica.tipoDinamica.nome}` : '';
  });
  readonly legenda = computed(() => `Procedimentos de ${this.nomeDinamica()}`);
  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar Procedimento' : 'Reativar Procedimento'));
  readonly mensagemConfirmacao = computed(() => {
    const procedimento = this.confirmacao();
    if (!procedimento) {
      return '';
    }
    const nome = procedimento.tipoProcedimento.nome;
    return procedimento.ativo
      ? `Desativar o Procedimento ${nome}? Deixa de ser oferecido e de entrar no sequenciamento; os processos em curso não são afetados.`
      : `Reativar o Procedimento ${nome}? Volta a ser oferecido e a entrar no sequenciamento.`;
  });

  private botaoMenu: HTMLElement | null = null;
  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private listagem: Subscription | undefined;
  private listagemDinamicas: Subscription | undefined;

  ngOnInit(): void {
    this.tipoInstrumento.valueChanges.subscribe((valor) => {
      this.tipoInstrumentoId.set(valor ? Number(valor) : null);
      this.carregarDinamicas();
    });
    this.dinamica.valueChanges.subscribe((valor) => {
      this.dinamicaConfigId.set(valor ? Number(valor) : null);
      this.recarregar();
    });
    this.configuracaoService.listarTiposInstrumento({}, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => {
        const tipos = [...resposta.content].sort((a, b) => a.codigo.localeCompare(b.codigo));
        this.tiposInstrumento.set(tipos);
        if (tipos.length > 0) {
          this.tipoInstrumento.setValue(String(tipos[0].id));
        } else {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
    this.configuracaoService.listarTiposProcedimento({ ativo: true }, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => this.tiposProcedimento.set(resposta.content),
      error: (erro: ApiError) => this.erroAcao.set(erro),
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.listagem?.unsubscribe();
    this.listagemDinamicas?.unsubscribe();
  }

  simNao(valor: boolean): string {
    return valor ? 'Sim' : 'Não';
  }

  rotuloSerie(procedimento: ProcedimentoConfig): string {
    return ROTULOS_SERIE_DR[procedimento.serieDr];
  }

  rotuloLimite(procedimento: ProcedimentoConfig): string {
    return formatarLimite(procedimento.tamanhoMaximoProcesso);
  }

  rotuloFormulario(procedimento: ProcedimentoConfig): string {
    const formulario = procedimento.formulario;
    return formulario ? (ROTULOS_FORMULARIO[formulario as FormularioFixo] ?? formulario) : '';
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

  alternarMenu(procedimento: ProcedimentoConfig, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAbertoId() === procedimento.id) {
      this.fecharMenu();
      return;
    }
    this.botaoMenu = evento.currentTarget as HTMLElement;
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(procedimento.id);
    setTimeout(() => this.anfitriao.nativeElement.querySelector<HTMLElement>('.dropdown-menu button')?.focus());
  }

  fecharMenu(devolverFoco = false): void {
    if (this.menuAbertoId() === null) {
      return;
    }
    this.menuAbertoId.set(null);
    if (devolverFoco) {
      this.botaoMenu?.focus();
    }
  }

  editar(procedimento: ProcedimentoConfig): void {
    this.voltarAoBotaoMenu();
    this.abrirModal(procedimento);
  }

  abrirPermissoes(procedimento: ProcedimentoConfig): void {
    this.voltarAoBotaoMenu();
    this.permissoesDe.set(procedimento);
  }

  fecharPermissoes(): void {
    this.permissoesDe.set(null);
  }

  abrirItensDocumentais(procedimento: ProcedimentoConfig): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    this.menuAbertoId.set(null);
    if (tipoInstrumentoId !== null) {
      this.itensDocumentais.emit({
        tipoInstrumentoId,
        dinamicaConfigId: procedimento.instrumentoDinamicaConfigId,
        procedimentoConfigId: procedimento.id,
      });
    }
  }

  pedirAlternarEstado(procedimento: ProcedimentoConfig): void {
    this.voltarAoBotaoMenu();
    this.confirmacao.set(procedimento);
  }

  fecharModal(): void {
    if (!this.aGravar()) {
      this.modalAberto.set(false);
    }
  }

  guardar(valor: ValorProcedimentoConfig): void {
    const instrumentoDinamicaConfigId = this.dinamicaConfigId();
    if (instrumentoDinamicaConfigId === null) {
      return;
    }
    const procedimento = this.emEdicao();
    const pedido: ProcedimentoConfigRequest = {
      instrumentoDinamicaConfigId: procedimento?.instrumentoDinamicaConfigId ?? instrumentoDinamicaConfigId,
      ...valor,
      ativo: procedimento?.ativo ?? true,
    };
    this.aGravar.set(true);
    this.erroModal.set(null);
    const gravacao = procedimento
      ? this.configuracaoService.atualizarProcedimentoConfig(procedimento.id, pedido)
      : this.configuracaoService.criarProcedimentoConfig(pedido);
    gravacao.subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalAberto.set(false);
        this.mostrarMensagem(procedimento ? 'Procedimento atualizado.' : 'Procedimento criado.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.erroModal.set(erro);
      },
    });
  }

  confirmarAlternarEstado(): void {
    const procedimento = this.confirmacao();
    if (!procedimento || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.configuracaoService
      .atualizarProcedimentoConfig(procedimento.id, { ...this.pedidoDe(procedimento), ativo: !procedimento.ativo })
      .subscribe({
        next: () => {
          this.aProcessar.set(false);
          this.confirmacao.set(null);
          this.mostrarMensagem(procedimento.ativo ? 'Procedimento desativado.' : 'Procedimento reativado.');
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

  private pedidoDe(procedimento: ProcedimentoConfig): ProcedimentoConfigRequest {
    return {
      instrumentoDinamicaConfigId: procedimento.instrumentoDinamicaConfigId,
      tipoProcedimentoId: procedimento.tipoProcedimento.id,
      ordem: procedimento.ordem,
      obrigatorio: procedimento.obrigatorio,
      serieDr: procedimento.serieDr,
      tamanhoMaximoProcesso: procedimento.tamanhoMaximoProcesso,
      repetivel: procedimento.repetivel,
      atoPontual: procedimento.atoPontual,
      ativo: procedimento.ativo,
      visivelNaDinamica: procedimento.visivelNaDinamica,
    };
  }

  private abrirModal(procedimento: ProcedimentoConfig | null): void {
    this.erroModal.set(null);
    this.emEdicao.set(procedimento);
    this.modalAberto.set(true);
  }

  private voltarAoBotaoMenu(): void {
    this.menuAbertoId.set(null);
    this.botaoMenu?.focus();
  }

  private mostrarMensagem(mensagem: string): void {
    clearTimeout(this.temporizadorMensagem);
    this.mensagemSucesso.set(mensagem);
    this.temporizadorMensagem = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_MENSAGEM_MS);
  }

  /** Ativas e inativas; abre na primeira que a API devolve. */
  private carregarDinamicas(): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    this.listagemDinamicas?.unsubscribe();
    this.listagem?.unsubscribe();
    this.dinamicas.set([]);
    this.linhas.set([]);
    this.total.set(0);
    if (tipoInstrumentoId === null) {
      return;
    }
    this.aCarregar.set(true);
    this.erro.set(null);
    this.listagemDinamicas = this.configuracaoService.listarDinamicasConfig({ tipoInstrumentoId }, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => {
        this.dinamicas.set(resposta.content);
        const primeira = resposta.content[0];
        this.dinamica.setValue(primeira ? String(primeira.id) : null);
        if (!primeira) {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  private recarregar(): void {
    this.pagina.set(0);
    this.carregar();
  }

  /** `manterLinhas` (depois de gravar): sem o esqueleto, o botão "···" continua no ecrã e o modal devolve-lhe o foco. */
  private carregar(manterLinhas = false): void {
    const instrumentoDinamicaConfigId = this.dinamicaConfigId();
    this.listagem?.unsubscribe();
    if (instrumentoDinamicaConfigId === null) {
      this.linhas.set([]);
      this.total.set(0);
      return;
    }
    this.aCarregar.set(!manterLinhas);
    this.erro.set(null);
    this.listagem = this.configuracaoService
      .listarProcedimentosConfig(
        { instrumentoDinamicaConfigId, ativo: ativoDoFiltro(this.filtroEstado()) },
        this.pagina(),
        TAMANHO_PAGINA,
      )
      .subscribe({
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
