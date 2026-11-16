import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { ApiError } from '../../core/models/error-response.model';
import { CategoriaDocumento, DocumentoProcesso, ProcessoHistoricoDocumental } from '../../models/documento.model';
import { EstadoValidacao } from '../../models/processo.model';
import { DescargaService } from '../../services/descarga.service';
import { DocumentoService } from '../../services/documento.service';
import { linkDrSeguro } from '../link-dr';
import { BotaoComponent } from '../ui/botao/botao.component';
import { CartaoComponent } from '../ui/cartao/cartao.component';
import { SeloComponent, TomSelo } from '../ui/selo/selo.component';
import { CelulaTabelaDirective } from '../ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../ui/tabela/tabela.component';
import { ErrorBannerComponent } from './error-banner.component';
import { EstadoProcessoBadgeComponent } from './estado-processo-badge.component';
import { ROTULO_CAIXA, VALIDACAO } from './ficheiros-processo.component';

const COLUNAS: ColunaTabela[] = [
  { chave: 'nomeFicheiro', titulo: 'Nome do ficheiro', largura: '34%' },
  { chave: 'extensao', titulo: 'Tipo', largura: '8%' },
  { chave: 'categoria', titulo: 'Caixa', largura: '12%' },
  { chave: 'criadoEm', titulo: 'Data de envio', largura: '19%' },
  { chave: 'estadoValidacao', titulo: 'Estado de validação', largura: '15%' },
  { chave: 'acao', titulo: 'Ações', largura: '12%' },
];

const MENSAGEM_ERRO = 'Não foi possível carregar o histórico documental. Tente novamente.';

/** Sem resposta do servidor o interceptor só tem o texto técnico do Angular; o do backend já vem legível. */
function comMensagemLegivel(erro: ApiError): ApiError {
  return erro.codigo === 'ERRO_DESCONHECIDO' ? new ApiError(erro.codigo, MENSAGEM_ERRO, erro.traceId, erro.campos) : erro;
}

/**
 * Ficheiros de todas as submissões do mesmo Procedimento, para o detalhe do processo. Só aparece quando há mais
 * de uma: com uma só, a lista seria a dos ficheiros remetidos.
 */
@Component({
  selector: 'app-historico-documental',
  imports: [
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    DatePipe,
    ErrorBannerComponent,
    EstadoProcessoBadgeComponent,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './historico-documental.component.html',
  styleUrls: ['./ficheiros-remetidos.component.css', './historico-documental.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoricoDocumentalComponent implements OnInit {
  private readonly documentoService = inject(DocumentoService);
  protected readonly descargaService = inject(DescargaService);

  readonly processoId = input.required<string>();

  readonly colunas = COLUNAS;
  readonly processos = signal<ProcessoHistoricoDocumental[]>([]);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  /** O processo atual começa fechado: os ficheiros dele já estão em «Ficheiros remetidos». */
  private readonly abertos = signal<ReadonlySet<string>>(new Set());

  readonly visivel = computed(() => this.aCarregar() || this.erro() !== null || this.processos().length > 1);

  ngOnInit(): void {
    // O serviço é partilhado: sem isto, o erro de uma descarga falhada noutro processo aparecia aqui.
    this.descargaService.erro.set(null);
    this.carregar();
  }

  carregar(): void {
    this.aCarregar.set(true);
    this.erro.set(null);
    this.documentoService.historico(this.processoId()).subscribe({
      next: (historico) => {
        this.processos.set(historico.processos);
        this.abertos.set(new Set(historico.processos.filter((processo) => !processo.atual).map((p) => p.processoId)));
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(comMensagemLegivel(erro));
        this.aCarregar.set(false);
      },
    });
  }

  aberto(processo: ProcessoHistoricoDocumental): boolean {
    return this.abertos().has(processo.processoId);
  }

  alternar(processo: ProcessoHistoricoDocumental): void {
    this.abertos.update((atuais) => {
      const novos = new Set(atuais);
      if (!novos.delete(processo.processoId)) {
        novos.add(processo.processoId);
      }
      return novos;
    });
  }

  descarregar(processo: ProcessoHistoricoDocumental, documento: DocumentoProcesso): void {
    this.descargaService.descarregar(processo.processoId, documento.id);
  }

  linkDr(processo: ProcessoHistoricoDocumental): string | null {
    return linkDrSeguro(processo.linkDr);
  }

  rotuloCaixa(categoria: CategoriaDocumento): string {
    return ROTULO_CAIXA[categoria];
  }

  validacao(estado: EstadoValidacao): { rotulo: string; tom: TomSelo } {
    return VALIDACAO[estado];
  }
}
