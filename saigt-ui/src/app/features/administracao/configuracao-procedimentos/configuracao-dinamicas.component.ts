import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  DinamicaConfig,
  DinamicaConfigRequest,
  FiltroEstadoConfig,
  ROTULOS_ASSOCIACAO,
  TipoDinamicaConfig,
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
import { DinamicaConfigModalComponent, ValorDinamicaConfig } from './dinamica-config-modal.component';

const TAMANHO_PAGINA = 20;
/** O máximo do contrato; os catálogos de tipos são pequenos e cabem numa página. */
const TAMANHO_CATALOGO = 100;
const DURACAO_MENSAGEM_MS = 4000;

/** A ordem é a que a API devolve: a do catálogo de tipos de Dinâmica, dentro de cada tipo de Instrumento. */
@Component({
  selector: 'app-configuracao-dinamicas',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    DinamicaConfigModalComponent,
    ErrorBannerComponent,
    PaginacaoComponent,
    ReactiveFormsModule,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './configuracao-dinamicas.component.html',
  styleUrl: './configuracao.css',
  host: {
    '(document:click)': 'fecharMenu()',
    '(document:keydown.escape)': 'fecharMenu(true)',
    '(window:scroll)': 'fecharMenu()',
    '(window:resize)': 'fecharMenu()',
  },
})
export class ConfiguracaoDinamicasComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly opcoesEstado: readonly { valor: FiltroEstadoConfig; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todas' },
    { valor: 'ativos', rotulo: 'Ativas' },
    { valor: 'inativos', rotulo: 'Inativas' },
  ];
  readonly colunas: ColunaTabela[] = [
    { chave: 'dinamica', titulo: 'Dinâmica' },
    { chave: 'codigo', titulo: 'Código', largura: '24%' },
    { chave: 'associacao', titulo: 'Associação de Instrumentos', largura: '22%' },
    { chave: 'estado', titulo: 'Estado', largura: '12%' },
    { chave: 'acoes', titulo: 'Ações', largura: '8%' },
  ];

  readonly tiposInstrumento = signal<TipoInstrumentoConfig[]>([]);
  readonly tiposDinamica = signal<TipoDinamicaConfig[]>([]);
  readonly tipoInstrumento = new FormControl<string | null>(null);
  readonly tipoInstrumentoId = signal<number | null>(null);
  readonly filtroEstado = signal<FiltroEstadoConfig>('todos');
  readonly pagina = signal(0);
  readonly linhas = signal<DinamicaConfig[]>([]);
  readonly total = signal(0);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly emEdicao = signal<DinamicaConfig | null>(null);
  readonly modalAberto = signal(false);
  readonly aGravar = signal(false);
  readonly erroModal = signal<ApiError | null>(null);
  readonly confirmacao = signal<DinamicaConfig | null>(null);
  readonly aProcessar = signal(false);
  readonly menuAbertoId = signal<number | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly opcoesTipoInstrumento = computed<OpcaoSeletor[]>(() =>
    this.tiposInstrumento().map((tipo) => ({ valor: String(tipo.id), rotulo: this.rotuloTipoInstrumento(tipo) })),
  );
  readonly nomeTipoInstrumento = computed(() => {
    const tipo = this.tiposInstrumento().find((t) => t.id === this.tipoInstrumentoId());
    return tipo ? `${tipo.codigo} — ${tipo.nome}` : '';
  });
  readonly legenda = computed(() => `Dinâmicas de ${this.tiposInstrumento().find((t) => t.id === this.tipoInstrumentoId())?.codigo ?? ''}`);
  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar Dinâmica' : 'Reativar Dinâmica'));
  readonly mensagemConfirmacao = computed(() => {
    const dinamica = this.confirmacao();
    if (!dinamica) {
      return '';
    }
    const nome = dinamica.tipoDinamica.nome;
    const tipo = dinamica.tipoInstrumento.codigo;
    return dinamica.ativo
      ? `Desativar a Dinâmica ${nome} em ${tipo}? Deixa de ser oferecida; as Dinâmicas em curso não são afetadas.`
      : `Reativar a Dinâmica ${nome} em ${tipo}? Volta a ser oferecida.`;
  });

  private botaoMenu: HTMLElement | null = null;
  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private listagem: Subscription | undefined;

  ngOnInit(): void {
    this.tipoInstrumento.valueChanges.subscribe((valor) => {
      this.tipoInstrumentoId.set(valor ? Number(valor) : null);
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
    this.configuracaoService.listarTiposDinamica({ ativo: true }, 0, TAMANHO_CATALOGO).subscribe({
      next: (resposta) => this.tiposDinamica.set(resposta.content),
      error: (erro: ApiError) => this.erroAcao.set(erro),
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.listagem?.unsubscribe();
  }

  rotuloAssociacao(dinamica: DinamicaConfig): string {
    return ROTULOS_ASSOCIACAO[dinamica.associacaoInstrumentos];
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

  alternarMenu(dinamica: DinamicaConfig, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAbertoId() === dinamica.id) {
      this.fecharMenu();
      return;
    }
    this.botaoMenu = evento.currentTarget as HTMLElement;
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(dinamica.id);
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

  editar(dinamica: DinamicaConfig): void {
    this.voltarAoBotaoMenu();
    this.abrirModal(dinamica);
  }

  pedirAlternarEstado(dinamica: DinamicaConfig): void {
    this.voltarAoBotaoMenu();
    this.confirmacao.set(dinamica);
  }

  fecharModal(): void {
    if (!this.aGravar()) {
      this.modalAberto.set(false);
    }
  }

  guardar(valor: ValorDinamicaConfig): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    if (tipoInstrumentoId === null) {
      return;
    }
    const dinamica = this.emEdicao();
    const pedido: DinamicaConfigRequest = { tipoInstrumentoId, ...valor, ativo: dinamica?.ativo ?? true };
    this.aGravar.set(true);
    this.erroModal.set(null);
    const gravacao = dinamica
      ? this.configuracaoService.atualizarDinamicaConfig(dinamica.id, { ...pedido, tipoInstrumentoId: dinamica.tipoInstrumento.id })
      : this.configuracaoService.criarDinamicaConfig(pedido);
    gravacao.subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalAberto.set(false);
        this.mostrarMensagem(dinamica ? 'Dinâmica atualizada.' : 'Dinâmica criada.');
        this.carregar(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.erroModal.set(erro);
      },
    });
  }

  confirmarAlternarEstado(): void {
    const dinamica = this.confirmacao();
    if (!dinamica || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.configuracaoService
      .atualizarDinamicaConfig(dinamica.id, {
        tipoInstrumentoId: dinamica.tipoInstrumento.id,
        tipoDinamicaId: dinamica.tipoDinamica.id,
        associacaoInstrumentos: dinamica.associacaoInstrumentos,
        ativo: !dinamica.ativo,
      })
      .subscribe({
        next: () => {
          this.aProcessar.set(false);
          this.confirmacao.set(null);
          this.mostrarMensagem(dinamica.ativo ? 'Dinâmica desativada.' : 'Dinâmica reativada.');
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

  private rotuloTipoInstrumento(tipo: TipoInstrumentoConfig): string {
    return `${tipo.codigo} — ${tipo.nome}${tipo.ativo ? '' : ' (inativo)'}`;
  }

  private abrirModal(dinamica: DinamicaConfig | null): void {
    this.erroModal.set(null);
    this.emEdicao.set(dinamica);
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

  private recarregar(): void {
    this.pagina.set(0);
    this.carregar();
  }

  /** `manterLinhas` (depois de gravar): sem o esqueleto, o botão "···" continua no ecrã e o modal devolve-lhe o foco. */
  private carregar(manterLinhas = false): void {
    const tipoInstrumentoId = this.tipoInstrumentoId();
    if (tipoInstrumentoId === null) {
      return;
    }
    this.listagem?.unsubscribe();
    this.aCarregar.set(!manterLinhas);
    this.erro.set(null);
    this.listagem = this.configuracaoService
      .listarDinamicasConfig({ tipoInstrumentoId, ativo: ativoDoFiltro(this.filtroEstado()) }, this.pagina(), TAMANHO_PAGINA)
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
