import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, forkJoin, map, of, startWith } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AcaoNotificacao,
  DocumentoNotificacao,
  EstadoNotificacao,
  NotificacaoDetalhe,
  NotificacaoFiltro,
  NotificacaoResumo,
  TipoNotificacao,
} from '../../../models/notificacao.model';
import { LOCALIZACAO, MENSAGEM_ERRO_DESCARGA } from '../../../services/descarga.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../../services/notificacoes-nao-lidas.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { ROUTES } from '../../../shared/routes';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { Separador, SeparadoresComponent } from '../../../shared/ui/separadores/separadores.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import {
  SeloEstado,
  apresentacaoTipo,
  formatarDataHoraTabela,
  formatarDia,
  remetente,
  seloEstado,
  tamanhoEmKb,
  temConversacao,
} from '../../notificacoes/apresentacao-notificacao';
import { MAXIMO_RESPOSTA } from '../../notificacoes/notificacao-detalhe/notificacao-detalhe.component';

export type TipoSeparador = Extract<TipoNotificacao, 'PEDIDO_APOIO' | 'REGULARIZACAO_PROCEDIMENTO' | 'ALERTA' | 'ESTATICA'>;
export type FiltroEstadoGestao = 'ATIVAS' | 'HISTORICO';
export type OperacaoGestao = 'LER' | 'RESOLVER' | 'REMOVER';

const SEPARADORES: readonly { id: TipoSeparador; etiqueta: string }[] = [
  { id: 'PEDIDO_APOIO', etiqueta: 'Pedidos de apoio' },
  { id: 'REGULARIZACAO_PROCEDIMENTO', etiqueta: 'Regularizações' },
  { id: 'ALERTA', etiqueta: 'Alertas gerais' },
  { id: 'ESTATICA', etiqueta: 'Notícias' },
];

/** O Master vê no histórico as linhas que lhe são visíveis (BL-NOTIF-001). */
export const ESTADOS_HISTORICO: readonly EstadoNotificacao[] = ['LIDA', 'RESOLVIDA', 'REMOVIDA'];
export const TAMANHO_PAGINA_GESTAO = 20;
const DURACAO_SUCESSO_MS = 4000;

/** Uma notícia ativa não tem selo próprio no Centro de Notificações; aqui a coluna Estado nunca fica vazia. */
const SELO_ATIVA: SeloEstado = { rotulo: 'Ativa', tom: 'info' };

/** Aviso da UI-NOTIF-004: remover uma notícia de lote tira-a a todos os destinatários (API-NOTIF-008). */
export const AVISO_REMOVER_NOTICIA = 'A notícia deixa de estar visível para todos os destinatários do lote.';

function naoEmBranco(controlo: AbstractControl<string>): ValidationErrors | null {
  return controlo.value.trim() === '' ? { required: true } : null;
}

/**
 * Fila do backoffice (UI-NOTIF-004). O ecrã nunca decide sozinho que ação mostrar: segue `acoesDisponiveis` do
 * detalhe. A API é que autoriza; a rota só existe para o `adminGuard`.
 */
@Component({
  selector: 'app-gestao-notificacoes',
  imports: [
    AppShellComponent,
    ConfirmacaoModalComponent,
    ErrorBannerComponent,
    AlertaComponent,
    BotaoComponent,
    CampoTextoComponent,
    CartaoComponent,
    ModalComponent,
    PaginacaoComponent,
    SeparadoresComponent,
    SeletorComponent,
    SeloComponent,
    TabelaComponent,
    CelulaTabelaDirective,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './gestao-notificacoes.component.html',
  styleUrls: ['../backoffice.css', './gestao-notificacoes.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GestaoNotificacoesComponent implements OnInit, OnDestroy {
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly naoLidas = inject(NotificacoesNaoLidasService);
  private readonly localizacao = inject(LOCALIZACAO);
  private readonly documento = inject(DOCUMENT);
  private readonly painel = viewChild<ElementRef<HTMLElement>>('painel');
  private readonly estadoDetalheRef = viewChild<ElementRef<HTMLElement>>('estadoDetalheRef');

  readonly tamanhoPagina = TAMANHO_PAGINA_GESTAO;
  readonly maximoResposta = MAXIMO_RESPOSTA;
  readonly colunas: ColunaTabela[] = [
    { chave: 'titulo', titulo: 'Título', largura: '26%' },
    { chave: 'remetente', titulo: 'Remetente', largura: '16%' },
    { chave: 'processo', titulo: 'Processo', largura: '17%' },
    { chave: 'criadoEm', titulo: 'Recebida em', largura: '13%' },
    { chave: 'estado', titulo: 'Estado', largura: '12%' },
    { chave: 'numeroMensagens', titulo: 'Mensagens', largura: '8%', alinhamento: 'centro' },
    { chave: 'numeroDocumentos', titulo: 'Anexos', largura: '8%', alinhamento: 'centro' },
  ];
  readonly opcoesEstado: OpcaoSeletor[] = [
    { valor: 'ATIVAS', rotulo: 'Ativas' },
    { valor: 'HISTORICO', rotulo: 'Histórico' },
  ];
  readonly filtroEstadoControlo = new FormControl<string | null>('ATIVAS');

  readonly separador = signal<TipoSeparador>('PEDIDO_APOIO');
  readonly filtroEstado = signal<FiltroEstadoGestao>('ATIVAS');
  readonly pagina = signal(0);
  readonly itens = signal<NotificacaoResumo[]>([]);
  readonly total = signal(0);
  readonly contadores = signal<Partial<Record<TipoSeparador, number>>>({});
  readonly aCarregar = signal(true);
  readonly erro = signal<string | null>(null);
  readonly selecionada = signal<NotificacaoResumo | null>(null);

  readonly separadores = computed<Separador[]>(() =>
    SEPARADORES.map((s) => ({ id: s.id, etiqueta: s.etiqueta, contador: this.contadores()[s.id] })),
  );

  readonly detalhe = signal<NotificacaoDetalhe | null>(null);
  readonly erroDetalhe = signal<string | null>(null);
  readonly erroAcao = signal<ApiError | null>(null);
  readonly sucesso = signal<string | null>(null);
  readonly aEnviar = signal(false);
  readonly controloResposta = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, naoEmBranco, Validators.maxLength(MAXIMO_RESPOSTA)],
  });
  readonly erroCampoServidor = signal<string | null>(null);
  private readonly tocado = signal(false);
  private readonly valorResposta = toSignal(this.controloResposta.valueChanges.pipe(startWith('')), {
    initialValue: '',
  });

  /** As linhas do histórico abrem só em leitura, seja qual for o que a API ainda aceitaria. */
  readonly acoes = computed<readonly AcaoNotificacao[]>(() =>
    this.filtroEstado() === 'HISTORICO' ? [] : (this.detalhe()?.acoesDisponiveis ?? []),
  );
  readonly podeResponder = computed(() => this.acoes().includes('RESPONDER'));
  readonly podeLer = computed(() => this.acoes().includes('LER'));
  readonly podeResolver = computed(() => this.acoes().includes('RESOLVER'));
  readonly podeRemover = computed(() => this.acoes().includes('REMOVER'));
  readonly temOperacoes = computed(() => this.podeLer() || this.podeResolver() || this.podeRemover());
  readonly ehNoticia = computed(() => this.detalhe()?.tipo === 'ESTATICA');
  readonly rotuloRemover = computed(() => (this.ehNoticia() ? 'Remover notícia' : 'Descartar'));
  readonly aOperar = signal<OperacaoGestao | null>(null);
  readonly confirmacao = signal<Exclude<OperacaoGestao, 'LER'> | null>(null);
  readonly tituloConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Resolver sem responder';
    }
    return this.ehNoticia() ? 'Remover notícia' : 'Descartar alerta';
  });
  readonly mensagemConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Resolver este pedido de apoio sem enviar resposta?';
    }
    return this.ehNoticia() ? `Remover esta notícia? ${AVISO_REMOVER_NOTICIA}` : 'Descartar este alerta?';
  });
  readonly rotuloSimConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Resolver';
    }
    return this.ehNoticia() ? 'Remover' : 'Descartar';
  });
  /** `id` do anexo cujo ticket está a ser pedido (API-NOTIF-010). */
  readonly aDescarregar = signal<string | null>(null);
  readonly erroDescarga = signal<string | null>(null);
  readonly ehRegularizacao = computed(() => this.detalhe()?.tipo === 'REGULARIZACAO_PROCEDIMENTO');
  readonly rotuloResponder = computed(() => (this.ehRegularizacao() ? 'Pedir mais informação' : 'Responder'));
  readonly comConversacao = computed(() => {
    const n = this.detalhe();
    return n !== null && temConversacao(n.tipo);
  });
  readonly tipoDetalhe = computed(() => {
    const n = this.detalhe();
    return n ? apresentacaoTipo(n) : null;
  });
  readonly estadoDetalhe = computed(() => {
    const n = this.detalhe();
    return n ? this.selo(n) : null;
  });
  readonly respostaValida = computed(() => {
    const valor = this.valorResposta();
    return valor.trim() !== '' && valor.length <= MAXIMO_RESPOSTA;
  });
  readonly contador = computed(() => `${this.valorResposta().length}/${MAXIMO_RESPOSTA} caracteres`);
  readonly erroResposta = computed(() => {
    if (this.erroCampoServidor()) {
      return this.erroCampoServidor();
    }
    if (!this.tocado() || this.respostaValida()) {
      return null;
    }
    return this.valorResposta().length > MAXIMO_RESPOSTA
      ? `A resposta não pode ter mais de ${MAXIMO_RESPOSTA} caracteres.`
      : 'A resposta é obrigatória.';
  });

  readonly remetente = remetente;
  readonly formatarDataHora = formatarDataHoraTabela;
  readonly formatarDia = formatarDia;
  readonly tamanhoEmKb = tamanhoEmKb;
  readonly rotaProcesso = ROUTES.PROCESSO_DETALHE;

  private pedidoLista?: Subscription;
  private pedidoDetalhe?: Subscription;
  private temporizadorSucesso: ReturnType<typeof setTimeout> | undefined;
  private temporizadorFoco: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.filtroEstadoControlo.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.filtroEstado.set(valor === 'HISTORICO' ? 'HISTORICO' : 'ATIVAS');
      this.pagina.set(0);
      this.carregar();
    });
  }

  ngOnInit(): void {
    this.carregar();
    this.carregarContadores();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorSucesso);
    clearTimeout(this.temporizadorFoco);
    this.pedidoLista?.unsubscribe();
    this.pedidoDetalhe?.unsubscribe();
  }

  selo(notificacao: NotificacaoResumo): SeloEstado {
    return seloEstado(notificacao) ?? SELO_ATIVA;
  }

  mudarSeparador(id: string): void {
    this.separador.set(id as TipoSeparador);
    this.pagina.set(0);
    this.carregar();
  }

  irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar();
  }

  tentarDeNovo(): void {
    this.carregar();
    this.carregarContadores();
  }

  abrir(linha: NotificacaoResumo): void {
    this.selecionada.set(linha);
    this.detalhe.set(null);
    this.erroAcao.set(null);
    this.erroDescarga.set(null);
    this.confirmacao.set(null);
    this.limparSucesso();
    this.limparResposta();
    this.carregarDetalhe(linha.id);
  }

  fecharDetalhe(): void {
    this.pedidoDetalhe?.unsubscribe();
    this.selecionada.set(null);
    this.detalhe.set(null);
    this.erroDetalhe.set(null);
    this.erroAcao.set(null);
    this.erroDescarga.set(null);
    this.confirmacao.set(null);
    this.limparSucesso();
    this.limparResposta();
    this.aEnviar.set(false);
    // O modal devolve o foco à linha que o abriu; se ela saiu da lista entretanto (ex.: resolvida), o foco fica no painel.
    clearTimeout(this.temporizadorFoco);
    this.temporizadorFoco = setTimeout(() => {
      const ativo = this.documento.activeElement;
      if (!ativo || ativo === this.documento.body) {
        this.painel()?.nativeElement.focus();
      }
    });
  }

  marcarTocado(): void {
    this.tocado.set(true);
  }

  responder(evento?: Event): void {
    evento?.preventDefault();
    const n = this.detalhe();
    this.tocado.set(true);
    if (!n || !this.podeResponder() || !this.respostaValida() || this.aEnviar()) {
      return;
    }
    this.aEnviar.set(true);
    this.erroAcao.set(null);
    this.erroCampoServidor.set(null);
    this.limparSucesso();
    this.notificacaoService
      .responder(n.id, this.controloResposta.value.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aEnviar.set(false);
          this.limparResposta();
          this.detalhe.update((atual) =>
            atual && atual.id === n.id
              ? {
                  ...atual,
                  estado: resposta.estado,
                  mensagens: [...atual.mensagens, resposta.mensagem],
                  numeroMensagens: atual.numeroMensagens + 1,
                }
              : atual,
          );
          this.mostrarSucesso(this.mensagemSucesso(n.tipo, resposta.estado));
          this.reler(n.id);
        },
        error: (erro: unknown) => {
          this.aEnviar.set(false);
          if (!(erro instanceof ApiError)) {
            this.erroAcao.set(new ApiError('ERRO_INESPERADO', 'Não foi possível enviar a resposta.', '', null));
            return;
          }
          const doCampo = erro.codigo === 'VALIDACAO' ? erro.campos?.find((campo) => campo.path === 'texto')?.erro : null;
          if (doCampo) {
            this.erroCampoServidor.set(doCampo);
            return;
          }
          // 403/404/409: outro Master pode ter tratado a notificação primeiro; a linha mostra o estado atual.
          this.erroAcao.set(erro);
          this.reler(n.id);
        },
      });
  }

  marcarComoLido(): void {
    this.executar('LER');
  }

  pedirConfirmacao(operacao: Exclude<OperacaoGestao, 'LER'>): void {
    this.confirmacao.set(operacao);
  }

  cancelarConfirmacao(): void {
    if (!this.aOperar()) {
      this.confirmacao.set(null);
    }
  }

  confirmar(): void {
    const operacao = this.confirmacao();
    if (operacao) {
      this.executar(operacao);
    }
  }

  /**
   * O ticket é de uso curto (60 s): navega-se logo para o URL devolvido, sem o guardar nem ler os bytes aqui.
   * O `Content-Disposition: attachment` da descarga mantém a página e o modal abertos.
   */
  descarregar(documento: DocumentoNotificacao): void {
    const n = this.detalhe();
    if (!n || this.aDescarregar()) {
      return;
    }
    this.erroDescarga.set(null);
    this.aDescarregar.set(documento.id);
    this.notificacaoService
      .urlDownload(n.id, documento.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aDescarregar.set(null);
          this.localizacao.assign(resposta.url);
        },
        error: (erro: unknown) => {
          this.aDescarregar.set(null);
          this.erroDescarga.set(erro instanceof ApiError ? erro.mensagem : MENSAGEM_ERRO_DESCARGA);
        },
      });
  }

  private executar(operacao: OperacaoGestao): void {
    const n = this.detalhe();
    if (!n || this.aOperar() || !this.acoes().includes(operacao)) {
      return;
    }
    this.aOperar.set(operacao);
    this.erroAcao.set(null);
    this.limparSucesso();
    const pedido: Observable<Partial<NotificacaoResumo>> =
      operacao === 'REMOVER'
        ? this.notificacaoService.remover(n.id).pipe(map(() => ({ estado: 'REMOVIDA' as const })))
        : operacao === 'LER'
          ? this.notificacaoService.ler(n.id)
          : this.notificacaoService.resolver(n.id);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resumo) => {
        this.aOperar.set(null);
        this.confirmacao.set(null);
        // Os botões saem já; a releitura traz o estado e as ações que a API dá agora.
        this.detalhe.update((atual) => (atual && atual.id === n.id ? { ...atual, ...resumo, acoesDisponiveis: [] } : atual));
        this.mostrarSucesso(this.sucessoOperacao(operacao, n.tipo));
        this.naoLidas.atualizar();
        this.reler(n.id);
        this.focarEstado();
      },
      error: (erro: unknown) => {
        this.aOperar.set(null);
        this.confirmacao.set(null);
        // 403/404/409: outro Master pode ter tratado a notificação primeiro; a linha mostra o estado atual.
        this.erroAcao.set(
          erro instanceof ApiError ? erro : new ApiError('ERRO_INESPERADO', 'Não foi possível concluir a operação.', '', null),
        );
        this.reler(n.id);
        this.focarEstado();
      },
    });
  }

  private sucessoOperacao(operacao: OperacaoGestao, tipo: TipoNotificacao): string {
    if (operacao === 'LER') {
      return 'Alerta marcado como lido.';
    }
    if (operacao === 'RESOLVER') {
      return 'Pedido de apoio resolvido.';
    }
    return tipo === 'ESTATICA' ? 'Notícia removida para todos os destinatários.' : 'Alerta descartado.';
  }

  /** O botão usado desaparece: o foco passa aos selos do detalhe, que dizem o estado, e não cai no `body`. */
  private focarEstado(): void {
    clearTimeout(this.temporizadorFoco);
    this.temporizadorFoco = setTimeout(() => this.estadoDetalheRef()?.nativeElement.focus());
  }

  private mensagemSucesso(tipo: TipoNotificacao, estado: EstadoNotificacao): string {
    if (tipo === 'PEDIDO_APOIO' && estado === 'RESOLVIDA') {
      return 'Resposta enviada. O pedido de apoio ficou resolvido.';
    }
    return tipo === 'REGULARIZACAO_PROCEDIMENTO' ? 'Pedido de mais informação enviado.' : 'Resposta enviada.';
  }

  /** Depois de uma ação (ou de um erro dela): detalhe, página e contadores voltam a ser pedidos. */
  private reler(id: string): void {
    this.carregarDetalhe(id, true);
    this.carregar(true);
    this.carregarContadores();
  }

  private carregarDetalhe(id: string, manter = false): void {
    this.pedidoDetalhe?.unsubscribe();
    this.erroDetalhe.set(null);
    this.pedidoDetalhe = this.notificacaoService.obter(id).subscribe({
      next: (notificacao) => {
        if (this.selecionada()?.id === id) {
          this.detalhe.set(notificacao);
        }
      },
      error: (erro: unknown) => {
        if (manter && this.detalhe()) {
          return;
        }
        this.erroDetalhe.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível carregar a notificação.');
      },
    });
  }

  private carregar(manterLinhas = false): void {
    this.pedidoLista?.unsubscribe();
    this.aCarregar.set(!manterLinhas);
    this.erro.set(null);
    const filtro: NotificacaoFiltro = { tipo: [this.separador()] };
    if (this.filtroEstado() === 'HISTORICO') {
      filtro.estado = [...ESTADOS_HISTORICO];
    }
    this.pedidoLista = this.notificacaoService.listar(filtro, this.pagina(), TAMANHO_PAGINA_GESTAO).subscribe({
      next: (pagina) => {
        // A última linha de uma página saiu das ativas: volta à página anterior em vez de mostrar uma vazia.
        if (pagina.content.length === 0 && this.pagina() > 0 && pagina.totalElements > 0) {
          this.pagina.set(Math.max(0, pagina.totalPages - 1));
          this.carregar(manterLinhas);
          return;
        }
        this.itens.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.aCarregar.set(false);
      },
      error: (erro: unknown) => {
        this.itens.set([]);
        this.erro.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível carregar as notificações.');
        this.aCarregar.set(false);
      },
    });
  }

  private carregarContadores(): void {
    const ativas = (tipo: TipoSeparador): Observable<number | undefined> =>
      this.notificacaoService.listar({ tipo: [tipo], estado: ['ATIVA'] }, 0, 1).pipe(
        map((pagina) => pagina.totalElements),
        catchError(() => of(undefined)),
      );
    forkJoin({
      PEDIDO_APOIO: ativas('PEDIDO_APOIO'),
      REGULARIZACAO_PROCEDIMENTO: ativas('REGULARIZACAO_PROCEDIMENTO'),
      ALERTA: ativas('ALERTA'),
      ESTATICA: ativas('ESTATICA'),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((contadores) => this.contadores.set(contadores));
  }

  private mostrarSucesso(mensagem: string): void {
    clearTimeout(this.temporizadorSucesso);
    this.sucesso.set(mensagem);
    this.temporizadorSucesso = setTimeout(() => this.sucesso.set(null), DURACAO_SUCESSO_MS);
  }

  private limparSucesso(): void {
    clearTimeout(this.temporizadorSucesso);
    this.sucesso.set(null);
  }

  private limparResposta(): void {
    this.controloResposta.reset('');
    this.tocado.set(false);
    this.erroCampoServidor.set(null);
  }
}
