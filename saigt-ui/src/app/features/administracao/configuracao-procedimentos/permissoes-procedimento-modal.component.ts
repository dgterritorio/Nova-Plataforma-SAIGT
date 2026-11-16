import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Observable, catchError, concatMap, from, map, of, toArray } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { PermissaoConfig, ProcedimentoConfig, TIPOS_ENTIDADE, TipoEntidade } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';

/** No máximo uma linha por tipo de entidade (11). */
const TAMANHO_PERMISSOES = 100;

interface Alteracao {
  tipoEntidade: TipoEntidade;
  ativo: boolean;
  linha: PermissaoConfig | null;
}

interface Falha {
  tipoEntidade: TipoEntidade;
  ativo: boolean;
  mensagem: string;
}

export interface FalhaPermissoes {
  tipos: TipoEntidade[];
  mensagens: string[];
}

let proximoId = 0;

/**
 * Tipos de entidade que podem criar, continuar e corrigir um Procedimento. Grava só o que mudou, um pedido
 * de cada vez: primeiro as permissões novas e só depois as retiradas, para o Procedimento nunca ficar sem
 * nenhuma a meio. Um pedido que falha não trava os seguintes.
 */
@Component({
  selector: 'app-permissoes-procedimento-modal',
  imports: [AlertaComponent, BotaoComponent, ErrorBannerComponent, ModalComponent, ReactiveFormsModule],
  templateUrl: './permissoes-procedimento-modal.component.html',
  styleUrl: './configuracao-formulario.css',
})
export class PermissoesProcedimentoModalComponent {
  private readonly configuracaoService = inject(ConfiguracaoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly aberto = input(false);
  readonly procedimento = input<ProcedimentoConfig | null>(null);
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-permissoes-${++proximoId}`;
  readonly idErro = `${this.idFormulario}-erro`;
  readonly tiposEntidade = TIPOS_ENTIDADE;
  readonly titulo = computed(() => `Permissões — ${this.procedimento()?.tipoProcedimento.nome ?? ''}`);

  readonly linhas = signal<PermissaoConfig[]>([]);
  readonly aCarregar = signal(false);
  readonly aGravar = signal(false);
  readonly erroCarregar = signal<ApiError | null>(null);
  readonly falha = signal<FalhaPermissoes | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);

  readonly formulario = new FormGroup(
    Object.fromEntries(TIPOS_ENTIDADE.map((tipo) => [tipo, new FormControl(false, { nonNullable: true })])) as Record<
      TipoEntidade,
      FormControl<boolean>
    >,
  );

  constructor() {
    effect(() => {
      const procedimento = this.procedimento();
      if (this.aberto() && procedimento) {
        untracked(() => {
          this.falha.set(null);
          this.mensagemSucesso.set(null);
          this.carregar(procedimento, []);
        });
      }
    });
  }

  idCaixa(tipo: TipoEntidade): string {
    return `${this.idFormulario}-${tipo}`;
  }

  rotuloFalha(falha: FalhaPermissoes): string {
    return `Não foi possível gravar: ${falha.tipos.join(', ')}.`;
  }

  guardar(): void {
    const procedimento = this.procedimento();
    if (!procedimento || this.aGravar() || this.aCarregar()) {
      return;
    }
    const alteracoes = this.alteracoes();
    this.falha.set(null);
    this.mensagemSucesso.set(null);
    if (alteracoes.length === 0) {
      this.mensagemSucesso.set('Sem alterações para gravar.');
      return;
    }
    this.aGravar.set(true);
    from(alteracoes)
      .pipe(
        concatMap((alteracao) => this.gravar(procedimento.id, alteracao)),
        toArray(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultados) => {
        const falhas = resultados.filter((resultado): resultado is Falha => resultado !== null);
        this.aGravar.set(false);
        if (falhas.length > 0) {
          this.falha.set({
            tipos: falhas.map((f) => f.tipoEntidade),
            mensagens: [...new Set(falhas.map((f) => f.mensagem))],
          });
        } else {
          this.mensagemSucesso.set('Permissões gravadas.');
        }
        this.carregar(procedimento, falhas);
      });
  }

  private alteracoes(): Alteracao[] {
    const valores = this.formulario.getRawValue();
    const novas: Alteracao[] = [];
    const trocadas: Alteracao[] = [];
    for (const tipoEntidade of TIPOS_ENTIDADE) {
      const linha = this.linhas().find((l) => l.tipoEntidade === tipoEntidade) ?? null;
      const ativo = valores[tipoEntidade];
      if (!linha && ativo) {
        novas.push({ tipoEntidade, ativo, linha });
      } else if (linha && linha.ativo !== ativo) {
        (ativo ? novas : trocadas).push({ tipoEntidade, ativo, linha });
      }
    }
    return [...novas, ...trocadas];
  }

  private gravar(procedimentoId: number, alteracao: Alteracao): Observable<Falha | null> {
    const pedido = { instrumentoProcedimentoConfigId: procedimentoId, tipoEntidade: alteracao.tipoEntidade, ativo: alteracao.ativo };
    const gravacao = alteracao.linha
      ? this.configuracaoService.atualizarPermissao(alteracao.linha.id, pedido)
      : this.configuracaoService.criarPermissao(pedido);
    return gravacao.pipe(
      map(() => null),
      catchError((erro: ApiError) => of({ tipoEntidade: alteracao.tipoEntidade, ativo: alteracao.ativo, mensagem: erro.mensagem })),
    );
  }

  /** Mostra o estado gravado; as caixas que falharam ficam como o Master as deixou, para poder tentar outra vez. */
  private carregar(procedimento: ProcedimentoConfig, falhas: readonly Falha[]): void {
    this.aCarregar.set(true);
    this.erroCarregar.set(null);
    this.configuracaoService
      .listarPermissoes({ instrumentoProcedimentoConfigId: procedimento.id }, 0, TAMANHO_PERMISSOES)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.linhas.set(resposta.content);
          const valores = Object.fromEntries(
            TIPOS_ENTIDADE.map((tipo) => [tipo, resposta.content.some((l) => l.tipoEntidade === tipo && l.ativo)]),
          ) as Record<TipoEntidade, boolean>;
          for (const falha of falhas) {
            valores[falha.tipoEntidade] = falha.ativo;
          }
          this.formulario.reset(valores);
          this.aCarregar.set(false);
        },
        error: (erro: ApiError) => {
          this.erroCarregar.set(erro);
          this.aCarregar.set(false);
        },
      });
  }
}
