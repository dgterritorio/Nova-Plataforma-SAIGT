import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiError } from '../../../core/models/error-response.model';
import { HistoricoProcessoEntrada } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { rotuloEstado } from '../../../shared/components/estado-processo-badge.component';
import { ROUTES } from '../../../shared/routes';

const TAMANHO_PAGINA = 20;

export interface DetalheAlteracao {
  chave: string;
  valor: string;
}

/** Sem mockup: lista cronológica do `GET /processos/{id}/historico`, só com o que a auditoria devolve. */
@Component({
  selector: 'app-processo-historico',
  standalone: true,
  imports: [AppShellComponent, DatePipe, ErrorBannerComponent, RouterLink],
  styleUrls: ['./processo-historico.component.css'],
  template: `
    <app-shell breadcrumb="Processos">
      <div class="conteudo">
        <a class="voltar" [routerLink]="rotaDetalhe()">← Processo</a>
        <h1>Histórico do processo</h1>
        <app-error-banner [erro]="erro()" />

        @if (aCarregar()) {
          <p class="estado-carregamento">A carregar…</p>
        } @else if (entradas().length === 0 && !erro()) {
          <p class="estado-carregamento">Sem registos.</p>
        } @else {
          <ol class="linha-tempo">
            @for (entrada of entradas(); track entrada.id) {
              <li class="entrada">
                <div class="entrada-cabecalho">
                  <span class="acao">{{ rotuloAcao(entrada.acao) }}</span>
                  <time>{{ entrada.criadoEm | date: 'dd/MM/yyyy HH:mm' }}</time>
                </div>
                <div class="autor">{{ entrada.utilizadorNome ?? 'Sistema' }}</div>
                @if (transicao(entrada); as t) {
                  <div class="transicao">{{ t }}</div>
                }
                @if (detalhes(entrada); as lista) {
                  <dl class="detalhes">
                    @for (d of lista; track d.chave) {
                      <div><dt>{{ d.chave }}</dt><dd>{{ d.valor }}</dd></div>
                    }
                  </dl>
                }
              </li>
            }
          </ol>
          @if (temMais()) {
            <button type="button" class="btn-secundario" [disabled]="aCarregarMais()" (click)="carregarMais()">
              {{ aCarregarMais() ? 'A carregar…' : 'Carregar mais' }}
            </button>
          }
        }
      </div>
    </app-shell>
  `,
})
export class ProcessoHistoricoComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  private readonly activatedRoute = inject(ActivatedRoute);

  private readonly processoId = signal('');
  private readonly pagina = signal(0);

  readonly entradas = signal<HistoricoProcessoEntrada[]>([]);
  readonly totalPaginas = signal(0);
  readonly aCarregar = signal(true);
  readonly aCarregarMais = signal(false);
  readonly erro = signal<ApiError | null>(null);

  readonly temMais = computed(() => this.pagina() + 1 < this.totalPaginas());
  readonly rotaDetalhe = computed(() => ROUTES.PROCESSO_DETALHE(this.processoId()));

  ngOnInit(): void {
    this.processoId.set(this.activatedRoute.snapshot.paramMap.get('processoId') ?? '');
    if (!this.processoId()) {
      this.aCarregar.set(false);
      return;
    }
    this.carregar(0);
  }

  carregarMais(): void {
    this.aCarregarMais.set(true);
    this.carregar(this.pagina() + 1);
  }

  /** `Submeter` → "Submeter"; `REGISTO_EXTERNO` → "Registo externo". */
  rotuloAcao(acao: string): string {
    const texto = acao.replaceAll('_', ' ').toLowerCase();
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  transicao(entrada: HistoricoProcessoEntrada): string | null {
    if (!entrada.estadoAntes && !entrada.estadoDepois) {
      return null;
    }
    const antes = entrada.estadoAntes ? rotuloEstado(entrada.estadoAntes) : '—';
    const depois = entrada.estadoDepois ? rotuloEstado(entrada.estadoDepois) : '—';
    return `${antes} → ${depois}`;
  }

  /** `dadosAlteracao` é JSON serializado (motivos, campos alterados); texto que não seja objeto não se mostra. */
  detalhes(entrada: HistoricoProcessoEntrada): DetalheAlteracao[] | null {
    if (!entrada.dadosAlteracao) {
      return null;
    }
    try {
      const dados: unknown = JSON.parse(entrada.dadosAlteracao);
      if (dados === null || typeof dados !== 'object' || Array.isArray(dados)) {
        return null;
      }
      const lista = Object.entries(dados as Record<string, unknown>).map(([chave, valor]) => ({
        chave,
        valor: typeof valor === 'object' && valor !== null ? JSON.stringify(valor) : String(valor ?? '—'),
      }));
      return lista.length > 0 ? lista : null;
    } catch {
      return null;
    }
  }

  private carregar(pagina: number): void {
    this.processoService.historico(this.processoId(), pagina, TAMANHO_PAGINA).subscribe({
      next: (resposta) => {
        this.pagina.set(resposta.page);
        this.totalPaginas.set(resposta.totalPages);
        this.entradas.update((atuais) => [...atuais, ...resposta.content]);
        this.aCarregar.set(false);
        this.aCarregarMais.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
        this.aCarregarMais.set(false);
      },
    });
  }
}
