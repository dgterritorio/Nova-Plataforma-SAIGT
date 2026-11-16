import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription, catchError, of } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { EstruturaDocumental, ResultadoVerificacao, VerificacaoChecklist, VerificacaoDocumental } from '../../../models/documento.model';
import { DescargaService } from '../../../services/descarga.service';
import { DocumentoService } from '../../../services/documento.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { SeloComponent, TomSelo } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';

/** Pela ordem do FR-092, a mesma da resposta da API. */
export const ROTULO_VERIFICACAO: Record<VerificacaoChecklist, string> = {
  CORRESPONDENCIA_ATO_TEXTO: 'Correspondência entre o ato e o texto do diploma',
  FORMATO_QUADRO_ANEXO: 'Formato do quadro anexo',
  GEORREFERENCIACAO_MATRICIAL: 'Georreferenciação matricial',
  CONSISTENCIA_VETORIAL_MATRICIAL: 'Consistência entre informação vetorial e matricial',
  FOLHA_NOME_TIF: 'N.º de folha no nome do ficheiro TIF',
  CORRECAO_ZIP: 'Correção do ZIP',
};

const ORDEM_VERIFICACOES = Object.keys(ROTULO_VERIFICACAO) as VerificacaoChecklist[];

export const SELO_RESULTADO: Record<ResultadoVerificacao, { rotulo: string; tom: TomSelo }> = {
  OK: { rotulo: 'Conforme', tom: 'sucesso' },
  FALHA: { rotulo: 'Não conforme', tom: 'erro' },
  MANUAL: { rotulo: 'Verificação manual', tom: 'aviso' },
};

export interface LinhaChecklist extends VerificacaoDocumental {
  rotuloResultado: string;
  tomResultado: TomSelo;
  justificacaoDesbloqueio: string | null;
}

export interface GrupoChecklist {
  verificacao: VerificacaoChecklist;
  titulo: string;
  linhas: LinhaChecklist[];
}

function justificacoesPorDocumento(estrutura: EstruturaDocumental | null): Map<string, string> {
  const mapa = new Map<string, string>();
  if (!estrutura) {
    return mapa;
  }
  const itens = estrutura.separadores.flatMap((separador) => [...separador.publicacao, ...separador.deposito]);
  for (const ficheiro of [...itens.flatMap((item) => item.ficheiros), ...estrutura.outros]) {
    if (ficheiro.justificacaoDesbloqueio) {
      mapa.set(ficheiro.id, ficheiro.justificacaoDesbloqueio);
    }
  }
  return mapa;
}

/**
 * Checklist de validação DGT, só de leitura: as verificações manuais não se marcam nem ficam registadas.
 * A justificação de desbloqueio vem da estrutura documental; se esta falhar, a checklist mostra-se sem ela.
 */
@Component({
  selector: 'app-checklist-validacao',
  imports: [AlertaComponent, BotaoComponent, CartaoComponent, CelulaTabelaDirective, ErrorBannerComponent, SeloComponent, TabelaComponent],
  templateUrl: './checklist-validacao.component.html',
  styleUrl: './checklist-validacao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChecklistValidacaoComponent {
  private readonly documentoService = inject(DocumentoService);
  protected readonly descargaService = inject(DescargaService);

  readonly processoId = input.required<string>();

  readonly colunas: ColunaTabela[] = [
    { chave: 'ficheiro', titulo: 'Ficheiro', largura: '30%' },
    { chave: 'resultado', titulo: 'Resultado', largura: '18%' },
    { chave: 'detalhe', titulo: 'Detalhe' },
    { chave: 'acoes', titulo: 'Ações', largura: '16%' },
  ];

  readonly linhas = signal<VerificacaoDocumental[]>([]);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly emDownload = this.descargaService.emCurso;
  private readonly justificacoes = signal(new Map<string, string>());
  /** O erro de descarga do serviço é partilhado: só se mostra aqui se o pedido saiu desta checklist. */
  private readonly descargaPropria = signal(false);

  readonly erroDescarga = computed(() => (this.descargaPropria() ? this.descargaService.erro() : null));

  readonly grupos = computed<GrupoChecklist[]>(() => {
    const justificacoes = this.justificacoes();
    return ORDEM_VERIFICACOES.map((verificacao) => ({
      verificacao,
      titulo: ROTULO_VERIFICACAO[verificacao],
      linhas: this.linhas()
        .filter((linha) => linha.verificacao === verificacao)
        .map((linha) => ({
          ...linha,
          rotuloResultado: SELO_RESULTADO[linha.resultado].rotulo,
          tomResultado: SELO_RESULTADO[linha.resultado].tom,
          justificacaoDesbloqueio: linha.documentoId ? (justificacoes.get(linha.documentoId) ?? null) : null,
        })),
    })).filter((grupo) => grupo.linhas.length > 0);
  });

  readonly resumo = computed(() => {
    const contar = (resultado: ResultadoVerificacao) => this.linhas().filter((linha) => linha.resultado === resultado).length;
    return `${contar('OK')} conformes · ${contar('FALHA')} não conformes · ${contar('MANUAL')} a verificar manualmente`;
  });

  private pedidos = new Subscription();

  constructor() {
    effect(() => {
      const processoId = this.processoId();
      untracked(() => this.carregar(processoId));
    });
    inject(DestroyRef).onDestroy(() => this.pedidos.unsubscribe());
  }

  atualizar(): void {
    this.carregar(this.processoId());
  }

  descarregar(documentoId: string): void {
    this.descargaPropria.set(true);
    this.descargaService.descarregar(this.processoId(), documentoId);
  }

  idGrupo(verificacao: VerificacaoChecklist): string {
    return `checklist-${verificacao.toLowerCase()}`;
  }

  private carregar(processoId: string): void {
    this.pedidos.unsubscribe();
    this.pedidos = new Subscription();
    this.aCarregar.set(true);
    this.erro.set(null);
    this.pedidos.add(
      this.documentoService.verificacoes(processoId).subscribe({
        next: (linhas) => {
          this.linhas.set(linhas);
          this.aCarregar.set(false);
        },
        error: (erro: ApiError) => {
          this.linhas.set([]);
          this.erro.set(erro);
          this.aCarregar.set(false);
        },
      }),
    );
    this.pedidos.add(
      this.documentoService
        .estrutura(processoId)
        .pipe(catchError(() => of(null)))
        .subscribe((estrutura) => this.justificacoes.set(justificacoesPorDocumento(estrutura))),
    );
  }
}
