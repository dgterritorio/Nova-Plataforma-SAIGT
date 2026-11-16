import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, OnDestroy, OnInit, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, debounceTime, distinctUntilChanged, map, of, switchMap, catchError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { Entidade } from '../../../models/entidade.model';
import {
  EntidadeVinculo,
  PERFIL_INTERNO_LABEL,
  PERFIS_INTERNOS,
  PerfilInterno,
  Utilizador,
  UtilizadorFiltro,
} from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { AlterarPerfilModalComponent } from '../../../shared/components/alterar-perfil-modal.component';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { ROUTES } from '../../../shared/routes';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoPesquisaComponent } from '../../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { PaginacaoComponent } from '../../../shared/ui/paginacao/paginacao.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { SeloComponent, TomSelo } from '../../../shared/ui/selo/selo.component';
import { Separador, SeparadoresComponent } from '../../../shared/ui/separadores/separadores.component';
import { PosicaoMenu, posicaoMenu } from '../posicao-menu';
import { Acesso, DESCRICAO_PERFIL, MATRIZ_PERMISSOES_PERFIS } from './matriz-permissoes-perfis';

export const TAMANHO_PAGINA_UTILIZADORES = 20;
export const ESPERA_PESQUISA_ENTIDADE_MS = 400;
const TAMANHO_PESQUISA_ENTIDADE = 20;
const DURACAO_ALERTA_SUCESSO_MS = 4000;

export type IdSeparadorUtilizadores = 'perfis' | 'dgt' | 'incm';
type Origem = 'AD_DGT' | 'INCM';

/** Estado próprio de cada lista: mudar de separador não perde a página nem a pesquisa da outra. */
interface EstadoLista {
  origem: Origem;
  pagina: WritableSignal<number>;
  totalElementos: WritableSignal<number>;
  utilizadores: WritableSignal<Utilizador[]>;
  aCarregar: WritableSignal<boolean>;
  erro: WritableSignal<ApiError | null>;
  pesquisa: WritableSignal<string>;
  linhas: Signal<LinhaUtilizador[]>;
}

export interface LinhaUtilizador {
  utilizador: Utilizador;
  nome: string;
  iniciais: string;
  entidadesOrdenadas: EntidadeVinculo[];
  entidadesAdicionais: number;
  podeExpandir: boolean;
}

/** Filtro por entidade aplicado na lista INCM; com `atuouDesde` é o roster de quem atuou desde a data. */
interface FiltroEntidade {
  entidadeId: number;
  atuouDesde: string | null;
}

const ROTULO_ACESSO: Record<Acesso, string> = { PERMITIDO: 'Permitido', PARCIAL: 'Parcial', SEM_ACESSO: 'Sem acesso' };
const TOM_ACESSO: Record<Acesso, TomSelo> = { PERMITIDO: 'sucesso', PARCIAL: 'aviso', SEM_ACESSO: 'cinza' };

/**
 * Pesquisa por nome/email só sobre a página carregada: o backend não tem parâmetro de texto livre.
 * Alterar o próprio perfil termina a sessão: as permissões em memória só se recarregam no login.
 */
@Component({
  selector: 'app-utilizadores',
  standalone: true,
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    AlterarPerfilModalComponent,
    ConfirmacaoModalComponent,
    AlertaComponent,
    BotaoComponent,
    CampoPesquisaComponent,
    CampoTextoComponent,
    CartaoComponent,
    PaginacaoComponent,
    SeletorComponent,
    SeparadoresComponent,
    SeloComponent,
    ReactiveFormsModule,
    NgTemplateOutlet,
    DatePipe,
  ],
  templateUrl: './utilizadores.component.html',
  styleUrls: ['../backoffice.css', './utilizadores.component.css'],
  host: { '(document:click)': 'fecharMenu()', '(window:scroll)': 'fecharMenu()', '(window:resize)': 'fecharMenu()' },
})
export class UtilizadoresComponent implements OnInit, OnDestroy {
  private readonly utilizadorService = inject(UtilizadorService);
  private readonly entidadeService = inject(EntidadeService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly tamanhoPagina = TAMANHO_PAGINA_UTILIZADORES;
  readonly perfis = PERFIS_INTERNOS;
  readonly matriz = MATRIZ_PERMISSOES_PERFIS;

  readonly dgt = this.criarEstadoLista('AD_DGT');
  readonly incm = this.criarEstadoLista('INCM');
  /** Total sem filtro por entidade: o cabeçalho e o separador não mudam ao filtrar. */
  readonly totalIncmRegistados = signal(0);
  readonly totalRegistados = computed(() => this.dgt.totalElementos() + this.totalIncmRegistados());

  readonly separadorAtivo = signal<string>('dgt');
  readonly separadores = computed<Separador[]>(() => [
    { id: 'perfis', etiqueta: 'Perfis e permissões', icone: 'icones/administracao/perfis.svg' },
    { id: 'dgt', etiqueta: 'Utilizadores DGT', icone: 'icones/administracao/utilizadores.svg', contador: this.dgt.totalElementos() },
    { id: 'incm', etiqueta: 'Utilizadores INCM', icone: 'icones/administracao/utilizadores.svg', contador: this.totalIncmRegistados() },
  ]);

  readonly perfilSelecionado = signal<PerfilInterno>('MASTER');
  readonly contagemPerfil = signal<Record<PerfilInterno, number | null>>({ MASTER: null, TECNICO: null, VISITANTE: null });

  readonly pesquisaEntidadeControlo = new FormControl('', { nonNullable: true });
  readonly entidadeControlo = new FormControl<string | null>(null);
  readonly atuouDesdeControlo = new FormControl('', { nonNullable: true });
  readonly filtroEntidadeForm = new FormGroup({
    pesquisa: this.pesquisaEntidadeControlo,
    entidade: this.entidadeControlo,
    atuouDesde: this.atuouDesdeControlo,
  });
  readonly opcoesEntidade = signal<OpcaoSeletor[]>([]);
  readonly aPesquisarEntidade = signal(false);
  readonly erroPesquisaEntidade = signal<string | null>(null);
  readonly entidadeEscolhida = signal<string | null>(null);
  readonly filtroEntidade = signal<FiltroEntidade | null>(null);
  readonly emRoster = computed(() => !!this.filtroEntidade()?.atuouDesde);

  readonly menuAbertoId = signal<string | null>(null);
  readonly menuPosicao = signal<PosicaoMenu>({ topo: 0, direita: 0 });

  readonly alterarPerfilAberto = signal(false);
  readonly confirmarSessoesAberto = signal(false);
  readonly aProcessar = signal(false);
  readonly alvo = signal<Utilizador | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  /** Erro de uma ação: fica até à ação seguinte e não esconde a lista. */
  readonly erroAcao = signal<ApiError | null>(null);
  readonly expandidos = signal<ReadonlySet<string>>(new Set());

  private temporizadorSucesso: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.pesquisaEntidadeControlo.valueChanges
      .pipe(
        map((termo) => termo.trim()),
        debounceTime(ESPERA_PESQUISA_ENTIDADE_MS),
        distinctUntilChanged(),
        switchMap((termo) => this.pesquisarEntidades(termo)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((opcoes) => this.opcoesEntidade.set(opcoes));
    this.entidadeControlo.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((valor) => this.entidadeEscolhida.set(valor));
  }

  ngOnInit(): void {
    this.carregarContagens();
    this.carregar(this.dgt);
    this.carregar(this.incm);
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorSucesso);
  }

  rotuloPerfil(perfil: PerfilInterno): string {
    return PERFIL_INTERNO_LABEL[perfil];
  }

  descricaoPerfil(perfil: PerfilInterno): string {
    return DESCRICAO_PERFIL[perfil];
  }

  rotuloAcesso(acesso: Acesso): string {
    return ROTULO_ACESSO[acesso];
  }

  tomAcesso(acesso: Acesso): TomSelo {
    return TOM_ACESSO[acesso];
  }

  /** O alerta e o erro de uma ação dizem respeito ao separador onde foi feita. */
  limparAvisos(): void {
    clearTimeout(this.temporizadorSucesso);
    this.mensagemSucesso.set(null);
    this.erroAcao.set(null);
  }

  escolherPerfil(perfil: PerfilInterno): void {
    this.perfilSelecionado.set(perfil);
  }

  nomeEntidade(item: EntidadeVinculo): string {
    return item.entidadeNome ?? String(item.entidadeId);
  }

  estaExpandido(utilizadorId: string): boolean {
    return this.expandidos().has(utilizadorId);
  }

  alternarExpansaoEntidades(utilizadorId: string, evento: Event): void {
    evento.stopPropagation();
    const atual = new Set(this.expandidos());
    if (!atual.delete(utilizadorId)) {
      atual.add(utilizadorId);
    }
    this.expandidos.set(atual);
  }

  irParaPagina(lista: EstadoLista, pagina: number): void {
    lista.pagina.set(pagina);
    this.expandidos.set(new Set());
    this.carregar(lista);
  }

  aplicarFiltroEntidade(): void {
    const entidadeId = this.entidadeEscolhida();
    if (!entidadeId) {
      return;
    }
    this.filtroEntidade.set({ entidadeId: Number(entidadeId), atuouDesde: this.atuouDesdeControlo.value || null });
    this.incm.pagina.set(0);
    this.carregar(this.incm);
  }

  limparFiltroEntidade(): void {
    this.pesquisaEntidadeControlo.setValue('', { emitEvent: false });
    this.entidadeControlo.setValue(null);
    this.atuouDesdeControlo.setValue('');
    this.opcoesEntidade.set([]);
    this.erroPesquisaEntidade.set(null);
    this.filtroEntidade.set(null);
    this.incm.pagina.set(0);
    this.carregar(this.incm);
  }

  alternarMenu(utilizadorId: string, evento: Event): void {
    evento.stopPropagation();
    this.menuPosicao.set(posicaoMenu(evento));
    this.menuAbertoId.set(this.menuAbertoId() === utilizadorId ? null : utilizadorId);
  }

  fecharMenu(): void {
    this.menuAbertoId.set(null);
  }

  /** O item do menu desaparece ao fechar: o foco passa para o «⋯», para o modal o devolver lá. */
  selecionarAlterarPerfil(utilizador: Utilizador, botaoMenu: HTMLElement): void {
    this.prepararAcao(utilizador, botaoMenu);
    this.alterarPerfilAberto.set(true);
  }

  selecionarTerminarSessoes(utilizador: Utilizador, botaoMenu: HTMLElement): void {
    this.prepararAcao(utilizador, botaoMenu);
    this.confirmarSessoesAberto.set(true);
  }

  nomeAlvo(): string {
    const alvo = this.alvo();
    return alvo ? this.nomeVisivel(alvo) : '';
  }

  mensagemTerminarSessoes(): string {
    return `Terminar as sessões ativas de ${this.nomeAlvo()}? A pessoa pode voltar a entrar de imediato.`;
  }

  confirmarAlterarPerfil(valor: { perfilInterno: PerfilInterno }): void {
    const alvo = this.alvo();
    if (!alvo || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.utilizadorService.alterarPerfilInterno(alvo.id, { perfilInterno: valor.perfilInterno }).subscribe({
      next: (resposta) => {
        this.aProcessar.set(false);
        this.alterarPerfilAberto.set(false);
        if (alvo.id === this.authService.utilizadorId()) {
          this.terminarSessaoPropria();
          return;
        }
        const perfilNovo = resposta.perfilInterno ?? valor.perfilInterno;
        this.dgt.utilizadores.update((lista) =>
          lista.map((utilizador) => (utilizador.id === alvo.id ? { ...utilizador, perfilInterno: perfilNovo } : utilizador)),
        );
        this.carregarContagens();
        this.mostrarSucesso(`Perfil de ${this.nomeVisivel(alvo)} alterado para ${this.rotuloPerfil(perfilNovo)}.`);
      },
      error: (erro: ApiError) => this.falharAcao(erro, () => this.alterarPerfilAberto.set(false)),
    });
  }

  confirmarTerminarSessoes(): void {
    const alvo = this.alvo();
    if (!alvo || this.aProcessar()) {
      return;
    }
    this.aProcessar.set(true);
    this.utilizadorService.revogarSessoes(alvo.id).subscribe({
      next: ({ sessoesRevogadas }) => {
        this.aProcessar.set(false);
        this.confirmarSessoesAberto.set(false);
        this.mostrarSucesso(sessoesRevogadas === 1 ? '1 sessão terminada.' : `${sessoesRevogadas} sessões terminadas.`);
      },
      error: (erro: ApiError) => this.falharAcao(erro, () => this.confirmarSessoesAberto.set(false)),
    });
  }

  nomeVisivel(utilizador: Utilizador): string {
    return utilizador.nome ?? utilizador.email ?? 'Utilizador sem nome';
  }

  private criarEstadoLista(origem: Origem): EstadoLista {
    const utilizadores = signal<Utilizador[]>([]);
    const pesquisa = signal('');
    return {
      origem,
      pagina: signal(0),
      totalElementos: signal(0),
      utilizadores,
      aCarregar: signal(true),
      erro: signal<ApiError | null>(null),
      pesquisa,
      linhas: computed(() => {
        const termo = pesquisa().trim().toLowerCase();
        return utilizadores()
          .filter(
            (utilizador) =>
              !termo ||
              (utilizador.nome ?? '').toLowerCase().includes(termo) ||
              (utilizador.email ?? '').toLowerCase().includes(termo),
          )
          .map((utilizador) => this.paraLinha(utilizador));
      }),
    };
  }

  private paraLinha(utilizador: Utilizador): LinhaUtilizador {
    const entidadesOrdenadas = [...utilizador.entidades].sort(
      (a, b) => Number(b.responsavel) - Number(a.responsavel) || this.nomeEntidade(a).localeCompare(this.nomeEntidade(b)),
    );
    return {
      utilizador,
      nome: this.nomeVisivel(utilizador),
      iniciais: this.iniciais(utilizador),
      entidadesOrdenadas,
      entidadesAdicionais: Math.max(entidadesOrdenadas.length - 1, 0),
      podeExpandir: entidadesOrdenadas.length > 1,
    };
  }

  private iniciais(utilizador: Utilizador): string {
    const base = utilizador.nome ?? utilizador.email ?? '';
    const partes = base
      .split(/[\s.]+/)
      .filter((parte) => parte.length > 0)
      .slice(0, 2)
      .map((parte) => parte[0]!.toUpperCase());
    return partes.join('') || '?';
  }

  private prepararAcao(utilizador: Utilizador, botaoMenu: HTMLElement): void {
    this.menuAbertoId.set(null);
    botaoMenu.focus();
    clearTimeout(this.temporizadorSucesso);
    this.mensagemSucesso.set(null);
    this.erroAcao.set(null);
    this.alvo.set(utilizador);
  }

  private falharAcao(erro: ApiError, fecharModal: () => void): void {
    this.aProcessar.set(false);
    fecharModal();
    this.mensagemSucesso.set(null);
    this.erroAcao.set(erro);
  }

  private mostrarSucesso(mensagem: string): void {
    this.erroAcao.set(null);
    this.mensagemSucesso.set(mensagem);
    clearTimeout(this.temporizadorSucesso);
    this.temporizadorSucesso = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_ALERTA_SUCESSO_MS);
  }

  private terminarSessaoPropria(): void {
    const irParaLogin = () => this.router.navigate([ROUTES.LOGIN], { queryParams: { erro: 'PERFIL_ALTERADO' } });
    this.authService.logout().subscribe({ next: irParaLogin, error: irParaLogin });
  }

  private pesquisarEntidades(termo: string): Observable<OpcaoSeletor[]> {
    this.erroPesquisaEntidade.set(null);
    if (!termo) {
      this.aPesquisarEntidade.set(false);
      return of([]);
    }
    this.aPesquisarEntidade.set(true);
    return this.entidadeService.listar({ nome: termo }, 0, TAMANHO_PESQUISA_ENTIDADE).pipe(
      map((pagina) => {
        this.aPesquisarEntidade.set(false);
        return pagina.content.map((entidade) => ({ valor: String(entidade.id), rotulo: this.rotuloEntidade(entidade) }));
      }),
      catchError(() => {
        this.aPesquisarEntidade.set(false);
        this.erroPesquisaEntidade.set('Não foi possível pesquisar as entidades.');
        return of([]);
      }),
    );
  }

  private rotuloEntidade(entidade: Entidade): string {
    return entidade.descricaoSaigt ?? entidade.descricaoIncm;
  }

  private carregarContagens(): void {
    for (const perfil of PERFIS_INTERNOS) {
      this.utilizadorService.listar({ origemAutenticacao: 'AD_DGT', perfilInterno: perfil }, 0, 1).subscribe({
        next: (pagina) => this.contagemPerfil.update((atual) => ({ ...atual, [perfil]: pagina.totalElements })),
        error: () => this.contagemPerfil.update((atual) => ({ ...atual, [perfil]: null })),
      });
    }
  }

  private carregar(lista: EstadoLista): void {
    lista.aCarregar.set(true);
    lista.erro.set(null);
    const filtro: UtilizadorFiltro = { origemAutenticacao: lista.origem };
    const filtroEntidade = lista.origem === 'INCM' ? this.filtroEntidade() : null;
    if (filtroEntidade) {
      filtro.entidadeId = filtroEntidade.entidadeId;
      filtro.atuouDesde = filtroEntidade.atuouDesde ?? undefined;
    }

    this.utilizadorService.listar(filtro, lista.pagina(), TAMANHO_PAGINA_UTILIZADORES).subscribe({
      next: (pagina) => {
        lista.utilizadores.set(pagina.content);
        lista.totalElementos.set(pagina.totalElements);
        if (lista.origem === 'INCM' && !filtroEntidade) {
          this.totalIncmRegistados.set(pagina.totalElements);
        }
        lista.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        lista.erro.set(erro);
        lista.aCarregar.set(false);
      },
    });
  }
}
