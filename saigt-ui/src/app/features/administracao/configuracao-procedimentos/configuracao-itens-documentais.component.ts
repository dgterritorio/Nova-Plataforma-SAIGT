import { Component, ElementRef, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AtualizarDocumentoConfigPedido,
  DinamicaConfig,
  DocumentoConfig,
  FiltroEstadoConfig,
  ProcedimentoConfig,
  ROTULOS_CAIXA,
  ROTULOS_FORMATO,
  ROTULOS_INFORMACAO_BASE,
  ROTULOS_TIPO_ELEMENTO,
  TipoInstrumentoConfig,
  ativoDoFiltro,
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
import { DocumentoConfigModalComponent, ValorDocumentoConfig } from './documento-config-modal.component';

const TAMANHO_PAGINA = 20;
const TAMANHO_CATALOGO = 100;
const DURACAO_MENSAGEM_MS = 4000;

/** Procedimento a abrir já escolhido, vindo da ação «Itens documentais» do separador de Procedimentos. */
export interface SelecaoProcedimento {
  tipoInstrumentoId: number;
  dinamicaConfigId: number;
  procedimentoConfigId: number;
}

/** Itens documentais de um Procedimento (API-DOC-013), pela `ordem`. */
@Component({
  selector: 'app-configuracao-itens-documentais',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    DocumentoConfigModalComponent,
    ErrorBannerComponent,
    PaginacaoComponent,
    ReactiveFormsModule,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './configuracao-itens-documentais.component.html',
  styleUrl: './configuracao.css',
  host: {
    '(document:click)': 'fecharMenu()',
    '(document:keydown.escape)': 'fecharMenu(true)',
    '(window:scroll)': 'fecharMenu()',
    '(window:resize)': 'fecharMenu()',
  },
})
export class ConfiguracaoItensDocumentaisComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly selecaoInicial = input<SelecaoProcedimento | null>(null);

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly opcoesEstado: readonly { valor: FiltroEstadoConfig; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todos' },
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
  ];
  readonly colunas: ColunaTabela[] = [
    { chave: 'ordem', titulo: 'Ordem', largura: '5%' },
    { chave: 'separador', titulo: 'Separador', largura: '12%' },
    { chave: 'item', titulo: 'Item' },
    { chave: 'caixa', titulo: 'Caixa', largura: '10%' },
    { chave: 'tipo', titulo: 'Tipo', largura: '9%' },
    { chave: 'formato', titulo: 'Formato', largura: '9%' },
    { chave: 'extensoes', titulo: 'Extensões', largura: '11%' },
    { chave: 'obrigatorio', titulo: 'Obrigatório', largura: '8%' },
    { chave: 'informacaoBase', titulo: 'Informação Base', largura: '9%' },
    { chave: 'estado', titulo: 'Estado', largura: '8%' },
    { chave: 'acoes', titulo: 'Ações', largura: '5%' },
  ];

  readonly tiposInstrumento = signal<TipoInstrumentoConfig[]>([]);
  readonly tipoInstrumento = new FormControl<string | null>(null);
  readonly tipoInstrumentoId = signal<number | null>(null);
  readonly dinamicas = signal<DinamicaConfig[]>([]);
  readonly dinamica = new FormControl<string | null>(null);
  readonly dinamicaConfigId = signal<number | null>(null);
  readonly procedimentos = signal<ProcedimentoConfig[]>([]);
  readonly procedimento = new FormControl<string | null>(null);
  readonly procedimentoConfigId = signal<number | null>(null);
  readonly filtroEstado = signal<FiltroEstadoConfig>('todos');
  readonly pagina = signal(0);
  readonly linhas = signal<DocumentoConfig[]>([]);
  readonly total = signal(0);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly emEdicao = signal<DocumentoConfig | null>(null);
  readonly modalAberto = signal(false);
  readonly aGravar = signal(false);
  readonly erroModal = signal<ApiError | null>(null);
  readonly confirmacao = signal<DocumentoConfig | null>(null);
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
  readonly opcoesProcedimento = computed<OpcaoSeletor[]>(() =>
    this.procedimentos().map((procedimento) => ({
      valor: String(procedimento.id),
      rotulo: `${procedimento.tipoProcedimento.nome}${procedimento.ativo ? '' : ' (inativo)'}`,
    })),
  );
  readonly nomeProcedimento = computed(() => {
    const dinamica = this.dinamicas().find((d) => d.id === this.dinamicaConfigId());
    const procedimento = this.procedimentos().find((p) => p.id === this.procedimentoConfigId());
    return dinamica && procedimento
      ? `${dinamica.tipoInstrumento.codigo} — ${dinamica.tipoDinamica.nome} — ${procedimento.tipoProcedimento.nome}`
      : '';
  });
  readonly legenda = computed(() => `Itens documentais de ${this.nomeProcedimento()}`);
  readonly contador = computed(() => `${this.total()} ${this.total() === 1 ? 'item' : 'itens'}`);
  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar item documental' : 'Reativar item documental'));
  readonly mensagemConfirmacao = computed(() => {
    const item = this.confirmacao();
    if (!item) {
      return '';
    }
    return item.ativo
      ? `Desativar o item ${item.nome}? Deixa de ser pedido, também nos processos em curso deste Procedimento; os ficheiros já carregados mantêm-se.`
      : `Reativar o item ${item.nome}? Volta a ser pedido nos processos deste Procedimento.`;
  });

  private selecaoPendente: SelecaoProcedimento | null = null;
  private botaoMenu: HTMLElement | null = null;
  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private listagem: Subscription | undefined;
  private listagemDinamicas: Subscription | undefined;
  private listagemProcedimentos: Subscription | undefined;

  ngOnInit(): void {
    this.selecaoPendente = this.selecaoInicial();
    this.tipoInstrumento.valueChanges.subscribe((valor) => {
      this.tipoInstrumentoId.set(valor ? Number(valor) : null);
      this.carregarDinamicas();
    });
    this.dinamica.valueChanges.subscribe((valor) => {
      this.dinamicaConfigId.set(valor ? Number(valor) : null);
      this.carregarProcedimentos();
    });
    this.procedimento.valueChanges.subscribe((valor) => {
      this.procedimentoConfigId.set(valor ? Number(valor) : null);
      this.recarregar();
    });
    this.configuracaoService.listarTiposInstrumento({}, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => {
        const tipos = [...resposta.content].sort((a, b) => a.codigo.localeCompare(b.codigo));
        this.tiposInstrumento.set(tipos);
        const escolhido = this.escolher(tipos, this.selecaoPendente?.tipoInstrumentoId);
        if (escolhido) {
          this.tipoInstrumento.setValue(String(escolhido.id));
        } else {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.listagem?.unsubscribe();
    this.listagemDinamicas?.unsubscribe();
    this.listagemProcedimentos?.unsubscribe();
  }

  simNao(valor: boolean): string {
    return valor ? 'Sim' : 'Não';
  }

  rotuloCaixa(item: DocumentoConfig): string {
    return ROTULOS_CAIXA[item.categoria];
  }

  rotuloTipo(item: DocumentoConfig): string {
    return ROTULOS_TIPO_ELEMENTO[item.tipoElemento];
  }

  rotuloFormato(item: DocumentoConfig): string {
    return ROTULOS_FORMATO[item.formato];
  }

  rotuloInformacaoBase(item: DocumentoConfig): string {
    return item.elementoInformacaoBase ? ROTULOS_INFORMACAO_BASE[item.elementoInformacaoBase] : '—';
  }

  rotuloExtensoes(item: DocumentoConfig): string {
    return item.extensoesAceites.join(', ');
  }

  /** Há itens com o mesmo nome na Publicação e no Depósito: a caixa distingue os botões. */
  rotuloAcoes(item: DocumentoConfig): string {
    return `Mais opções para ${item.nome} (${this.rotuloCaixa(item)})`;
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

  alternarMenu(item: DocumentoConfig, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAbertoId() === item.id) {
      this.fecharMenu();
      return;
    }
    this.botaoMenu = evento.currentTarget as HTMLElement;
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(item.id);
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

  editar(item: DocumentoConfig): void {
    this.voltarAoBotaoMenu();
    this.abrirModal(item);
  }

  pedirAlternarEstado(item: DocumentoConfig): void {
    this.voltarAoBotaoMenu();
    this.confirmacao.set(item);
  }

  fecharModal(): void {
    if (!this.aGravar()) {
      this.modalAberto.set(false);
    }
  }

  guardar(valor: ValorDocumentoConfig): void {
    const instrumentoProcedimentoConfigId = this.procedimentoConfigId();
    if (instrumentoProcedimentoConfigId === null) {
      return;
    }
    const item = this.emEdicao();
    this.aGravar.set(true);
    this.erroModal.set(null);
    const gravacao = item
      ? this.configuracaoService.atualizarDocumentoConfig(item.id, valor.pedido)
      : this.configuracaoService.criarDocumentoConfig({ ...valor.pedido, instrumentoProcedimentoConfigId, codigo: valor.codigo ?? '' });
    gravacao.subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalAberto.set(false);
        this.mostrarMensagem(item ? 'Item documental atualizado.' : 'Item documental criado.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        if (erro.codigo === 'SEM_PERMISSAO') {
          this.modalAberto.set(false);
          this.erroAcao.set(erro);
        } else {
          this.erroModal.set(erro);
        }
      },
    });
  }

  confirmarAlternarEstado(): void {
    const item = this.confirmacao();
    if (!item || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.configuracaoService.atualizarDocumentoConfig(item.id, { ...this.pedidoDe(item), ativo: !item.ativo }).subscribe({
      next: () => {
        this.aProcessar.set(false);
        this.confirmacao.set(null);
        this.mostrarMensagem(item.ativo ? 'Item documental desativado.' : 'Item documental reativado.');
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

  /** Sem `regrasTiff`: o servidor mantém as gravadas. */
  private pedidoDe(item: DocumentoConfig): AtualizarDocumentoConfigPedido {
    return {
      nome: item.nome,
      separador: item.separador,
      descricao: item.descricao,
      categoria: item.categoria,
      tipoElemento: item.tipoElemento,
      formato: item.formato,
      extensoesAceites: item.extensoesAceites,
      obrigatorio: item.obrigatorio,
      ordem: item.ordem,
      elementoInformacaoBase: item.elementoInformacaoBase,
      tamanhoMaximoFicheiro: item.tamanhoMaximoFicheiro,
      tamanhoMaximoTotal: item.tamanhoMaximoTotal,
      numeroMaximoFicheiros: item.numeroMaximoFicheiros,
      linkPublicoPermitido: item.linkPublicoPermitido,
      ativo: item.ativo,
      regraNomenclatura: item.regraNomenclatura,
    };
  }

  private abrirModal(item: DocumentoConfig | null): void {
    this.erroModal.set(null);
    this.emEdicao.set(item);
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

  /** O da seleção inicial, se existir; senão o primeiro. */
  private escolher<T extends { id: number }>(opcoes: readonly T[], preferido: number | undefined): T | undefined {
    return opcoes.find((opcao) => opcao.id === preferido) ?? opcoes[0];
  }

  private limparLinhas(): void {
    this.listagem?.unsubscribe();
    this.linhas.set([]);
    this.total.set(0);
  }

  /** Mudar o tipo limpa a Dinâmica e o Procedimento e escolhe os primeiros. */
  private carregarDinamicas(): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    this.listagemDinamicas?.unsubscribe();
    this.listagemProcedimentos?.unsubscribe();
    this.dinamicas.set([]);
    this.procedimentos.set([]);
    this.limparLinhas();
    if (tipoInstrumentoId === null) {
      return;
    }
    this.aCarregar.set(true);
    this.erro.set(null);
    this.listagemDinamicas = this.configuracaoService.listarDinamicasConfig({ tipoInstrumentoId }, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => {
        this.dinamicas.set(resposta.content);
        const escolhida = this.escolher(resposta.content, this.selecaoPendente?.dinamicaConfigId);
        this.dinamica.setValue(escolhida ? String(escolhida.id) : null);
        if (!escolhida) {
          this.aCarregar.set(false);
        }
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  private carregarProcedimentos(): void {
    const instrumentoDinamicaConfigId = this.dinamicaConfigId();
    this.listagemProcedimentos?.unsubscribe();
    this.procedimentos.set([]);
    this.limparLinhas();
    if (instrumentoDinamicaConfigId === null) {
      this.procedimento.setValue(null, { emitEvent: false });
      this.procedimentoConfigId.set(null);
      return;
    }
    this.aCarregar.set(true);
    this.erro.set(null);
    this.listagemProcedimentos = this.configuracaoService
      .listarProcedimentosConfig({ instrumentoDinamicaConfigId }, 0, TAMANHO_CATALOGO)
      .subscribe({
        next: (resposta) => {
          this.procedimentos.set(resposta.content);
          const escolhido = this.escolher(resposta.content, this.selecaoPendente?.procedimentoConfigId);
          this.selecaoPendente = null;
          this.procedimento.setValue(escolhido ? String(escolhido.id) : null);
          if (!escolhido) {
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
    const instrumentoProcedimentoConfigId = this.procedimentoConfigId();
    this.listagem?.unsubscribe();
    if (instrumentoProcedimentoConfigId === null) {
      this.linhas.set([]);
      this.total.set(0);
      return;
    }
    this.aCarregar.set(!manterLinhas);
    this.erro.set(null);
    this.listagem = this.configuracaoService
      .listarDocumentoConfig(
        { instrumentoProcedimentoConfigId, ativo: ativoDoFiltro(this.filtroEstado()) },
        this.pagina(),
        TAMANHO_PAGINA,
      )
      .subscribe({
        next: (resposta) => {
          // Desativar o último item de uma página com o filtro «Ativos» deixa-a vazia: volta à última que tem itens.
          if (resposta.content.length === 0 && this.pagina() > 0) {
            this.pagina.set(Math.max(0, Math.ceil(resposta.totalElements / TAMANHO_PAGINA) - 1));
            this.carregar(manterLinhas);
            return;
          }
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
