import { Component, DestroyRef, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, catchError, map, of, startWith, switchMap, tap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoNotificacao, NotificacaoDetalhe, NotificacaoResumo } from '../../../models/notificacao.model';
import { LOCALIZACAO, MENSAGEM_ERRO_DESCARGA } from '../../../services/descarga.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../../services/notificacoes-nao-lidas.service';
import { ConfirmacaoModalComponent } from '../../../shared/components/confirmacao-modal.component';
import { ROUTES } from '../../../shared/routes';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { SeloComponent } from '../../../shared/ui/selo/selo.component';
import {
  apresentacaoTipo,
  formatarDataHora,
  formatarDia,
  remetente,
  seloEstado,
  tamanhoEmKb,
  temConversacao,
} from '../apresentacao-notificacao';
import { ACEITES_ANEXO, enviarAnexos, validarAnexo } from '../anexos-notificacao/anexos-notificacao.component';
import { IconeNotificacaoComponent } from '../icone-notificacao.component';

export const MAXIMO_RESPOSTA = 5000;

/** Códigos em que o estado da notificação mudou entretanto: o detalhe é relido e a caixa pode desaparecer. */
const CODIGOS_RELER = ['NOTIF_ESTADO_INVALIDO', 'NOTIF_TIPO_SEM_CONVERSACAO', 'NOTIF_PERFIL_NAO_AUTORIZADO'];

export type OperacaoNotificacao = 'LER' | 'RESOLVER' | 'REMOVER';

const ANUNCIO_SUCESSO: Record<OperacaoNotificacao, string> = {
  LER: 'Notificação marcada como lida.',
  RESOLVER: 'Pedido de apoio resolvido.',
  REMOVER: 'Notificação removida.',
};

function naoEmBranco(controlo: AbstractControl<string>): ValidationErrors | null {
  return controlo.value.trim() === '' ? { required: true } : null;
}

@Component({
  selector: 'app-notificacao-detalhe',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    ReactiveFormsModule,
    AlertaComponent,
    BotaoComponent,
    CampoTextoComponent,
    ConfirmacaoModalComponent,
    SeloComponent,
    IconeNotificacaoComponent,
  ],
  templateUrl: './notificacao-detalhe.component.html',
  styleUrl: './notificacao-detalhe.component.css',
})
export class NotificacaoDetalheComponent {
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly naoLidas = inject(NotificacoesNaoLidasService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly localizacao = inject(LOCALIZACAO);

  readonly notificacaoId = input.required<string>();
  readonly soLeitura = input(false);
  readonly fechar = output<void>();
  readonly naoEncontrada = output<void>();
  /** A notificação mudou (nova mensagem ou estado): a lista volta a pedir a página atual. */
  readonly alterada = output<void>();

  readonly notificacao = signal<NotificacaoDetalhe | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<string | null>(null);

  readonly maximoResposta = MAXIMO_RESPOSTA;
  readonly controloResposta = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, naoEmBranco, Validators.maxLength(MAXIMO_RESPOSTA)],
  });
  readonly aEnviar = signal(false);
  readonly erroEnvio = signal<string | null>(null);
  readonly erroCampoServidor = signal<string | null>(null);
  readonly anuncio = signal('');
  readonly aReabrir = signal(false);
  readonly erroReabrir = signal<string | null>(null);
  private readonly tocado = signal(false);
  private readonly valorResposta = toSignal(this.controloResposta.valueChanges.pipe(startWith('')), {
    initialValue: '',
  });

  readonly tipo = computed(() => {
    const n = this.notificacao();
    return n ? apresentacaoTipo(n) : null;
  });
  readonly estado = computed(() => {
    const n = this.notificacao();
    return n ? seloEstado(n) : null;
  });
  readonly comConversacao = computed(() => {
    const n = this.notificacao();
    return n !== null && temConversacao(n.tipo);
  });
  readonly podeResponder = computed(() => this.disponivel('RESPONDER'));
  readonly podeReabrir = computed(() => this.notificacao()?.acoesDisponiveis?.includes('REABRIR') ?? false);
  readonly podeLer = computed(() => this.disponivel('LER'));
  readonly podeResolver = computed(() => this.disponivel('RESOLVER'));
  readonly podeRemover = computed(() => this.disponivel('REMOVER'));
  readonly podeAnexar = computed(() => this.disponivel('ANEXAR'));
  readonly aAnexar = signal(false);
  readonly errosAnexo = signal<string[]>([]);
  readonly aDescarregar = signal<string | null>(null);
  readonly erroDescarga = signal<string | null>(null);
  readonly aceitesAnexo = ACEITES_ANEXO;
  private readonly seletorAnexos = viewChild<ElementRef<HTMLInputElement>>('seletorAnexos');
  readonly temOperacoes = computed(() => this.podeLer() || this.podeResolver() || this.podeRemover());
  readonly aOperar = signal<OperacaoNotificacao | null>(null);
  readonly erroOperacao = signal<string | null>(null);
  readonly confirmacao = signal<Exclude<OperacaoNotificacao, 'LER'> | null>(null);
  readonly ehAlerta = computed(() => this.notificacao()?.tipo === 'ALERTA');
  readonly tituloConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Marcar como resolvida';
    }
    return this.ehAlerta() ? 'Descartar alerta' : 'Eliminar notícia';
  });
  readonly mensagemConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Marcar este pedido de apoio como resolvido sem responder?';
    }
    return this.ehAlerta()
      ? 'Descartar este alerta?'
      : 'Eliminar esta notícia? A notícia deixa de estar visível para todos os destinatários.';
  });
  readonly rotuloSimConfirmacao = computed(() => {
    if (this.confirmacao() === 'RESOLVER') {
      return 'Marcar como resolvida';
    }
    return this.ehAlerta() ? 'Descartar' : 'Eliminar';
  });
  private readonly tituloDetalhe = viewChild<ElementRef<HTMLElement>>('tituloDetalhe');
  readonly respostaValida = computed(() => {
    const valor = this.valorResposta();
    return valor.trim() !== '' && valor.length <= MAXIMO_RESPOSTA;
  });
  readonly contador = computed(() => `${this.valorResposta().length}/${MAXIMO_RESPOSTA} caracteres`);
  readonly erroResposta = computed(() => {
    if (this.erroCampoServidor()) {
      return this.erroCampoServidor();
    }
    if (!this.tocado() || this.respostaValida()) {
      return null;
    }
    return this.valorResposta().length > MAXIMO_RESPOSTA
      ? `A resposta não pode ter mais de ${MAXIMO_RESPOSTA} caracteres.`
      : 'A resposta é obrigatória.';
  });
  /**
   * Só navegação: quem autoriza a transferência é a API (só o `MASTER` reatribui). Um pedido sem processo leva só o
   * `pedidoApoioId` e o Master procura o processo no ecrã.
   */
  readonly parametrosAlterarOwner = computed<Record<string, string> | null>(() => {
    const n = this.notificacao();
    const pedidoPendenteDoMaster =
      n !== null && n.tipo === 'PEDIDO_APOIO' && n.estado === 'ATIVA' && n.perfilDestino === 'MASTER';
    if (!pedidoPendenteDoMaster || this.authService.perfilInterno() !== 'MASTER') {
      return null;
    }
    const parametros: Record<string, string> = { pedidoApoioId: n.id };
    return n.processoId ? { processoId: n.processoId, ...parametros } : parametros;
  });
  readonly destinatarios = computed(() => {
    const n = this.notificacao();
    if (!n) {
      return '';
    }
    const nomes = [n.destinatarioEntidadeNome, n.destinatarioUtilizadorNome].filter((nome): nome is string => !!nome);
    if (nomes.length > 0) {
      return nomes.join(' — ');
    }
    return n.perfilDestino === 'MASTER' ? 'Master' : 'Todos';
  });

  readonly remetente = remetente;
  readonly formatarDataHora = formatarDataHora;
  readonly formatarDia = formatarDia;
  readonly tamanhoEmKb = tamanhoEmKb;
  readonly rotaProcesso = ROUTES.PROCESSO_DETALHE;
  readonly rotaAlteracaoOwner = ROUTES.ADMINISTRACAO_ALTERACAO_OWNER;

  constructor() {
    toObservable(this.notificacaoId)
      .pipe(
        tap(() => {
          this.aCarregar.set(true);
          this.erro.set(null);
          this.notificacao.set(null);
          this.limparResposta();
          this.erroEnvio.set(null);
          this.erroReabrir.set(null);
          this.erroOperacao.set(null);
          this.confirmacao.set(null);
          this.anuncio.set('');
          this.errosAnexo.set([]);
          this.erroDescarga.set(null);
        }),
        switchMap((id) =>
          this.notificacaoService.obter(id).pipe(
            catchError((erro: unknown) => {
              if (erro instanceof ApiError && erro.codigo === 'NOTIF_NAO_ENCONTRADA') {
                this.naoEncontrada.emit();
              } else {
                this.erro.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível carregar a notificação.');
              }
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((notificacao) => {
        this.notificacao.set(notificacao);
        this.aCarregar.set(false);
      });
  }

  marcarTocado(): void {
    this.tocado.set(true);
  }

  enviar(evento?: Event): void {
    evento?.preventDefault();
    const n = this.notificacao();
    this.tocado.set(true);
    if (!n || !this.respostaValida() || this.aEnviar()) {
      return;
    }
    this.aEnviar.set(true);
    this.erroEnvio.set(null);
    this.erroCampoServidor.set(null);
    this.anuncio.set('');
    this.notificacaoService
      .responder(n.id, this.controloResposta.value.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aEnviar.set(false);
          this.limparResposta();
          this.notificacao.update((atual) =>
            atual && atual.id === n.id
              ? {
                  ...atual,
                  estado: resposta.estado,
                  mensagens: [...atual.mensagens, resposta.mensagem],
                  numeroMensagens: atual.numeroMensagens + 1,
                }
              : atual,
          );
          this.anuncio.set('Resposta enviada.');
          this.naoLidas.atualizar();
          this.alterada.emit();
          this.reler(n.id);
        },
        error: (erro: unknown) => {
          this.aEnviar.set(false);
          if (!(erro instanceof ApiError)) {
            this.erroEnvio.set('Não foi possível enviar a resposta.');
            return;
          }
          if (erro.codigo === 'VALIDACAO') {
            const doCampo = erro.campos?.find((campo) => campo.path === 'texto')?.erro;
            if (doCampo) {
              this.erroCampoServidor.set(doCampo);
              return;
            }
          }
          this.erroEnvio.set(erro.mensagem);
          if (CODIGOS_RELER.includes(erro.codigo) || erro.codigo === 'NOTIF_NAO_ENCONTRADA') {
            this.reler(n.id);
          }
        },
      });
  }

  reabrir(): void {
    const n = this.notificacao();
    if (!n || this.aReabrir()) {
      return;
    }
    this.aReabrir.set(true);
    this.erroReabrir.set(null);
    this.anuncio.set('');
    this.notificacaoService
      .reabrir(n.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resumo) => {
          this.aReabrir.set(false);
          this.notificacao.update((atual) =>
            atual && atual.id === n.id
              ? {
                  ...atual,
                  estado: resumo.estado,
                  resolvidoEm: resumo.resolvidoEm,
                  acoesDisponiveis: atual.acoesDisponiveis.filter((acao) => acao !== 'REABRIR'),
                }
              : atual,
          );
          this.anuncio.set('Pedido reaberto.');
          this.alterada.emit();
          this.reler(n.id);
        },
        error: (erro: unknown) => {
          this.aReabrir.set(false);
          this.erroReabrir.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível reabrir o pedido.');
          this.reler(n.id);
        },
      });
  }

  descarregar(documento: DocumentoNotificacao): void {
    const n = this.notificacao();
    if (!n || this.aDescarregar()) {
      return;
    }
    this.erroDescarga.set(null);
    this.aDescarregar.set(documento.id);
    this.notificacaoService
      .urlDownload(n.id, documento.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aDescarregar.set(null);
          this.localizacao.assign(resposta.url);
        },
        error: (erro: unknown) => {
          this.aDescarregar.set(null);
          this.erroDescarga.set(erro instanceof ApiError ? erro.mensagem : MENSAGEM_ERRO_DESCARGA);
        },
      });
  }

  escolherAnexos(): void {
    this.seletorAnexos()?.nativeElement.click();
  }

  anexar(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const escolhidos = [...(campo.files ?? [])];
    campo.value = '';
    const n = this.notificacao();
    if (!n || escolhidos.length === 0 || this.aAnexar()) {
      return;
    }
    const recusados = escolhidos.map(validarAnexo).filter((erro): erro is string => erro !== null);
    const validos = escolhidos.filter((ficheiro) => validarAnexo(ficheiro) === null);
    this.errosAnexo.set(recusados);
    this.anuncio.set('');
    if (validos.length === 0) {
      return;
    }
    this.aAnexar.set(true);
    enviarAnexos(this.notificacaoService, n.id, validos)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resultados) => {
        this.aAnexar.set(false);
        const anexados = resultados.filter((r) => r.documento !== null).length;
        this.errosAnexo.set([...recusados, ...resultados.flatMap((r) => (r.erro ? [r.erro] : []))]);
        if (anexados > 0) {
          this.anuncio.set(`${anexados} documento(s) anexado(s).`);
          this.alterada.emit();
        }
        this.reler(n.id);
      });
  }

  marcarComoLida(): void {
    this.executar('LER');
  }

  pedirConfirmacao(operacao: Exclude<OperacaoNotificacao, 'LER'>): void {
    this.erroOperacao.set(null);
    this.confirmacao.set(operacao);
  }

  cancelarConfirmacao(): void {
    this.confirmacao.set(null);
  }

  confirmar(): void {
    const operacao = this.confirmacao();
    if (operacao) {
      this.executar(operacao);
    }
  }

  private executar(operacao: OperacaoNotificacao): void {
    const n = this.notificacao();
    if (!n || this.aOperar()) {
      return;
    }
    this.aOperar.set(operacao);
    this.erroOperacao.set(null);
    this.anuncio.set('');
    const pedido: Observable<NotificacaoResumo | null> =
      operacao === 'REMOVER'
        ? this.notificacaoService.remover(n.id).pipe(map(() => null))
        : operacao === 'LER'
          ? this.notificacaoService.ler(n.id)
          : this.notificacaoService.resolver(n.id);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resumo) => {
        this.aOperar.set(null);
        this.confirmacao.set(null);
        this.notificacao.update((atual) =>
          atual && atual.id === n.id
            ? { ...atual, ...(resumo ?? { estado: 'REMOVIDA' as const }), acoesDisponiveis: [] }
            : atual,
        );
        this.anuncio.set(ANUNCIO_SUCESSO[operacao]);
        this.naoLidas.atualizar();
        this.alterada.emit();
        this.reler(n.id);
        // O botão usado desaparece: o foco passa ao título para não cair no fim da página.
        setTimeout(() => this.tituloDetalhe()?.nativeElement.focus());
      },
      error: (erro: unknown) => {
        this.aOperar.set(null);
        this.confirmacao.set(null);
        this.erroOperacao.set(erro instanceof ApiError ? erro.mensagem : 'Não foi possível concluir a operação.');
        this.reler(n.id);
      },
    });
  }

  private disponivel(acao: NotificacaoDetalhe['acoesDisponiveis'][number]): boolean {
    return !this.soLeitura() && (this.notificacao()?.acoesDisponiveis?.includes(acao) ?? false);
  }

  /** Relê o detalhe sem o apagar do ecrã, para atualizar o estado e `acoesDisponiveis`. */
  private reler(id: string): void {
    this.notificacaoService
      .obter(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (notificacao) => {
          if (this.notificacaoId() === id) {
            this.notificacao.set(notificacao);
          }
        },
        error: (erro: unknown) => {
          if (erro instanceof ApiError && erro.codigo === 'NOTIF_NAO_ENCONTRADA') {
            this.naoEncontrada.emit();
          }
        },
      });
  }

  private limparResposta(): void {
    this.controloResposta.reset('');
    this.tocado.set(false);
    this.erroCampoServidor.set(null);
  }
}
