import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental } from '../../../models/documento.model';
import { ListaValoresItem } from '../../../models/lista-valores.model';
import { Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { DocumentoService } from '../../../services/documento.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoService } from '../../../services/processo.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { textoTotalFicheiros } from '../../../shared/components/ficheiros-remetidos.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { ROTULO_POR_ELEMENTO } from '../processo-documentos/processo-documentos.component';

const TIPO_CATEGORIA = 'CATEGORIA_REJEICAO_DGT';
const TIPO_MOTIVO = 'MOTIVO_REJEICAO_DGT';
const CODIGOS_DE_CONFLITO = ['VERSAO_DESATUALIZADA', 'ESTADO_INVALIDO'];
const SEPARADOR_OUTROS = 'Outros';
const SEPARADOR_PLANTAS = 'Plantas';

export interface LinhaRejeicao {
  itemId: string;
  separador: string;
  nomeFicheiro: string | null;
  categoria: string | null;
  motivo: string | null;
  notas: string;
}

interface FicheiroComSeparador {
  ficheiro: DocumentoProcesso;
  separador: string;
}

function ficheirosPorId(estrutura: EstruturaDocumental): Map<string, FicheiroComSeparador> {
  const mapa = new Map<string, FicheiroComSeparador>();
  for (const separador of estrutura.separadores) {
    for (const item of [...separador.publicacao, ...separador.deposito]) {
      item.ficheiros.forEach((ficheiro) => mapa.set(ficheiro.id, { ficheiro, separador: separador.separador }));
    }
  }
  estrutura.outros.forEach((ficheiro) => mapa.set(ficheiro.id, { ficheiro, separador: SEPARADOR_OUTROS }));
  return mapa;
}

function descricoes(valores: ListaValoresItem[]): Map<number, string> {
  return new Map(valores.map((valor) => [valor.id, valor.descricao]));
}

@Component({
  selector: 'app-resumo-rejeicao',
  imports: [
    BotaoComponent,
    CampoTextoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    DatePipe,
    ErrorBannerComponent,
    ModalComponent,
    ReactiveFormsModule,
    SeloComponent,
    TabelaComponent,
  ],
  templateUrl: './resumo-rejeicao.component.html',
  styleUrl: './resumo-rejeicao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumoRejeicaoComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  private readonly documentoService = inject(DocumentoService);
  private readonly listaValoresService = inject(ListaValoresService);
  private readonly plantasService = inject(PlantasService);

  readonly processo = input.required<ProcessoDetalhe>();

  /** Volta à vista de validação sem gravar nada. */
  readonly anterior = output<void>();
  readonly rejeitado = output<Processo>();
  /** Pede ao ecrã o detalhe de novo: as rejeições do quadro vêm dele. */
  readonly recarregar = output<void>();

  private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

  readonly colunas: ColunaTabela[] = [
    { chave: 'separador', titulo: 'Separador' },
    { chave: 'nomeFicheiro', titulo: 'Nome do Ficheiro' },
    { chave: 'categoria', titulo: 'Categoria' },
    { chave: 'motivo', titulo: 'Motivo' },
    { chave: 'notas', titulo: 'Notas' },
  ];

  private readonly ficheiros = signal<Map<string, FicheiroComSeparador> | null>(null);
  /** Os ficheiros da página das Plantas ficam fora da estrutura documental: o nome vem da página, pelo id. */
  private readonly ficheirosPlantas = signal<Map<string, string>>(new Map());
  private readonly categorias = signal<Map<number, string>>(new Map());
  private readonly motivos = signal<Map<number, string>>(new Map());
  readonly aCarregar = signal(true);
  readonly erroCarregamento = signal<ApiError | null>(null);
  readonly notasGerais = signal('');
  readonly controloNotas = new FormControl<string>('', { nonNullable: true });
  readonly recusaAberta = signal(false);
  readonly aConfirmar = signal(false);
  readonly erro = signal<ApiError | null>(null);

  readonly conflito = computed(() => CODIGOS_DE_CONFLITO.includes(this.erro()?.codigo ?? ''));
  readonly podeRecusar = computed(() => !this.aCarregar() && this.erroCarregamento() === null && this.linhas().length > 0);
  readonly podeConfirmar = computed(() => this.podeRecusar() && !this.aConfirmar() && !this.conflito());

  readonly linhas = computed<LinhaRejeicao[]>(() => {
    const ficheiros = this.ficheiros();
    if (!ficheiros) {
      return [];
    }
    const processo = this.processo();
    const categorias = this.categorias();
    const motivos = this.motivos();
    return processo.rejeicoesEmValidacao.map((rejeicao) => {
      const comum = {
        itemId: rejeicao.itemId,
        categoria: rejeicao.categoriaId === null ? null : (categorias.get(rejeicao.categoriaId) ?? null),
        motivo: rejeicao.motivoId === null ? null : (motivos.get(rejeicao.motivoId) ?? null),
        notas: rejeicao.notas,
      };
      if (rejeicao.tipo === 'DOCUMENTO') {
        const ficheiro = ficheiros.get(rejeicao.itemId);
        const daPlanta = ficheiro ? undefined : this.ficheirosPlantas().get(rejeicao.itemId);
        if (daPlanta !== undefined) {
          return { ...comum, separador: SEPARADOR_PLANTAS, nomeFicheiro: daPlanta };
        }
        return { ...comum, separador: ficheiro?.separador ?? '—', nomeFicheiro: ficheiro?.ficheiro.nomeFicheiro ?? null };
      }
      const elemento = processo.informacaoBase.find((e) => e.id === rejeicao.itemId)?.elemento;
      return { ...comum, separador: `Informação Base — ${elemento ? ROTULO_POR_ELEMENTO[elemento] : '—'}`, nomeFicheiro: null };
    });
  });

  readonly textoElementos = computed(() => {
    const total = this.linhas().length;
    return total === 1 ? '1 elemento' : `${total} elementos`;
  });

  readonly concelhos = computed(() => {
    const concelhos = this.processo().territorio?.concelhos ?? [];
    return concelhos.length > 0 ? concelhos.map((concelho) => concelho.nome ?? concelho.codigo).join(', ') : null;
  });

  readonly totalFicheiros = computed(() => {
    const ficheiros = this.ficheiros();
    return ficheiros ? textoTotalFicheiros(ficheiros.size) : null;
  });

  constructor() {
    afterNextRender(() => this.titulo().nativeElement.focus());
    this.controloNotas.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.notasGerais.set(valor));
  }

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.aCarregar.set(true);
    this.erroCarregamento.set(null);
    forkJoin([
      this.documentoService.estrutura(this.processo().id),
      this.listaValoresService.listar(TIPO_CATEGORIA),
      this.listaValoresService.listar(TIPO_MOTIVO),
      this.processo().exigePlantasEMetadados ? this.plantasService.obterPlantas(this.processo().id) : of(null),
    ]).subscribe({
      next: ([estrutura, categorias, motivos, plantas]) => {
        this.ficheiros.set(ficheirosPorId(estrutura));
        this.ficheirosPlantas.set(
          new Map((plantas?.itens ?? []).flatMap((item) => (item.ficheiro ? [[item.ficheiro.id, item.ficheiro.nomeFicheiro] as const] : []))),
        );
        this.categorias.set(descricoes(categorias));
        this.motivos.set(descricoes(motivos));
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erroCarregamento.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  abrirRecusa(): void {
    if (!this.podeRecusar()) {
      return;
    }
    this.erro.set(null);
    this.controloNotas.reset('');
    this.recusaAberta.set(true);
  }

  fecharRecusa(): void {
    if (this.aConfirmar()) {
      return;
    }
    this.erro.set(null);
    this.recusaAberta.set(false);
  }

  confirmar(): void {
    if (!this.podeConfirmar()) {
      return;
    }
    const processo = this.processo();
    const notas = this.notasGerais().trim();
    this.erro.set(null);
    this.aConfirmar.set(true);
    this.controloNotas.disable({ emitEvent: false });
    this.processoService.rejeitar(processo.id, processo.versao, notas === '' ? null : notas).subscribe({
      next: (atualizado) => {
        this.aConfirmar.set(false);
        this.controloNotas.enable({ emitEvent: false });
        this.recusaAberta.set(false);
        this.rejeitado.emit(atualizado);
      },
      error: (erro: ApiError) => {
        this.aConfirmar.set(false);
        this.controloNotas.enable({ emitEvent: false });
        this.erro.set(erro);
        if (erro.codigo === 'VALIDACAO') {
          this.recarregar.emit();
          this.carregar();
        }
      },
    });
  }

  pedirRecarregar(): void {
    this.erro.set(null);
    this.recusaAberta.set(false);
    this.recarregar.emit();
    this.carregar();
  }
}
