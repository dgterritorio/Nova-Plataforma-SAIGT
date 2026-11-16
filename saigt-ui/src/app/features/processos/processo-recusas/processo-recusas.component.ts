import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { ItemRecusa, OrigemRecusa, Recusa } from '../../../models/processo.model';
import { DescargaService } from '../../../services/descarga.service';
import { ProcessoService } from '../../../services/processo.service';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { SeloComponent, TomSelo } from '../../../shared/ui/selo/selo.component';
import { Separador, SeparadoresComponent } from '../../../shared/ui/separadores/separadores.component';
import { CelulaTabelaDirective } from '../../../shared/ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../../../shared/ui/tabela/tabela.component';
import { ROTULO_POR_ELEMENTO } from '../processo-documentos/processo-documentos.component';

const SEPARADOR_OUTROS = 'Outros';

export const TOM_POR_ORIGEM: Record<OrigemRecusa, TomSelo> = { DGT: 'info', INCM: 'aviso' };

export interface LinhaRecusa {
  separador: string;
  documentoId: string | null;
  nomeFicheiro: string | null;
  categoria: string | null;
  motivo: string | null;
  notas: string;
}

function linha(item: ItemRecusa): LinhaRecusa {
  const comum = { categoria: item.categoria?.descricao ?? null, motivo: item.motivo?.descricao ?? null, notas: item.notas };
  if (item.tipo === 'DOCUMENTO') {
    return { ...comum, separador: item.separador ?? SEPARADOR_OUTROS, documentoId: item.documentoId, nomeFicheiro: item.nomeFicheiro };
  }
  const elemento = item.elemento ? ROTULO_POR_ELEMENTO[item.elemento] : '—';
  return { ...comum, separador: `Informação Base — ${elemento}`, documentoId: null, nomeFicheiro: null };
}

function idSeparador(recusa: Recusa): string {
  return `recusa-${recusa.sequencia}`;
}

@Component({
  selector: 'app-processo-recusas',
  imports: [
    AlertaComponent,
    BotaoComponent,
    CartaoComponent,
    CelulaTabelaDirective,
    DatePipe,
    SeloComponent,
    SeparadoresComponent,
    TabelaComponent,
  ],
  templateUrl: './processo-recusas.component.html',
  styleUrl: './processo-recusas.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessoRecusasComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  protected readonly descargaService = inject(DescargaService);

  readonly processoId = input.required<string>();

  readonly colunas: ColunaTabela[] = [
    { chave: 'separador', titulo: 'Separador' },
    { chave: 'nomeFicheiro', titulo: 'Nome do Ficheiro' },
    { chave: 'categoria', titulo: 'Categoria' },
    { chave: 'motivo', titulo: 'Motivo' },
    { chave: 'notas', titulo: 'Notas' },
  ];
  readonly tomPorOrigem = TOM_POR_ORIGEM;

  readonly aCarregar = signal(true);
  readonly erro = signal(false);
  readonly recusas = signal<Recusa[]>([]);
  readonly ativo = signal('');

  protected readonly separadoresVista = viewChild(SeparadoresComponent);

  readonly variasRecusas = computed(() => this.recusas().length > 1);

  readonly separadores = computed<Separador[]>(() =>
    this.recusas().map((recusa) => ({ id: idSeparador(recusa), etiqueta: `Recusa ${recusa.sequencia} · ${recusa.origem}` })),
  );

  readonly recusaAtiva = computed(() => this.recusas().find((recusa) => idSeparador(recusa) === this.ativo()) ?? null);

  readonly linhas = computed(() => this.recusaAtiva()?.itens.map(linha) ?? []);

  readonly textoElementos = computed(() => {
    const total = this.linhas().length;
    return total === 1 ? '1 elemento' : `${total} elementos`;
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.aCarregar.set(true);
    this.erro.set(false);
    this.processoService.estatisticas(this.processoId()).subscribe({
      next: (estatisticas) => {
        const recusas = estatisticas.recusas;
        this.recusas.set(recusas);
        this.ativo.set(recusas.length > 0 ? idSeparador(recusas[recusas.length - 1]) : '');
        this.aCarregar.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.aCarregar.set(false);
      },
    });
  }

  descarregar(documentoId: string): void {
    this.descargaService.descarregar(this.processoId(), documentoId);
  }
}
