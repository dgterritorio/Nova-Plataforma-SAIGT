import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import { CamposResumoValidados, Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CartaoComponent } from '../../../shared/ui/cartao/cartao.component';

const CODIGOS_DE_CONFLITO = ['VERSAO_DESATUALIZADA', 'ESTADO_INVALIDO'];
export const MAXIMO_NOTAS = 4000;

const ROTULO_ESTADO_APOS_PUBLICAR: Record<string, string> = {
  ENVIADO_INCM: 'Enviado INCM',
  EM_VALIDACAO_INCM: 'Em validação INCM',
  ANULADO_INCM: 'Anulado INCM',
};

type CampoTexto = 'notasIncm' | 'notasDgt';

/**
 * Resumo do pedido com todos os itens validados: dados do processo e os campos opcionais INCM, DGT, Reg. Uso do Solo e
 * Alteração PEOT's. Em `EM_VALIDACAO_DGT`, qualquer «Gravar» envia os quatro campos no `validar` (a única operação que
 * os grava) e o processo passa a `VALIDADO_DGT`; daí em diante os valores leem-se e o rodapé mostra «Publicar» e
 * «Enviar PCM» segundo `acoesDisponiveis`.
 */
@Component({
  selector: 'app-resumo-validados',
  imports: [BotaoComponent, CartaoComponent, ErrorBannerComponent, ReactiveFormsModule],
  templateUrl: './resumo-validados.component.html',
  styleUrl: './resumo-validados.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumoValidadosComponent {
  private readonly processoService = inject(ProcessoService);
  private readonly injector = inject(Injector);

  readonly processo = input.required<ProcessoDetalhe>();

  /** Volta à revisão dos itens sem gravar nada; só antes da validação. */
  readonly anterior = output<void>();
  /** O `validar` passou: o ecrã relê o detalhe, que traz as ações de envio. */
  readonly validado = output<Processo>();
  readonly publicado = output<Processo>();
  /** O ecrã abre aqui o diálogo de escolha do utilizador da PCM. */
  readonly enviarPcm = output<void>();
  readonly recarregar = output<void>();

  private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

  readonly maximoNotas = MAXIMO_NOTAS;
  readonly blocos: readonly { campo: CampoTexto; titulo: string; subtitulo: string }[] = [
    { campo: 'notasIncm', titulo: 'INCM', subtitulo: 'Imprensa Nacional Casa da Moeda' },
    { campo: 'notasDgt', titulo: 'DGT', subtitulo: 'Direção-Geral do Território' },
  ];
  readonly formulario = new FormGroup({
    notasIncm: new FormControl<string>('', { nonNullable: true }),
    notasDgt: new FormControl<string>('', { nonNullable: true }),
    regUsoSolo: new FormControl<boolean | null>(null),
    alteracaoPeotId: new FormControl<number | null>(null),
  });

  readonly aGravar = signal(false);
  readonly aPublicar = signal(false);
  readonly erro = signal<ApiError | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);

  readonly emEdicao = computed(
    () => this.processo().estado === 'EM_VALIDACAO_DGT' && this.processo().acoesDisponiveis.includes('validar'),
  );
  readonly opcoesPeot = computed(() => this.processo().opcoesAlteracaoPeot ?? []);
  /** Sem opções na lista o campo não aparece; um valor já gravado continua a ler-se. */
  readonly mostraPeot = computed(() =>
    this.emEdicao() ? this.opcoesPeot().length > 0 : this.processo().alteracaoPeotId != null || this.opcoesPeot().length > 0,
  );
  readonly podePublicar = computed(() => this.processo().acoesDisponiveis.includes('enviar-incm'));
  readonly podeEnviarPcm = computed(() => this.processo().acoesDisponiveis.includes('enviar-pcm'));
  readonly conflito = computed(() => CODIGOS_DE_CONFLITO.includes(this.erro()?.codigo ?? ''));
  readonly ocupado = computed(() => this.aGravar() || this.aPublicar());

  readonly concelhos = computed(() => {
    const concelhos = this.processo().territorio?.concelhos ?? [];
    return concelhos.length > 0 ? concelhos.map((concelho) => concelho.nome ?? concelho.codigo).join(', ') : null;
  });

  readonly regUsoSoloLido = computed(() => {
    const valor = this.processo().regUsoSolo;
    return valor == null ? null : valor ? 'Sim' : 'Não';
  });

  constructor() {
    afterNextRender(() => this.titulo().nativeElement.focus());
    // Em edição, o formulário parte dos valores do detalhe (vazios antes da validação); nunca durante uma gravação.
    effect(() => {
      const processo = this.processo();
      untracked(() => {
        if (!this.aGravar()) {
          this.formulario.reset({
            notasIncm: processo.notasIncm ?? '',
            notasDgt: processo.notasDgt ?? '',
            regUsoSolo: processo.regUsoSolo ?? null,
            alteracaoPeotId: processo.alteracaoPeotId ?? null,
          });
        }
      });
    });
  }

  erroDoCampo(campo: CampoTexto | 'alteracaoPeotId'): string | null {
    const erro = this.erro();
    if (erro?.codigo !== 'VALIDACAO') {
      return null;
    }
    return erro.campos?.find((c) => c.path === campo)?.erro ?? null;
  }

  caracteres(campo: CampoTexto): number {
    return this.formulario.controls[campo].value.length;
  }

  gravar(): void {
    const processo = this.processo();
    if (!this.emEdicao() || this.ocupado() || this.conflito()) {
      return;
    }
    const valores = this.formulario.getRawValue();
    const campos: CamposResumoValidados = {
      notasIncm: textoOuNulo(valores.notasIncm),
      notasDgt: textoOuNulo(valores.notasDgt),
      regUsoSolo: valores.regUsoSolo,
      alteracaoPeotId: this.mostraPeot() ? valores.alteracaoPeotId : null,
    };
    this.erro.set(null);
    this.mensagemSucesso.set(null);
    this.aGravar.set(true);
    this.formulario.disable({ emitEvent: false });
    this.processoService.validar(processo.id, processo.versao, campos).subscribe({
      next: (atualizado) => {
        this.aGravar.set(false);
        this.formulario.enable({ emitEvent: false });
        this.mensagemSucesso.set('Processo validado. Os campos do resumo ficaram gravados.');
        this.validado.emit(atualizado);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.formulario.enable({ emitEvent: false });
        this.erro.set(erro);
      },
    });
  }

  publicar(): void {
    const processo = this.processo();
    if (!this.podePublicar() || this.ocupado() || this.conflito()) {
      return;
    }
    this.erro.set(null);
    this.mensagemSucesso.set(null);
    this.aPublicar.set(true);
    this.processoService.enviarIncm(processo.id, processo.versao).subscribe({
      next: (atualizado) => {
        this.aPublicar.set(false);
        const estado = ROTULO_ESTADO_APOS_PUBLICAR[atualizado.estado] ?? atualizado.estado;
        this.mensagemSucesso.set(`Processo enviado para publicação na INCM. Estado: ${estado}.`);
        this.publicado.emit(atualizado);
      },
      error: (erro: ApiError) => {
        this.aPublicar.set(false);
        this.erro.set(erro);
      },
    });
  }

  pedirEnvioPcm(): void {
    if (this.podeEnviarPcm() && !this.ocupado()) {
      this.enviarPcm.emit();
    }
  }

  /** Uma ação feita fora do resumo (o diálogo da PCM): a mensagem é anunciada e o foco volta ao topo do resumo. */
  anunciar(mensagem: string): void {
    this.erro.set(null);
    this.mensagemSucesso.set(mensagem);
    // Depois do render: o modal, ao fechar, devolve o foco ao botão que entretanto sai do rodapé.
    afterNextRender(() => this.titulo().nativeElement.focus(), { injector: this.injector });
  }

  /** Depois de um 409 o detalhe está velho: lê-se de novo, nunca se repete o pedido. */
  pedirRecarregar(): void {
    this.erro.set(null);
    this.recarregar.emit();
  }
}

function textoOuNulo(texto: string): string | null {
  const limpo = texto.trim();
  return limpo === '' ? null : limpo;
}
