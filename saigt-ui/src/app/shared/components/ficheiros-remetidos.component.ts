import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { EMPTY, expand, reduce } from 'rxjs';
import { ApiError } from '../../core/models/error-response.model';
import { DocumentoProcesso } from '../../models/documento.model';
import { DocumentoService } from '../../services/documento.service';
import { CartaoComponent } from '../ui/cartao/cartao.component';
import { SeloComponent } from '../ui/selo/selo.component';
import { CelulaTabelaDirective } from '../ui/tabela/celula-tabela.directive';
import { ColunaTabela, TabelaComponent } from '../ui/tabela/tabela.component';
import { ErrorBannerComponent } from './error-banner.component';

export function textoTotalFicheiros(total: number): string {
  return total === 1 ? '1 ficheiro' : `${total} ficheiros`;
}

const TAMANHO_PAGINA = 100;

// O contrato tem `SUBSTITUIDO` em `EstadoValidacao`; o tipo do cliente só tem as decisões da DGT.
const SUBSTITUIDO = 'SUBSTITUIDO' as string;

const COLUNAS: ColunaTabela[] = [
  { chave: 'nomeFicheiro', titulo: 'Nome do ficheiro', largura: '52%' },
  { chave: 'extensao', titulo: 'Tipo', largura: '10%' },
  { chave: 'criadoEm', titulo: 'Data de envio', largura: '22%' },
  { chave: 'estado', titulo: 'Estado', largura: '16%' },
];

/** Ficheiros do processo em só leitura, usado no Resumo, no detalhe e no recibo. */
@Component({
  selector: 'app-ficheiros-remetidos',
  imports: [CartaoComponent, CelulaTabelaDirective, DatePipe, ErrorBannerComponent, SeloComponent, TabelaComponent],
  templateUrl: './ficheiros-remetidos.component.html',
  styleUrl: './ficheiros-remetidos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FicheirosRemetidosComponent implements OnInit {
  private readonly documentoService = inject(DocumentoService);

  readonly processoId = input.required<string>();
  readonly carregados = output<DocumentoProcesso[]>();

  readonly colunas = COLUNAS;
  readonly documentos = signal<DocumentoProcesso[] | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);

  readonly total = signal(0);
  readonly textoTotal = computed(() => textoTotalFicheiros(this.total()));

  ngOnInit(): void {
    const pedirPagina = (page: number) => this.documentoService.listar(this.processoId(), {}, page, TAMANHO_PAGINA);

    // A tabela mostra todos os ficheiros: só se preenche com as páginas todas, para um erro não deixar uma lista parcial.
    pedirPagina(0)
      .pipe(
        expand((pagina) => (pagina.page + 1 < pagina.totalPages ? pedirPagina(pagina.page + 1) : EMPTY)),
        reduce((acumulado, pagina) => [...acumulado, ...pagina.content], [] as DocumentoProcesso[]),
      )
      .subscribe({
        next: (todos) => {
          // Um ficheiro recusado e substituído na correção não segue na submissão; fica só nas Recusas do detalhe.
          const lista = todos.filter((documento) => documento.estadoValidacao !== SUBSTITUIDO);
          this.documentos.set(lista);
          this.total.set(lista.length);
          this.aCarregar.set(false);
          this.carregados.emit(lista);
        },
        error: (erro: ApiError) => {
          this.erro.set(erro);
          this.aCarregar.set(false);
        },
      });
  }
}
