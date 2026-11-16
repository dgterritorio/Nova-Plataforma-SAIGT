import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../shared/components/error-banner.component';
import { EstadoProcessoBadgeComponent } from '../../shared/components/estado-processo-badge.component';
import { BotaoComponent } from '../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../shared/ui/cartao/cartao.component';
import { SeloComponent } from '../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../shared/ui/tabela/tabela.component';
import { AuthService } from '../../core/auth/auth.service';
import { ehUtilizadorDgt } from '../../core/auth/perfis-backoffice';
import { ApiError } from '../../core/models/error-response.model';
import { GrupoPainel, GrupoProcesso, PainelProcessos, ProcessoListagem, devolvidoPelaPcm } from '../../models/processo.model';
import { ProcessoService } from '../../services/processo.service';
import { ROUTES } from '../../shared/routes';

const TAMANHO_PREVISUALIZACAO = 5;

type TomIndicador = 'sucesso' | 'info' | 'erro' | 'aviso';

/** Na entidade todas as ações são azul-escuro; na DGT, verde e âmbar escuros (Figma UI-001 e UI-032). */
type TomAcao = 'primaria' | 'sucesso' | 'aviso';

interface GrupoMeta {
  rotulo: string;
  acao: string;
  /** Na DGT a ação do grupo é outra: a fila é para validar, não para consultar. */
  acaoDgt?: string;
  tom: TomIndicador;
  icone: string;
  tomAcaoDgt?: TomAcao;
  /** Ecrã próprio do grupo; sem ele, a listagem geral filtrada por `grupo`. */
  rotaEntidade?: string;
}

interface IndicadorPainel {
  grupo: GrupoProcesso;
  valor: number;
  rotulo: string;
  acao: string;
  tom: TomIndicador;
  icone: string;
  tomAcao: TomAcao;
  rota: string;
  queryParams: { grupo: GrupoProcesso } | null;
}

const ICONES = 'icones/painel/';

/** Rótulos, ícones e ações dos cartões do Figma (UI-001 e UI-032), um por grupo da API. */
const META_GRUPOS: Record<GrupoProcesso, GrupoMeta> = {
  PUBLICADOS: {
    rotulo: 'Publicados',
    acao: 'Consultar Processo',
    tom: 'sucesso',
    icone: `${ICONES}publicados.svg`,
    tomAcaoDgt: 'sucesso',
  },
  EM_CURSO: {
    rotulo: 'Em Formação',
    acao: 'Continuar Processo',
    tom: 'info',
    icone: `${ICONES}em-formacao.svg`,
    rotaEntidade: ROUTES.PROCESSOS_CONTINUAR,
  },
  PARA_CORRECAO: {
    rotulo: 'Para Correção',
    acao: 'Corrigir Processo',
    tom: 'erro',
    icone: `${ICONES}para-correcao.svg`,
    rotaEntidade: ROUTES.PROCESSOS_CORRIGIR,
  },
  EM_VALIDACAO: {
    rotulo: 'Em Validação SAIGT e INCM',
    acao: 'Consultar Processo',
    acaoDgt: 'Validar Processo',
    tom: 'aviso',
    icone: `${ICONES}em-validacao.svg`,
    tomAcaoDgt: 'aviso',
  },
};

/** Pela ordem dos cartões, igual à dos grupos que a API devolve a cada tipo de utilizador. */
const GRUPOS_ENTIDADE: GrupoProcesso[] = ['PUBLICADOS', 'EM_CURSO', 'PARA_CORRECAO', 'EM_VALIDACAO'];

const GRUPOS_DGT: GrupoProcesso[] = ['PUBLICADOS', 'EM_VALIDACAO'];

interface ConfigPainelProcessos {
  grupo: GrupoProcesso;
  rotaVerTodos: string;
  queryParamsVerTodos: { grupo: GrupoProcesso } | null;
  titulo: string;
  icone: string;
  mensagemVazio: string;
}

/** Não-backoffice: processos recusados da própria entidade (REJEITADO_DGT e ANULADO_INCM, BL-PROC-050). */
const CONFIG_RECUSADOS: ConfigPainelProcessos = {
  grupo: 'PARA_CORRECAO',
  rotaVerTodos: ROUTES.PROCESSOS_CORRIGIR,
  queryParamsVerTodos: null,
  titulo: 'Recusados',
  icone: `${ICONES}recusados.svg`,
  mensagemVazio: 'Sem processos recusados.',
};

/** Backoffice: a fila da DGT (por capturar, em validação e por enviar). */
const CONFIG_EM_VALIDACAO: ConfigPainelProcessos = {
  grupo: 'EM_VALIDACAO',
  rotaVerTodos: ROUTES.PROCESSOS,
  queryParamsVerTodos: { grupo: 'EM_VALIDACAO' },
  titulo: 'Em validação',
  icone: `${ICONES}em-validacao.svg`,
  mensagemVazio: 'Sem processos em validação.',
};

/** "Nome" é a designação do Instrumento; numa Dinâmica sem IGT a API já a preenche com o descritivo da Dinâmica. */
const COLUNAS: ColunaTabela[] = [
  { chave: 'numeroProcesso', titulo: 'Referência', largura: '14%' },
  { chave: 'tipoInstrumento', titulo: 'Tipo', largura: '12%' },
  { chave: 'nomeProcedimento', titulo: 'Fase', largura: '20%' },
  { chave: 'designacaoInstrumento', titulo: 'Nome', largura: '15%' },
  { chave: 'entidadeNome', titulo: 'Entidade', largura: '22%' },
  { chave: 'estado', titulo: 'Estado', largura: '17%' },
];

/**
 * Painel Principal (`/painel`), ecrã de aterragem após login.
 *
 * Cartões e lista numa só chamada a `GET /processos/painel`, nunca simulados com várias chamadas
 * a `GET /processos` (regra de frequência). O âmbito por entidade, por utilizador e por grupo de
 * estados é resolvido no backend (`GrupoProcesso`), o frontend não filtra nada aqui. A DGT vê só
 * 2 cartões, sem "Criar Processo" (não é autor de processos).
 * A tabela mostra "Recusados" ou a fila "Em validação" da DGT consoante o perfil. A fila da DGT já vem sem os
 * processos que estão com a PCM; os devolvidos pela PCM levam o selo.
 */
@Component({
  selector: 'app-painel',
  imports: [
    AppShellComponent,
    ErrorBannerComponent,
    EstadoProcessoBadgeComponent,
    RouterLink,
    BotaoComponent,
    CartaoComponent,
    SeloComponent,
    TabelaComponent,
    CelulaTabelaDirective,
  ],
  templateUrl: './painel.component.html',
  styleUrl: './painel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PainelComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  readonly authService = inject(AuthService);
  readonly rotas = ROUTES;
  readonly colunas = COLUNAS;
  readonly iconeSeta = `${ICONES}seta-diagonal.svg`;
  readonly devolvidoPelaPcm = devolvidoPelaPcm;

  /** Qualquer perfil interno, também o `VISITANTE`: o painel da DGT não tem "Criar Processo". */
  readonly souDgt = computed(() => ehUtilizadorDgt(this.authService.perfilInterno()));

  /** `null` até `GET /processos/painel` responder, ou se falhar: os cartões ficam a 0. */
  private readonly painel = signal<PainelProcessos | null>(null);

  readonly cartoes = computed<IndicadorPainel[]>(() => {
    const dgt = this.souDgt();
    return (dgt ? GRUPOS_DGT : GRUPOS_ENTIDADE).map((grupo) => {
      const meta = META_GRUPOS[grupo];
      return {
        grupo,
        valor: this.grupoDoPainel(grupo)?.contagem ?? 0,
        rotulo: meta.rotulo,
        acao: dgt ? (meta.acaoDgt ?? meta.acao) : meta.acao,
        tom: meta.tom,
        icone: meta.icone,
        tomAcao: dgt ? (meta.tomAcaoDgt ?? 'primaria') : 'primaria',
        rota: (!dgt && meta.rotaEntidade) || ROUTES.PROCESSOS,
        queryParams: !dgt && meta.rotaEntidade ? null : { grupo },
      };
    });
  });

  readonly painelConfig = computed(() => (this.souDgt() ? CONFIG_EM_VALIDACAO : CONFIG_RECUSADOS));

  readonly processosLista = computed<ProcessoListagem[]>(() => this.grupoDoPainel(this.painelConfig().grupo)?.processos ?? []);
  readonly totalLista = computed(() => this.grupoDoPainel(this.painelConfig().grupo)?.contagem ?? 0);
  readonly loading = signal(true);
  readonly erro = signal<ApiError | null>(null);

  ngOnInit(): void {
    this.processoService.painel(TAMANHO_PREVISUALIZACAO).subscribe({
      next: (painel) => {
        this.painel.set(painel);
        this.loading.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.loading.set(false);
      },
    });
  }

  chevron(tom: TomAcao): string {
    return `${ICONES}chevron-${tom}.svg`;
  }

  saudacao(): string {
    const hora = new Date().getHours();
    let periodo = 'Boa noite';
    if (hora < 12) {
      periodo = 'Bom dia';
    } else if (hora < 20) {
      periodo = 'Boa tarde';
    }
    const primeiroNome = this.authService.nome()?.split(' ')[0] ?? '';
    return primeiroNome ? `${periodo}, ${primeiroNome}` : periodo;
  }

  subtitulo(): string {
    if (this.authService.origem() === 'AD_DGT') {
      return 'DGT — Direção-Geral do Território';
    }
    return this.authService.entidade()?.nome ?? '';
  }

  private grupoDoPainel(grupo: GrupoProcesso): GrupoPainel | undefined {
    return this.painel()?.grupos.find((candidato) => candidato.grupo === grupo);
  }
}
