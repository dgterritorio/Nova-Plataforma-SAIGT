import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  BYTES_POR_MB,
  ProcedimentoConfig,
  ROTULOS_SERIE_DR,
  SerieDr,
  TipoProcedimentoConfig,
} from '../../../models/configuracao.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';
import { inteiroPositivo, numeroOuNulo, separarErroServidor } from './erros-formulario';

export interface ValorProcedimentoConfig {
  tipoProcedimentoId: number;
  ordem: number;
  serieDr: SerieDr;
  /** Em bytes. */
  tamanhoMaximoProcesso: number | null;
  obrigatorio: boolean;
  repetivel: boolean;
  atoPontual: boolean;
  visivelNaDinamica: boolean;
}

type CampoProcedimento =
  | 'tipoProcedimentoId'
  | 'ordem'
  | 'serieDr'
  | 'tamanhoMaximoProcesso'
  | 'obrigatorio'
  | 'repetivel'
  | 'atoPontual'
  | 'visivelNaDinamica';

const CAMPOS: readonly CampoProcedimento[] = [
  'tipoProcedimentoId',
  'ordem',
  'serieDr',
  'tamanhoMaximoProcesso',
  'obrigatorio',
  'repetivel',
  'atoPontual',
  'visivelNaDinamica',
];

export const AJUDA_TIPO_PROCEDIMENTO_EM_USO = 'Já há processos deste Procedimento: o tipo não pode mudar.';
export const AVISO_SEM_FORMULARIO =
  'Este tipo de Procedimento ainda não tem formulário na aplicação: os processos não vão poder gravar o formulário.';

let proximoId = 0;

/** Cria ou edita um Procedimento de uma Dinâmica. O limite edita-se em MB e segue em bytes. */
@Component({
  selector: 'app-procedimento-config-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, SeletorComponent, ReactiveFormsModule],
  templateUrl: './procedimento-config-modal.component.html',
  styleUrl: './configuracao-formulario.css',
})
export class ProcedimentoConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  /** Ex.: "PDM — Alteração". */
  readonly dinamica = input.required<string>();
  /** Tipos de Procedimento ativos; o da linha em edição entra mesmo que esteja inativo. */
  readonly tiposProcedimento = input.required<readonly TipoProcedimentoConfig[]>();
  /** Nulo: criação. */
  readonly procedimento = input<ProcedimentoConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<ValorProcedimentoConfig>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-procedimento-${++proximoId}`;
  readonly opcoesSerie: readonly OpcaoSeletor[] = (Object.keys(ROTULOS_SERIE_DR) as SerieDr[]).map((valor) => ({
    valor,
    rotulo: ROTULOS_SERIE_DR[valor],
  }));
  readonly avisoSemFormulario = AVISO_SEM_FORMULARIO;
  readonly titulo = computed(() => (this.procedimento() ? 'Editar Procedimento' : 'Adicionar Procedimento'));
  readonly emUso = computed(() => this.procedimento()?.emUso ?? false);
  readonly ajudaTipo = computed(() => (this.emUso() ? AJUDA_TIPO_PROCEDIMENTO_EM_USO : null));
  readonly opcoesTipo = computed<OpcaoSeletor[]>(() => {
    const opcoes = this.tiposProcedimento().map((tipo) => ({ valor: String(tipo.id), rotulo: tipo.nome }));
    const atual = this.procedimento()?.tipoProcedimento;
    if (atual && !opcoes.some((opcao) => opcao.valor === String(atual.id))) {
      opcoes.push({ valor: String(atual.id), rotulo: `${atual.nome} (inativo)` });
    }
    return opcoes;
  });

  readonly tipoEscolhido = signal<string | null>(null);
  readonly semFormulario = computed(() => {
    const escolhido = this.tipoEscolhido();
    if (!escolhido) {
      return false;
    }
    const tipo = this.tiposProcedimento().find((t) => String(t.id) === escolhido);
    if (tipo) {
      return tipo.formulario === null;
    }
    const atual = this.procedimento();
    return atual !== null && String(atual.tipoProcedimento.id) === escolhido && atual.formulario === null;
  });

  readonly errosServidor = signal<Partial<Record<CampoProcedimento, string>>>({});
  readonly erroGeral = signal<ApiError | null>(null);
  private submetido = false;

  readonly formulario = this.formBuilder.group({
    tipoProcedimentoId: this.formBuilder.control<string | null>(null, Validators.required),
    ordem: this.formBuilder.control<string | null>('', [Validators.required, inteiroPositivo]),
    serieDr: this.formBuilder.control<string | null>(null, Validators.required),
    /** Em MB. */
    tamanhoMaximoProcesso: this.formBuilder.control<string | null>('', inteiroPositivo),
    obrigatorio: this.formBuilder.nonNullable.control(true),
    repetivel: this.formBuilder.nonNullable.control(false),
    atoPontual: this.formBuilder.nonNullable.control(false),
    visivelNaDinamica: this.formBuilder.nonNullable.control(false),
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const procedimento = this.procedimento();
        untracked(() => this.preparar(procedimento));
      }
    });
    effect(() => {
      const erro = this.erroServidor();
      untracked(() => {
        const { porCampo, geral } = separarErroServidor(erro, CAMPOS);
        this.errosServidor.set(porCampo);
        this.erroGeral.set(geral);
      });
    });
    for (const campo of CAMPOS) {
      this.formulario.get(campo)!.valueChanges.subscribe(() => {
        if (this.errosServidor()[campo]) {
          this.errosServidor.update((erros) => ({ ...erros, [campo]: undefined }));
        }
      });
    }
    this.formulario.controls.tipoProcedimentoId.valueChanges.subscribe((valor) => this.tipoEscolhido.set(valor));
  }

  erroDoCampo(campo: CampoProcedimento): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo = this.formulario.controls[campo];
    if (!controlo.errors || !(controlo.dirty || controlo.touched || this.submetido)) {
      return null;
    }
    if (controlo.errors['required']) {
      return {
        tipoProcedimentoId: 'Escolha o tipo de Procedimento.',
        ordem: 'Indique a ordem.',
        serieDr: 'Escolha a série do DR.',
      }[campo as 'tipoProcedimentoId' | 'ordem' | 'serieDr'];
    }
    if (controlo.errors['inteiroPositivo']) {
      return campo === 'ordem'
        ? 'Use um número inteiro igual ou superior a 1.'
        : 'Use um número inteiro de MB igual ou superior a 1, ou deixe vazio.';
    }
    return null;
  }

  submeter(): void {
    this.submetido = true;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.aGravar()) {
      return;
    }
    const valor = this.formulario.getRawValue();
    const megabytes = numeroOuNulo(valor.tamanhoMaximoProcesso);
    this.guardar.emit({
      tipoProcedimentoId: Number(valor.tipoProcedimentoId),
      ordem: Number(valor.ordem),
      serieDr: valor.serieDr as SerieDr,
      tamanhoMaximoProcesso: megabytes === null ? null : megabytes * BYTES_POR_MB,
      obrigatorio: valor.obrigatorio,
      repetivel: valor.repetivel,
      atoPontual: valor.atoPontual,
      visivelNaDinamica: valor.visivelNaDinamica,
    });
  }

  private preparar(procedimento: ProcedimentoConfig | null): void {
    this.submetido = false;
    this.errosServidor.set({});
    this.erroGeral.set(null);
    const bytes = procedimento?.tamanhoMaximoProcesso ?? null;
    this.formulario.reset({
      tipoProcedimentoId: procedimento ? String(procedimento.tipoProcedimento.id) : null,
      ordem: procedimento ? String(procedimento.ordem) : '',
      serieDr: procedimento?.serieDr ?? null,
      tamanhoMaximoProcesso: bytes === null ? '' : String(Math.round(bytes / BYTES_POR_MB)),
      obrigatorio: procedimento?.obrigatorio ?? true,
      repetivel: procedimento?.repetivel ?? false,
      atoPontual: procedimento?.atoPontual ?? false,
      visivelNaDinamica: procedimento?.visivelNaDinamica ?? false,
    });
    const tipo = this.formulario.controls.tipoProcedimentoId;
    if (procedimento?.emUso) {
      tipo.disable();
    } else {
      tipo.enable();
    }
    this.tipoEscolhido.set(tipo.value);
  }
}
