import { Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { EMPTY, Observable, Subject, catchError, exhaustMap, finalize, startWith, switchMap, tap, timer } from 'rxjs';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { ApiError } from '../../../core/models/error-response.model';
import { EstatisticasAdmin } from '../../../models/admin.model';
import { PontoSerie, PreferenciaKpi, ValorKpi } from '../../../models/dashboard.model';
import { AdminService } from '../../../services/admin.service';
import { DashboardService } from '../../../services/dashboard.service';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { KpiGraficoComponent } from './kpi-grafico.component';

/** Polling dos KPIs enquanto o ecrã está aberto, sem push. */
export const PERIODO_ATUALIZACAO_MS = 60_000;

type GrupoEstado = 'PENDENTE' | 'EM_ANALISE' | 'VALIDADO' | 'REJEITADO';

/**
 * Agrupa os estados reais nos 4 estados macro do donut. `PENDENTE`: ainda não entrou em validação;
 * `EM_ANALISE`: em processamento na DGT ou INCM; `REJEITADO` inclui os anulados.
 */
const ESTADO_GRUPO: Record<string, GrupoEstado> = {
  EM_SUBMISSAO: 'PENDENTE',
  ENVIADO_DGT: 'PENDENTE',
  EM_VALIDACAO_DGT: 'EM_ANALISE',
  VALIDADO_DGT: 'EM_ANALISE',
  ENVIADO_INCM: 'EM_ANALISE',
  EM_VALIDACAO_INCM: 'EM_ANALISE',
  PUBLICADO: 'VALIDADO',
  REJEITADO_DGT: 'REJEITADO',
  ANULADO: 'REJEITADO',
  ANULADO_INCM: 'REJEITADO',
};

const GRUPOS: readonly GrupoEstado[] = ['VALIDADO', 'EM_ANALISE', 'PENDENTE', 'REJEITADO'];

const GRUPO_LABEL: Record<GrupoEstado, string> = {
  VALIDADO: 'Validado',
  EM_ANALISE: 'Em análise',
  PENDENTE: 'Pendente',
  REJEITADO: 'Rejeitado',
};

/** As cores da legenda do Figma (UI-058); o âmbar substitui o amarelo antigo, que ficava abaixo de 3:1 sobre branco. */
const GRUPO_COR: Record<GrupoEstado, string> = {
  VALIDADO: '--cor-sucesso-grafico',
  EM_ANALISE: '--cor-primaria',
  PENDENTE: '--cor-aviso-grafico',
  REJEITADO: '--cor-erro',
};

const MESES_PT = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

const FORMATO_HORA = new Intl.DateTimeFormat('pt-PT', {
  timeZone: 'Europe/Lisbon',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const CODIGOS_SEM_PERMISSAO = new Set(['SEM_PERMISSAO', 'ACAO_NAO_AUTORIZADA']);

export interface GrupoDonut {
  rotulo: string;
  valor: number;
  /** Token semântico da cor do grupo. */
  cor: string;
  /** Os estados reais do grupo, maior primeiro, ex. "Enviado DGT (4), Em submissão (2)". */
  dica: string;
}

export type BlocoKpi =
  | { tipo: 'numero'; kpi: ValorKpi }
  | { tipo: 'barras-horizontais'; kpi: ValorKpi; serie: PontoSerie[] }
  | { tipo: 'donut'; kpi: ValorKpi; grupos: GrupoDonut[]; cores: string[] }
  | { tipo: 'barras-verticais'; kpi: ValorKpi; serie: PontoSerie[] }
  | { tipo: 'generico'; kpi: ValorKpi };

/** Estado sem correspondência em `ESTADO_GRUPO` cai em `EM_ANALISE`. Os grupos saem do maior para o menor. */
export function agruparPorEstado(serie: PontoSerie[]): GrupoDonut[] {
  const totais = new Map<GrupoEstado, number>(GRUPOS.map((grupo) => [grupo, 0]));
  const estados = new Map<GrupoEstado, PontoSerie[]>();
  for (const ponto of serie) {
    const grupo = ESTADO_GRUPO[ponto.rotulo] ?? 'EM_ANALISE';
    totais.set(grupo, (totais.get(grupo) ?? 0) + ponto.valor);
    if (ponto.valor > 0) {
      estados.set(grupo, [...(estados.get(grupo) ?? []), ponto]);
    }
  }
  return GRUPOS.map((grupo) => ({
    rotulo: GRUPO_LABEL[grupo],
    valor: totais.get(grupo) ?? 0,
    cor: GRUPO_COR[grupo],
    dica: (estados.get(grupo) ?? [])
      .slice()
      .sort((a, b) => b.valor - a.valor)
      .map((ponto) => `${rotuloEstado(ponto.rotulo)} (${ponto.valor})`)
      .join(', '),
  })).sort((a, b) => b.valor - a.valor);
}

/** `AAAA-MM` → «Mmm AAAA»; um rótulo noutro formato fica como veio. */
export function rotuloMes(rotulo: string): string {
  const [ano, mes] = rotulo.split('-');
  const nome = MESES_PT[Number(mes) - 1];
  return nome && ano ? `${nome} ${ano}` : rotulo;
}

function blocoDe(kpi: ValorKpi): BlocoKpi {
  switch (kpi.codigo) {
    case 'PROCESSOS_POR_ESTADO':
      return { tipo: 'numero', kpi };
    case 'TOTAL_POR_TIPOLOGIA':
      return { tipo: 'barras-horizontais', kpi, serie: kpi.serie ?? [] };
    case 'GRAFICO_ESTADO': {
      const grupos = agruparPorEstado(kpi.serie ?? []);
      return { tipo: 'donut', kpi, grupos, cores: grupos.map((grupo) => grupo.cor) };
    }
    case 'GRAFICO_MES':
      return {
        tipo: 'barras-verticais',
        kpi,
        serie: (kpi.serie ?? []).map((ponto) => ({ rotulo: rotuloMes(ponto.rotulo), valor: ponto.valor })),
      };
    default:
      return { tipo: 'generico', kpi };
  }
}

/**
 * Os KPIs vêm das preferências do utilizador e do cálculo da plataforma (`/dashboard`); «Atividade recente» e
 * «IGT publicados por entidade» continuam a vir das estatísticas de administração. "Exportar CSV" não tem suporte no backend.
 */
@Component({
  selector: 'app-painel-administrativo',
  standalone: true,
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    ModalComponent,
    SeloComponent,
    TabelaComponent,
    CelulaTabelaDirective,
    KpiGraficoComponent,
    DatePipe,
  ],
  styleUrls: ['../backoffice.css', './painel-administrativo.component.css'],
  templateUrl: './painel-administrativo.component.html',
})
export class PainelAdministrativoComponent implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly dashboardService = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly botaoPersonalizar = viewChild('botaoPersonalizar', { read: ElementRef<HTMLButtonElement> });
  private readonly reinicio = new Subject<void>();

  readonly kpis = signal<ValorKpi[] | null>(null);
  readonly aCarregar = signal(false);
  readonly erroKpis = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);

  readonly estatisticas = signal<EstatisticasAdmin | null>(null);
  readonly erroEstatisticas = signal<ApiError | null>(null);

  readonly painelAberto = signal(false);
  readonly linhasPainel = signal<PreferenciaKpi[]>([]);
  readonly aCarregarPainel = signal(false);
  readonly gravando = signal(false);
  readonly erroPainel = signal<ApiError | null>(null);
  readonly anuncioPainel = signal('');
  /** KPIs visíveis nas preferências que o servidor devolveu (ao abrir ou ao gravar). */
  private readonly visiveisNoServidor = signal<ReadonlySet<string>>(new Set());

  readonly blocos = computed(() => (this.kpis() ?? []).map(blocoDe));
  readonly atualizadoAs = computed(() => {
    const instante = this.kpis()?.[0]?.calculadoEm;
    return instante ? FORMATO_HORA.format(new Date(instante)) : null;
  });
  private readonly codigosCalculados = computed(() => new Set((this.kpis() ?? []).map((kpi) => kpi.codigo)));

  readonly colunasEntidades: ColunaTabela[] = [
    { chave: 'entidade', titulo: 'Entidade', largura: '22%' },
    { chave: 'pdm', titulo: 'PDM', alinhamento: 'centro' },
    { chave: 'pp', titulo: 'PP', alinhamento: 'centro' },
    { chave: 'pu', titulo: 'PU', alinhamento: 'centro' },
    { chave: 'prot', titulo: 'PROT', alinhamento: 'centro' },
    { chave: 'total', titulo: 'Total publicados', largura: '16%', alinhamento: 'centro' },
    { chave: 'caducados', titulo: 'Caducados', largura: '12%', alinhamento: 'centro' },
    { chave: 'ultimaPublicacao', titulo: 'Últ. publicação', largura: '16%', alinhamento: 'centro' },
  ];

  ngOnInit(): void {
    // O «Atualizar» reinicia a contagem; um pedido em curso não deixa sair outro.
    this.reinicio
      .pipe(
        startWith(undefined),
        switchMap(() => timer(0, PERIODO_ATUALIZACAO_MS)),
        exhaustMap(() => this.pedirKpis()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
    this.carregarEstatisticas();
  }

  atualizar(): void {
    this.reinicio.next();
  }

  /** Visível nas preferências gravadas, mas ausente da resposta dos KPIs. */
  semCalculo(linha: PreferenciaKpi): boolean {
    return (
      linha.visivel && this.kpis() !== null && this.visiveisNoServidor().has(linha.kpiCodigo) && !this.codigosCalculados().has(linha.kpiCodigo)
    );
  }

  abrirPainel(): void {
    this.painelAberto.set(true);
    this.erroPainel.set(null);
    this.anuncioPainel.set('');
    this.mensagemSucesso.set(null);
    this.linhasPainel.set([]);
    this.aCarregarPainel.set(true);
    this.dashboardService.preferencias().subscribe({
      next: (lista) => {
        this.aplicarPreferencias(lista);
        this.aCarregarPainel.set(false);
      },
      error: (erro: ApiError) => {
        this.erroPainel.set(erro);
        this.aCarregarPainel.set(false);
      },
    });
  }

  cancelar(): void {
    if (this.gravando()) {
      return;
    }
    this.fecharPainel();
  }

  alternarVisivel(indice: number, visivel: boolean): void {
    this.linhasPainel.update((linhas) => linhas.map((linha, i) => (i === indice ? { ...linha, visivel } : linha)));
  }

  mover(indice: number, sentido: -1 | 1): void {
    const destino = indice + sentido;
    const linhas = [...this.linhasPainel()];
    if (destino < 0 || destino >= linhas.length) {
      return;
    }
    [linhas[indice], linhas[destino]] = [linhas[destino], linhas[indice]];
    this.linhasPainel.set(linhas);
    const movida = linhas[destino];
    this.anuncioPainel.set(`${movida.designacao} passou para a posição ${destino + 1}.`);
    // A linha muda de sítio no DOM e perde o foco; nas pontas o botão usado fica desativado e o foco passa ao outro.
    const preferido = `${sentido === -1 ? 'subir' : 'descer'}-${movida.kpiCodigo}`;
    const alternativo = `${sentido === -1 ? 'descer' : 'subir'}-${movida.kpiCodigo}`;
    afterNextRender(
      () => {
        const botao = this.botaoPainel(preferido);
        (botao && !botao.disabled ? botao : this.botaoPainel(alternativo))?.focus();
      },
      { injector: this.injector },
    );
  }

  gravar(): void {
    const pedido = this.linhasPainel().map((linha, ordem) => ({ kpiCodigo: linha.kpiCodigo, visivel: linha.visivel, ordem }));
    this.gravando.set(true);
    this.erroPainel.set(null);
    this.dashboardService.gravarPreferencias(pedido).subscribe({
      next: () => {
        this.gravando.set(false);
        this.fecharPainel();
        this.mensagemSucesso.set('Preferências gravadas.');
        this.atualizar();
      },
      error: (erro: ApiError) => {
        this.gravando.set(false);
        this.erroPainel.set(erro);
      },
    });
  }

  /** O servidor apaga as preferências e devolve a configuração por omissão, que a grelha também passa a mostrar. */
  reporPredefinicao(): void {
    this.gravando.set(true);
    this.erroPainel.set(null);
    this.dashboardService.gravarPreferencias([]).subscribe({
      next: (lista) => {
        this.gravando.set(false);
        this.aplicarPreferencias(lista);
        this.anuncioPainel.set('Predefinição reposta.');
        this.atualizar();
      },
      error: (erro: ApiError) => {
        this.gravando.set(false);
        this.erroPainel.set(erro);
      },
    });
  }

  tempoRelativo(criadoEm: string): string {
    const diffMs = Date.now() - new Date(criadoEm).getTime();
    const diffMinutos = Math.floor(diffMs / 60000);
    if (diffMinutos < 60) {
      return `Há ${Math.max(diffMinutos, 1)}min`;
    }
    const diffHoras = Math.floor(diffMinutos / 60);
    if (diffHoras < 24) {
      return `Há ${diffHoras}h`;
    }
    const diffDias = Math.floor(diffHoras / 24);
    if (diffDias === 1) {
      return 'Ontem';
    }
    return `Há ${diffDias} dias`;
  }

  private pedirKpis(): Observable<ValorKpi[]> {
    this.aCarregar.set(true);
    return this.dashboardService.kpis().pipe(
      tap({
        next: (kpis) => {
          this.kpis.set(kpis);
          this.erroKpis.set(null);
        },
        error: (erro: ApiError) => {
          this.erroKpis.set(erro);
          if (CODIGOS_SEM_PERMISSAO.has(erro.codigo)) {
            this.kpis.set(null);
          }
        },
      }),
      catchError(() => EMPTY),
      finalize(() => this.aCarregar.set(false)),
    );
  }

  private aplicarPreferencias(lista: PreferenciaKpi[]): void {
    this.linhasPainel.set([...lista].sort((a, b) => a.ordem - b.ordem));
    this.visiveisNoServidor.set(new Set(lista.filter((linha) => linha.visivel).map((linha) => linha.kpiCodigo)));
  }

  private fecharPainel(): void {
    this.painelAberto.set(false);
    afterNextRender(() => this.botaoPersonalizar()?.nativeElement.focus(), { injector: this.injector });
  }

  private botaoPainel(id: string): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>(`#${id}`);
  }

  private carregarEstatisticas(): void {
    this.adminService.estatisticas().subscribe({
      next: (dados) => this.estatisticas.set(dados),
      error: (erro: ApiError) => this.erroEstatisticas.set(erro),
    });
  }
}
