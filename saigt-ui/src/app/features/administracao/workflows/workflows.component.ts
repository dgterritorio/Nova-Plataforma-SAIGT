import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { ApiError } from '../../../core/models/error-response.model';
import { TipoInstrumento } from '../../../models/instrumento.model';
import { PassoWorkflow, Workflow } from '../../../models/workflow.model';
import { WorkflowService } from '../../../services/workflow.service';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';

const ROTULOS_ATOR: Record<string, string> = { ENTIDADE: 'Entidade', DGT: 'DGT', PCM: 'PCM' };

interface GrupoWorkflows {
  tipoInstrumento: TipoInstrumento;
  workflows: Workflow[];
}

/**
 * Só leitura: os workflows são carregados por seed. Diverge do mockup porque os workflows reais
 * não têm prazos nem um único responsável por passo (mostra os atores de `permissao_acao`).
 * A lista agrupa-se por tipo de instrumento, cada grupo recolhível; abre só o do workflow selecionado.
 */
@Component({
  selector: 'app-workflows',
  standalone: true,
  imports: [AppShellComponent, ErrorBannerComponent, CartaoComponent, SeloComponent],
  styleUrls: ['../backoffice.css', './workflows.component.css'],
  template: `
    <app-shell breadcrumb="Workflows">
      <div class="conteudo">
        <div class="cabecalho">
          <div>
            <h1>Workflows e precedências</h1>
            <p class="subtitulo">Configurar fluxos sem necessidade de desenvolvimento técnico</p>
          </div>
        </div>

        @if (loading()) {
          <p class="estado">A carregar…</p>
        } @else {
          <app-error-banner [erro]="erro()" />

          <div class="grelha-workflows">
            <app-cartao titulo="Workflows disponíveis" espacamento="nenhum" class="painel-lista">
              @for (grupo of grupos(); track grupo.tipoInstrumento.codigo) {
                <div class="grupo-workflows">
                  <button
                    type="button"
                    class="cabecalho-grupo"
                    [attr.aria-expanded]="estaAberto(grupo.tipoInstrumento.codigo)"
                    (click)="alternarGrupo(grupo.tipoInstrumento.codigo)"
                  >
                    <svg
                      class="seta-grupo"
                      [class.aberto]="estaAberto(grupo.tipoInstrumento.codigo)"
                      viewBox="0 0 12 12"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.5"
                      stroke-linecap="round"
                      aria-hidden="true"
                    >
                      <path d="M4.5 3l3 3-3 3" />
                    </svg>
                    <span class="codigo-grupo">{{ grupo.tipoInstrumento.codigo }}</span>
                    <span class="nome-grupo">{{ grupo.tipoInstrumento.nome }}</span>
                    <span class="contagem-grupo">{{ grupo.workflows.length }}</span>
                  </button>
                  @if (estaAberto(grupo.tipoInstrumento.codigo)) {
                    <ul class="lista-workflows">
                      @for (workflow of grupo.workflows; track workflow.id) {
                        <li>
                          <button
                            type="button"
                            class="item-workflow"
                            [class.selecionado]="workflow === selecionado()"
                            [attr.aria-current]="workflow === selecionado() ? 'true' : null"
                            (click)="selecionado.set(workflow)"
                          >
                            <span class="linha-titulo">
                              <span class="titulo-workflow">{{ workflow.titulo }}</span>
                              <app-selo [tom]="workflow.ativo ? 'sucesso' : 'neutro'" tamanho="pequeno" class="badge">
                                {{ workflow.ativo ? 'Ativo' : 'Inativo' }}
                              </app-selo>
                            </span>
                            <span class="resumo-workflow">
                              @if (workflow.numeroPassos === 0) {
                                Sem procedimentos configurados
                              } @else {
                                {{ workflow.numeroPassos }} passos
                              }
                              · {{ workflow.processosAtivos }} processos ativos
                            </span>
                          </button>
                        </li>
                      }
                    </ul>
                  }
                </div>
              } @empty {
                <p class="item-vazio">Sem workflows configurados.</p>
              }
            </app-cartao>

            <app-cartao espacamento="nenhum" class="painel-detalhe">
              @if (selecionado(); as workflow) {
                <div class="cabecalho-detalhe">
                  <h2>{{ workflow.titulo }}</h2>
                </div>
                <ol class="lista-passos">
                  @for (passo of workflow.passos; track passo.ordem) {
                    <li class="passo">
                      <span class="numero-passo" aria-hidden="true">{{ passo.ordem }}</span>
                      <div>
                        <p class="nome-passo">{{ passo.nome }}</p>
                        <p class="detalhe-passo">
                          Atores: {{ rotulosAtores(passo) }}
                          @if (passo.prazoDias !== null) {
                            · Prazo: {{ passo.prazoDias }}d
                          }
                        </p>
                      </div>
                    </li>
                  } @empty {
                    <li class="passo passo-vazio">Sem procedimentos configurados.</li>
                  }
                </ol>
              }
            </app-cartao>
          </div>
        }
      </div>
    </app-shell>
  `
})
export class WorkflowsComponent implements OnInit {
  private readonly workflowService = inject(WorkflowService);

  readonly workflows = signal<Workflow[]>([]);
  readonly selecionado = signal<Workflow | null>(null);
  readonly loading = signal(true);
  readonly erro = signal<ApiError | null>(null);
  private readonly gruposAbertos = signal<ReadonlySet<string>>(new Set());

  /** Mantém a ordem da API, que já vem agrupada por tipo de instrumento. */
  readonly grupos = computed<GrupoWorkflows[]>(() => {
    const porCodigo = new Map<string, GrupoWorkflows>();
    for (const workflow of this.workflows()) {
      const codigo = workflow.tipoInstrumento.codigo;
      let grupo = porCodigo.get(codigo);
      if (!grupo) {
        grupo = { tipoInstrumento: workflow.tipoInstrumento, workflows: [] };
        porCodigo.set(codigo, grupo);
      }
      grupo.workflows.push(workflow);
    }
    return [...porCodigo.values()];
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.erro.set(null);
    this.workflowService.listar().subscribe({
      next: (workflows) => {
        this.workflows.set(workflows);
        this.selecionado.set(workflows[0] ?? null);
        this.gruposAbertos.set(new Set(workflows.length > 0 ? [workflows[0].tipoInstrumento.codigo] : []));
        this.loading.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.loading.set(false);
      },
    });
  }

  estaAberto(codigo: string): boolean {
    return this.gruposAbertos().has(codigo);
  }

  alternarGrupo(codigo: string): void {
    this.gruposAbertos.update((abertos) => {
      const novos = new Set(abertos);
      if (!novos.delete(codigo)) {
        novos.add(codigo);
      }
      return novos;
    });
  }

  rotulosAtores(passo: PassoWorkflow): string {
    return passo.atores.map((ator) => ROTULOS_ATOR[ator] ?? ator).join(', ');
  }
}
