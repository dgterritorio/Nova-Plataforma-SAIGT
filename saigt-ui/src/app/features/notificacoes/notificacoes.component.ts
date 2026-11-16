import { Component, DestroyRef, ElementRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { NotificacaoFiltro, NotificacaoResumo, TipoNotificacao } from '../../models/notificacao.model';
import { ProcessoListagem } from '../../models/processo.model';
import { FILTRO_NAO_LIDAS, NotificacaoService } from '../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { ProcessoService } from '../../services/processo.service';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { AlertaComponent } from '../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../shared/ui/botao/botao.component';
import { CampoPesquisaComponent, ESPERA_PESQUISA_MS } from '../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CampoTextoComponent } from '../../shared/ui/campo-texto/campo-texto.component';
import { PaginacaoComponent } from '../../shared/ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../../shared/ui/seletor/seletor.component';
import { SeloComponent } from '../../shared/ui/selo/selo.component';
import { Separador, SeparadoresComponent } from '../../shared/ui/separadores/separadores.component';
import { dateRangeValidator } from '../../shared/validators/date-range.validator';
import {
  OPCOES_TIPO_NOTIFICACAO,
  apresentacaoTipo,
  ehNaoLida,
  formatarDataHora,
  formatarDia,
  normalizarPesquisa,
  remetente,
  seloEstado,
} from './apresentacao-notificacao';
import { CriarNotificacaoModalComponent, NotificacaoCriada } from './criar-notificacao-modal/criar-notificacao-modal.component';
import { IconeNotificacaoComponent } from './icone-notificacao.component';
import { NotificacaoDetalheComponent } from './notificacao-detalhe/notificacao-detalhe.component';

export type SeparadorNotificacoes = 'todas' | 'naoLidas' | 'historico';

export const TAMANHO_PAGINA_NOTIFICACOES = 20;

export const MENSAGEM_NAO_ENCONTRADA = 'A notificação não existe ou já não está disponível.';

export const MENSAGEM_INTERVALO_DATAS = 'A data «Até» não pode ser anterior à data «Desde».';

export const FILTRO_HISTORICO: NotificacaoFiltro = { estado: ['LIDA', 'RESOLVIDA'] };

const FILTROS: Record<Exclude<SeparadorNotificacoes, 'historico'>, NotificacaoFiltro> = {
  todas: {},
  naoLidas: FILTRO_NAO_LIDAS,
};

const TAMANHO_PESQUISA_PROCESSOS = 20;
const DATA_MAXIMA = '9999-12-31';

function rotuloProcesso(processo: ProcessoListagem): string {
  return [processo.numeroProcesso, processo.designacaoInstrumento, processo.nomeProcedimento]
    .filter((parte): parte is string => !!parte)
    .join(' — ');
}

@Component({
  selector: 'app-notificacoes',
  imports: [
    AppShellComponent,
    AlertaComponent,
    BotaoComponent,
    CampoPesquisaComponent,
    CampoTextoComponent,
    CriarNotificacaoModalComponent,
    PaginacaoComponent,
    ReactiveFormsModule,
    SeletorComponent,
    SeloComponent,
    SeparadoresComponent,
    IconeNotificacaoComponent,
    NotificacaoDetalheComponent,
  ],
  templateUrl: './notificacoes.component.html',
  styleUrl: './notificacoes.component.css',
})
export class NotificacoesComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly naoLidasService = inject(NotificacoesNaoLidasService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly processoService = inject(ProcessoService);
  private pedido: Subscription | null = null;
  private pesquisaProcessos: Subscription | null = null;

  readonly tamanhoPagina = TAMANHO_PAGINA_NOTIFICACOES;
  readonly separador = signal<SeparadorNotificacoes>('todas');
  readonly pagina = signal(0);
  readonly itens = signal<NotificacaoResumo[]>([]);
  readonly total = signal(0);
  readonly totalTodas = signal<number | null>(null);
  readonly totalNaoLidas = this.naoLidasService.naoLidas;
  private readonly daEntidade = computed(() => this.auth.perfilInterno() === null);
  /** O Master cria Notícias e Respostas obrigatórias; o utilizador externo, pedidos de apoio e regularizações. */
  readonly podeCriar = computed(() => this.auth.perfilInterno() === 'MASTER' || this.daEntidade());
  readonly criarAberto = signal(false);
  readonly sucesso = signal<string | null>(null);
  readonly avisoCriacao = signal<string | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<string | null>(null);
  readonly pesquisa = signal('');
  readonly selecionadaId = signal<string | null>(null);
  readonly avisoDetalhe = signal<string | null>(null);

  readonly filtrosHistorico = new FormGroup({
    tipo: new FormControl<TipoNotificacao | null>(null),
    processoId: new FormControl<string | null>(null),
    desde: new FormControl('', { nonNullable: true }),
    ate: new FormControl('', { nonNullable: true }),
  });
  readonly pesquisaProcesso = new FormControl('', { nonNullable: true });
  readonly processos = signal<ProcessoListagem[]>([]);
  readonly filtroHistoricoAplicado = signal<NotificacaoFiltro>(FILTRO_HISTORICO);
  readonly erroDatas = signal<string | null>(null);
  readonly opcoesTipo: readonly OpcaoSeletor[] = OPCOES_TIPO_NOTIFICACAO;
  readonly opcoesProcesso = computed<OpcaoSeletor[]>(() =>
    this.processos().map((processo) => ({ valor: processo.id, rotulo: rotuloProcesso(processo) })),
  );
  readonly noHistorico = computed(() => this.separador() === 'historico');

  readonly separadores = computed<Separador[]>(() => [
    { id: 'todas', etiqueta: 'Todas', contador: this.totalTodas() ?? undefined },
    { id: 'naoLidas', etiqueta: 'Não lidas', contador: this.totalNaoLidas() },
    { id: 'historico', etiqueta: 'Histórico' },
  ]);

  readonly itensVisiveis = computed(() => {
    const termo = normalizarPesquisa(this.pesquisa().trim());
    if (!termo) {
      return this.itens();
    }
    return this.itens().filter((n) =>
      [n.titulo, remetente(n.criadoPorNome), n.numeroProcesso ?? ''].some((campo) => normalizarPesquisa(campo).includes(termo)),
    );
  });

  readonly mensagemVazio = computed(() => {
    switch (this.separador()) {
      case 'naoLidas':
        return 'Não tem notificações por ler.';
      case 'historico':
        return 'Não há notificações lidas ou resolvidas com estes filtros.';
      default:
        return 'Não tem notificações.';
    }
  });

  readonly apresentacaoTipo = apresentacaoTipo;
  readonly seloEstado = seloEstado;
  readonly ehNaoLida = ehNaoLida;
  readonly remetente = remetente;
  readonly formatarDataHora = formatarDataHora;
  readonly formatarDia = formatarDia;

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros) => {
      this.selecionadaId.set(parametros.get('id'));
    });
    this.pesquisaProcesso.valueChanges
      .pipe(debounceTime(ESPERA_PESQUISA_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((termo) => this.pesquisarProcessos(termo));
    this.carregar();
  }

  carregar(): void {
    const separador = this.separador();
    this.pedido?.unsubscribe();
    this.aCarregar.set(true);
    this.erro.set(null);
    this.pedido = this.notificacaoService
      .listar(separador === 'historico' ? this.filtroHistoricoAplicado() : FILTROS[separador], this.pagina(), TAMANHO_PAGINA_NOTIFICACOES)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pagina) => {
          this.itens.set(pagina.content);
          this.total.set(pagina.totalElements);
          if (separador === 'todas') {
            this.totalTodas.set(pagina.totalElements);
          }
          this.aCarregar.set(false);
        },
        error: (erro: unknown) => {
          this.itens.set([]);
          this.erro.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível carregar as notificações.');
          this.aCarregar.set(false);
        },
      });
  }

  abrirCriacao(): void {
    this.sucesso.set(null);
    this.avisoCriacao.set(null);
    this.criarAberto.set(true);
  }

  notificacaoCriada(criada?: NotificacaoCriada): void {
    this.criarAberto.set(false);
    this.pagina.set(0);
    this.carregar();
    this.naoLidasService.atualizar();
    const falhados = criada?.anexosFalhados ?? 0;
    if (falhados > 0 && criada?.notificacoes.length === 1) {
      this.sucesso.set(null);
      this.avisoCriacao.set(`Notificação criada; ${falhados} documento(s) não foram anexados.`);
      this.selecionar(criada.notificacoes[0].id);
      return;
    }
    this.avisoCriacao.set(null);
    if (this.daEntidade()) {
      this.sucesso.set('Pedido enviado à DGT.');
      const primeira = criada?.notificacoes[0];
      if (primeira) {
        this.selecionar(primeira.id);
      }
      return;
    }
    this.sucesso.set('Notificação criada.');
  }

  mudarSeparador(id: string): void {
    this.separador.set(id as SeparadorNotificacoes);
    this.pagina.set(0);
    this.carregar();
  }

  aplicarFiltrosHistorico(evento?: Event): void {
    evento?.preventDefault();
    const { tipo, processoId, desde, ate } = this.filtrosHistorico.getRawValue();
    if (desde && ate && dateRangeValidator(desde, DATA_MAXIMA)(this.filtrosHistorico.controls.ate)) {
      this.erroDatas.set(MENSAGEM_INTERVALO_DATAS);
      return;
    }
    this.erroDatas.set(null);
    this.filtroHistoricoAplicado.set({
      ...FILTRO_HISTORICO,
      ...(tipo ? { tipo: [tipo] } : {}),
      ...(processoId ? { processoId } : {}),
      ...(desde ? { desde } : {}),
      ...(ate ? { ate } : {}),
    });
    this.pagina.set(0);
    this.carregar();
  }

  limparFiltrosHistorico(): void {
    this.filtrosHistorico.reset({ tipo: null, processoId: null, desde: '', ate: '' });
    this.pesquisaProcesso.reset('', { emitEvent: false });
    this.processos.set([]);
    this.aplicarFiltrosHistorico();
  }

  pesquisarProcessos(termo: string): void {
    this.pesquisaProcessos?.unsubscribe();
    const designacao = termo.trim();
    const escolhido = this.processos().find((p) => p.id === this.filtrosHistorico.controls.processoId.value);
    if (!designacao) {
      this.processos.set(escolhido ? [escolhido] : []);
      return;
    }
    this.pesquisaProcessos = this.processoService
      .listar({ designacaoInstrumento: designacao }, 0, TAMANHO_PESQUISA_PROCESSOS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pagina) => {
          const encontrados = pagina.content;
          this.processos.set(escolhido && !encontrados.some((p) => p.id === escolhido.id) ? [escolhido, ...encontrados] : encontrados);
        },
        error: () => this.processos.set(escolhido ? [escolhido] : []),
      });
  }

  mudarPagina(pagina: number): void {
    this.pagina.set(pagina - 1);
    this.carregar();
  }

  selecionar(id: string): void {
    this.avisoDetalhe.set(null);
    this.navegarParaSelecao(id);
  }

  fecharDetalhe(): void {
    const id = this.selecionadaId();
    this.navegarParaSelecao(null);
    // Abaixo de 768 px a lista só volta a existir depois da deteção de alterações.
    setTimeout(() => this.botaoDoItem(id)?.focus());
  }

  naoEncontrada(): void {
    this.avisoDetalhe.set(MENSAGEM_NAO_ENCONTRADA);
    this.navegarParaSelecao(null, true);
  }

  private navegarParaSelecao(id: string | null, substituir = false): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id },
      queryParamsHandling: 'merge',
      replaceUrl: substituir,
    });
  }

  private botaoDoItem(id: string | null): HTMLElement | null {
    if (!id) {
      return null;
    }
    const botoes = this.elemento.nativeElement.querySelectorAll<HTMLElement>('[data-notificacao]');
    return Array.from(botoes).find((botao) => botao.dataset['notificacao'] === id) ?? null;
  }
}
