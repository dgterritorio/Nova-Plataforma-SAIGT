import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  FiltroEstadoConfig,
  RedeDefesaConfig,
  RedeDefesaConfigRequest,
  TipoAtoConfig,
  TipoAtoConfigRequest,
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
import { RedeDefesaConfigModalComponent } from './rede-defesa-config-modal.component';
import { TipoAtoConfigModalComponent } from './tipo-ato-config-modal.component';

const TAMANHO_PAGINA = 20;
const DURACAO_MENSAGEM_MS = 4000;

/** Dois catálogos pequenos na mesma página: os tipos de ato (com estado) e as redes de defesa do SGIFR (sem estado). */
@Component({
  selector: 'app-configuracao-atos-redes',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    PaginacaoComponent,
    RedeDefesaConfigModalComponent,
    SeloComponent,
    TabelaComponent,
    TipoAtoConfigModalComponent,
  ],
  templateUrl: './configuracao-atos-redes.component.html',
  styleUrl: './configuracao.css',
  host: {
    '(document:click)': 'fecharMenu()',
    '(document:keydown.escape)': 'fecharMenu(true)',
    '(window:scroll)': 'fecharMenu()',
    '(window:resize)': 'fecharMenu()',
  },
})
export class ConfiguracaoAtosRedesComponent implements OnInit, OnDestroy {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly tamanhoPagina = TAMANHO_PAGINA;
  readonly opcoesEstado: readonly { valor: FiltroEstadoConfig; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todos' },
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
  ];
  readonly colunasAtos: ColunaTabela[] = [
    { chave: 'ordem', titulo: 'Ordem', largura: '8%' },
    { chave: 'codigo', titulo: 'Código', largura: '14%' },
    { chave: 'nome', titulo: 'Nome' },
    { chave: 'serie1', titulo: 'ActTypeId 1.ª série', largura: '14%' },
    { chave: 'serie2', titulo: 'ActTypeId 2.ª série', largura: '14%' },
    { chave: 'estado', titulo: 'Estado', largura: '11%' },
    { chave: 'acoes', titulo: 'Ações', largura: '8%' },
  ];
  readonly colunasRedes: ColunaTabela[] = [
    { chave: 'codigo', titulo: 'Código', largura: '12%' },
    { chave: 'nome', titulo: 'Nome' },
    { chave: 'pra', titulo: 'PRA', largura: '9%' },
    { chave: 'psa', titulo: 'PSA', largura: '9%' },
    { chave: 'publicavel', titulo: 'Publicável', largura: '11%' },
    { chave: 'acoes', titulo: 'Ações', largura: '10%' },
  ];

  readonly filtroAtos = signal<FiltroEstadoConfig>('todos');
  readonly paginaAtos = signal(0);
  readonly atos = signal<TipoAtoConfig[]>([]);
  readonly totalAtos = signal(0);
  readonly aCarregarAtos = signal(true);
  readonly erroAtos = signal<ApiError | null>(null);
  readonly ato = signal<TipoAtoConfig | null>(null);
  readonly modalAtoAberto = signal(false);
  readonly erroModalAto = signal<ApiError | null>(null);
  readonly confirmacao = signal<TipoAtoConfig | null>(null);
  readonly aProcessar = signal(false);
  readonly menuAbertoId = signal<number | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly paginaRedes = signal(0);
  readonly redes = signal<RedeDefesaConfig[]>([]);
  readonly totalRedes = signal(0);
  readonly aCarregarRedes = signal(true);
  readonly erroRedes = signal<ApiError | null>(null);
  readonly rede = signal<RedeDefesaConfig | null>(null);
  readonly modalRedeAberto = signal(false);
  readonly erroModalRede = signal<ApiError | null>(null);

  readonly aGravar = signal(false);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);

  readonly tituloConfirmacao = computed(() => (this.confirmacao()?.ativo ? 'Desativar tipo de ato' : 'Reativar tipo de ato'));
  readonly mensagemConfirmacao = computed(() => {
    const ato = this.confirmacao();
    if (!ato) {
      return '';
    }
    return ato.ativo
      ? `Desativar o tipo de ato ${ato.nome}? Deixa de ser oferecido; os processos existentes não são afetados.`
      : `Reativar o tipo de ato ${ato.nome}? Volta a ser oferecido.`;
  });

  private botaoOrigem: HTMLElement | null = null;
  private temporizadorMensagem: ReturnType<typeof setTimeout> | undefined;
  private listagemAtos: Subscription | undefined;
  private listagemRedes: Subscription | undefined;

  ngOnInit(): void {
    this.carregarAtos();
    this.carregarRedes();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorMensagem);
    this.listagemAtos?.unsubscribe();
    this.listagemRedes?.unsubscribe();
  }

  simNao(valor: boolean): string {
    return valor ? 'Sim' : 'Não';
  }

  mudarFiltroAtos(filtro: FiltroEstadoConfig): void {
    if (filtro !== this.filtroAtos()) {
      this.filtroAtos.set(filtro);
      this.paginaAtos.set(0);
      this.carregarAtos();
    }
  }

  irParaPaginaAtos(pagina: number): void {
    this.paginaAtos.set(pagina);
    this.carregarAtos();
  }

  irParaPaginaRedes(pagina: number): void {
    this.paginaRedes.set(pagina);
    this.carregarRedes();
  }

  adicionarAto(): void {
    this.abrirAto(null);
  }

  alternarMenu(ato: TipoAtoConfig, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAbertoId() === ato.id) {
      this.fecharMenu();
      return;
    }
    this.botaoOrigem = evento.currentTarget as HTMLElement;
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(ato.id);
    setTimeout(() => this.anfitriao.nativeElement.querySelector<HTMLElement>('.dropdown-menu button')?.focus());
  }

  fecharMenu(devolverFoco = false): void {
    if (this.menuAbertoId() === null) {
      return;
    }
    this.menuAbertoId.set(null);
    if (devolverFoco) {
      this.botaoOrigem?.focus();
    }
  }

  editarAto(ato: TipoAtoConfig): void {
    this.voltarAoBotaoMenu();
    this.abrirAto(ato);
  }

  pedirAlternarEstado(ato: TipoAtoConfig): void {
    this.voltarAoBotaoMenu();
    this.confirmacao.set(ato);
  }

  fecharModalAto(): void {
    if (!this.aGravar()) {
      this.modalAtoAberto.set(false);
    }
  }

  guardarAto(pedido: TipoAtoConfigRequest): void {
    const ato = this.ato();
    this.aGravar.set(true);
    this.erroModalAto.set(null);
    const gravacao = ato ? this.configuracaoService.atualizarTipoAto(ato.id, pedido) : this.configuracaoService.criarTipoAto(pedido);
    gravacao.subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalAtoAberto.set(false);
        this.mostrarMensagem(ato ? 'Tipo de ato atualizado.' : 'Tipo de ato criado.');
        this.carregarAtos(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.erroModalAto.set(erro);
      },
    });
  }

  confirmarAlternarEstado(): void {
    const ato = this.confirmacao();
    if (!ato || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.erroAcao.set(null);
    this.configuracaoService
      .atualizarTipoAto(ato.id, {
        codigo: ato.codigo,
        nome: ato.nome,
        incmActTypeIdSerie1: ato.incmActTypeIdSerie1,
        incmActTypeIdSerie2: ato.incmActTypeIdSerie2,
        ordem: ato.ordem,
        ativo: !ato.ativo,
      })
      .subscribe({
        next: () => {
          this.aProcessar.set(false);
          this.confirmacao.set(null);
          this.mostrarMensagem(ato.ativo ? 'Tipo de ato desativado.' : 'Tipo de ato reativado.');
          this.carregarAtos(true);
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

  adicionarRede(): void {
    this.abrirRede(null);
  }

  editarRede(rede: RedeDefesaConfig): void {
    this.abrirRede(rede);
  }

  fecharModalRede(): void {
    if (!this.aGravar()) {
      this.modalRedeAberto.set(false);
    }
  }

  guardarRede(pedido: RedeDefesaConfigRequest): void {
    const rede = this.rede();
    this.aGravar.set(true);
    this.erroModalRede.set(null);
    const gravacao = rede
      ? this.configuracaoService.atualizarRedeDefesa(rede.id, pedido)
      : this.configuracaoService.criarRedeDefesa(pedido);
    gravacao.subscribe({
      next: () => {
        this.aGravar.set(false);
        this.modalRedeAberto.set(false);
        this.mostrarMensagem(rede ? 'Rede atualizada.' : 'Rede criada.');
        this.carregarRedes(true);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.erroModalRede.set(erro);
      },
    });
  }

  private abrirAto(ato: TipoAtoConfig | null): void {
    this.erroModalAto.set(null);
    this.ato.set(ato);
    this.modalAtoAberto.set(true);
  }

  private abrirRede(rede: RedeDefesaConfig | null): void {
    this.erroModalRede.set(null);
    this.rede.set(rede);
    this.modalRedeAberto.set(true);
  }

  private voltarAoBotaoMenu(): void {
    this.menuAbertoId.set(null);
    this.botaoOrigem?.focus();
  }

  private mostrarMensagem(mensagem: string): void {
    clearTimeout(this.temporizadorMensagem);
    this.mensagemSucesso.set(mensagem);
    this.temporizadorMensagem = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_MENSAGEM_MS);
  }

  private carregarAtos(manterLinhas = false): void {
    this.listagemAtos?.unsubscribe();
    this.aCarregarAtos.set(!manterLinhas);
    this.erroAtos.set(null);
    this.listagemAtos = this.configuracaoService
      .listarTiposAto({ ativo: ativoDoFiltro(this.filtroAtos()) }, this.paginaAtos(), TAMANHO_PAGINA)
      .subscribe({
        next: (resposta) => {
          this.atos.set(resposta.content);
          this.totalAtos.set(resposta.totalElements);
          this.aCarregarAtos.set(false);
        },
        error: (erro: ApiError) => {
          this.erroAtos.set(erro);
          this.aCarregarAtos.set(false);
        },
      });
  }

  private carregarRedes(manterLinhas = false): void {
    this.listagemRedes?.unsubscribe();
    this.aCarregarRedes.set(!manterLinhas);
    this.erroRedes.set(null);
    this.listagemRedes = this.configuracaoService.listarRedesDefesa(this.paginaRedes(), TAMANHO_PAGINA).subscribe({
      next: (resposta) => {
        this.redes.set(resposta.content);
        this.totalRedes.set(resposta.totalElements);
        this.aCarregarRedes.set(false);
      },
      error: (erro: ApiError) => {
        this.erroRedes.set(erro);
        this.aCarregarRedes.set(false);
      },
    });
  }
}
