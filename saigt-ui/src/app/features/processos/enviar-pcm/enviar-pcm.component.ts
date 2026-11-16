import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, Subscription, forkJoin, map, of, switchMap } from 'rxjs';
import { ApiError } from '../../../core/models/error-response.model';
import { Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

/** Uma página chega: as entidades PCM são poucas e cada uma tem poucos utilizadores. */
const TAMANHO_PAGINA = 100;

/** O envio já não é possível como o ecrã o mostra: lê-se o processo de novo, nunca se repete o pedido. */
const CODIGOS_DE_CONFLITO = [
  'PCM_ESTADO_INVALIDO',
  'PCM_SERIE_NAO_PRIMEIRA',
  'PCM_JA_ATRIBUIDO',
  'PCM_DOCUMENTOS_POR_VALIDAR',
  'VERSAO_DESATUALIZADA',
  'ESTADO_INVALIDO',
  'DESTINO_SO_DEPOSITO',
];
const CODIGO_UTILIZADOR_INVALIDO = 'PCM_UTILIZADOR_INVALIDO';

/**
 * Escolha do utilizador da PCM a quem o processo `VALIDADO_DGT` fica atribuído. Os candidatos são os utilizadores das
 * entidades PCM ativas; as duas listagens são só do `MASTER`, por isso a outro perfil o seletor fica «Indisponível».
 * Os dados dos candidatos vivem só neste componente, enquanto o diálogo está aberto.
 */
@Component({
  selector: 'app-enviar-pcm',
  imports: [AlertaComponent, BotaoComponent, ErrorBannerComponent, ModalComponent, ReactiveFormsModule, SeletorComponent],
  templateUrl: './enviar-pcm.component.html',
  styleUrl: './enviar-pcm.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnviarPcmComponent {
  private readonly processoService = inject(ProcessoService);
  private readonly entidadeService = inject(EntidadeService);
  private readonly utilizadorService = inject(UtilizadorService);

  readonly aberto = input(false);
  readonly processo = input.required<ProcessoDetalhe>();

  readonly fechar = output<void>();
  /** O `PATCH` passou: o ecrã funde a resposta e fecha o diálogo. */
  readonly processoAlterado = output<Processo>();
  /** Pedido depois de um 409: o ecrã relê o processo. */
  readonly recarregar = output<void>();

  /** Nulo enquanto carrega ou depois de falhar. */
  readonly candidatos = signal<Utilizador[] | null>(null);
  readonly falhaCandidatosPcm = signal(false);
  readonly utilizadorId = signal<string | null>(null);
  readonly aProcessar = signal(false);
  readonly erro = signal<ApiError | null>(null);

  readonly controloUtilizador = new FormControl<string | null>(null);
  readonly idAvisoSemCandidatos = 'enviar-pcm-sem-candidatos';

  readonly semCandidatos = computed(() => this.candidatos()?.length === 0);
  readonly conflito = computed(() => CODIGOS_DE_CONFLITO.includes(this.erro()?.codigo ?? ''));
  readonly erroUtilizador = computed(() => {
    const erro = this.erro();
    if (erro?.codigo !== CODIGO_UTILIZADOR_INVALIDO) {
      return null;
    }
    return erro.campos?.find((campo) => campo.path === 'utilizadorId')?.erro ?? erro.mensagem;
  });
  /** O erro do utilizador fica junto ao seletor; os restantes, no banner. */
  readonly erroBanner = computed(() => (this.erroUtilizador() ? null : this.erro()));
  readonly opcoes = computed<OpcaoSeletor[]>(() =>
    (this.candidatos() ?? []).map((u) => ({ valor: u.id, rotulo: `${u.nome ?? '—'} · ${u.email ?? '—'}` })),
  );
  readonly placeholder = computed(() =>
    this.candidatos() !== null ? 'Selecione…' : this.falhaCandidatosPcm() ? 'Indisponível' : 'A carregar…',
  );
  readonly podeConfirmar = computed(
    () => this.utilizadorId() !== null && this.candidatos() !== null && !this.aProcessar() && !this.conflito(),
  );

  private carregamento: Subscription | null = null;

  constructor() {
    // Cada abertura recomeça do zero e lê os candidatos de novo; Cancelar não deixa escolha para a próxima vez.
    effect(() => {
      if (this.aberto()) {
        untracked(() => {
          this.limpar();
          this.carregarCandidatosPcm();
        });
      } else {
        untracked(() => this.limpar());
      }
    });
    effect(() => {
      const desativado = this.aProcessar() || this.candidatos() === null || this.semCandidatos();
      if (desativado) {
        this.controloUtilizador.disable({ emitEvent: false });
      } else {
        this.controloUtilizador.enable({ emitEvent: false });
      }
    });
    this.controloUtilizador.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => {
      this.utilizadorId.set(valor || null);
      if (this.erroUtilizador()) {
        this.erro.set(null);
      }
    });
  }

  /** Os utilizadores de todas as entidades PCM ativas, sem repetir quem tem vínculo a mais de uma. */
  carregarCandidatosPcm(): void {
    this.carregamento?.unsubscribe();
    this.candidatos.set(null);
    this.falhaCandidatosPcm.set(false);
    this.erro.set(null);
    this.carregamento = this.entidadeService
      .listar({ tipoEntidade: 'PCM', ativo: true }, 0, TAMANHO_PAGINA)
      .pipe(
        switchMap((pagina): Observable<Utilizador[][]> =>
          pagina.content.length === 0
            ? of([])
            : forkJoin(
                pagina.content.map((entidade) =>
                  this.utilizadorService.listar({ entidadeId: entidade.id }, 0, TAMANHO_PAGINA).pipe(map((p) => p.content)),
                ),
              ),
        ),
        map((listas) => semRepetidos(listas.flat())),
      )
      .subscribe({
        next: (utilizadores) => this.candidatos.set(utilizadores),
        error: (erro: ApiError) => {
          this.falhaCandidatosPcm.set(true);
          this.erro.set(erro);
        },
      });
  }

  confirmar(): void {
    const utilizadorId = this.utilizadorId();
    if (!this.podeConfirmar() || utilizadorId === null) {
      return;
    }
    const processo = this.processo();
    this.erro.set(null);
    this.aProcessar.set(true);
    this.processoService.enviarPcm(processo.id, processo.versao, utilizadorId).subscribe({
      next: (atualizado) => {
        this.aProcessar.set(false);
        this.processoAlterado.emit(atualizado);
      },
      error: (erro: ApiError) => {
        this.aProcessar.set(false);
        this.erro.set(erro);
      },
    });
  }

  private limpar(): void {
    this.carregamento?.unsubscribe();
    this.carregamento = null;
    this.candidatos.set(null);
    this.falhaCandidatosPcm.set(false);
    this.utilizadorId.set(null);
    this.aProcessar.set(false);
    this.erro.set(null);
    this.controloUtilizador.reset(null, { emitEvent: false });
  }
}

function semRepetidos(utilizadores: Utilizador[]): Utilizador[] {
  const vistos = new Set<string>();
  return utilizadores.filter((u) => !vistos.has(u.id) && vistos.add(u.id));
}
