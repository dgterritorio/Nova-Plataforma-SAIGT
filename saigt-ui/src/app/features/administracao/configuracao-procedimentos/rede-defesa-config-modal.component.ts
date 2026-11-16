import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  PADRAO_CODIGO,
  RedeDefesaConfig,
  RedeDefesaConfigRequest,
  TAMANHO_MAXIMO_CODIGO_ATO_REDE,
  TAMANHO_MAXIMO_NOME,
} from '../../../models/configuracao.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../../shared/ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { separarErroServidor } from './erros-formulario';

type CampoRede = 'codigo' | 'nome' | 'aplicaPra' | 'aplicaPsa' | 'publicavel';

const CAMPOS: readonly CampoRede[] = ['codigo', 'nome', 'aplicaPra', 'aplicaPsa', 'publicavel'];

let proximoId = 0;

/** Cria ou edita uma rede de defesa do SGIFR. A tabela não tem estado ativo: não há desativar. */
@Component({
  selector: 'app-rede-defesa-config-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, ReactiveFormsModule],
  templateUrl: './rede-defesa-config-modal.component.html',
  styleUrl: './configuracao-formulario.css',
})
export class RedeDefesaConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  /** Nula: criação. */
  readonly rede = input<RedeDefesaConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<RedeDefesaConfigRequest>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-rede-${++proximoId}`;
  readonly tamanhoMaximoCodigo = TAMANHO_MAXIMO_CODIGO_ATO_REDE;
  readonly tamanhoMaximoNome = TAMANHO_MAXIMO_NOME;
  readonly titulo = computed(() => (this.rede() ? 'Editar rede' : 'Adicionar rede'));

  readonly errosServidor = signal<Partial<Record<CampoRede, string>>>({});
  readonly erroGeral = signal<ApiError | null>(null);
  private submetido = false;

  readonly formulario = this.formBuilder.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(PADRAO_CODIGO), Validators.maxLength(TAMANHO_MAXIMO_CODIGO_ATO_REDE)]],
    nome: ['', [Validators.required, Validators.maxLength(TAMANHO_MAXIMO_NOME)]],
    aplicaPra: [false],
    aplicaPsa: [false],
    publicavel: [false],
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const rede = this.rede();
        untracked(() => this.preparar(rede));
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
  }

  erroDoCampo(campo: CampoRede): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo = this.formulario.controls[campo];
    if (!controlo.errors || !(controlo.dirty || controlo.touched || this.submetido)) {
      return null;
    }
    if (controlo.errors['required']) {
      return campo === 'codigo' ? 'Indique o código.' : 'Indique o nome.';
    }
    if (controlo.errors['pattern']) {
      return 'Use só letras maiúsculas sem acentos, algarismos e _ (sem espaços).';
    }
    if (controlo.errors['maxlength']) {
      return `Até ${controlo.errors['maxlength'].requiredLength} caracteres.`;
    }
    return null;
  }

  /** Erros do servidor nas caixas (raros): aparecem no alerta geral, que é o único sítio onde se veem. */
  errosDasCaixas(): string[] {
    const erros = this.errosServidor();
    return (['aplicaPra', 'aplicaPsa', 'publicavel'] as const).map((campo) => erros[campo]).filter((erro): erro is string => !!erro);
  }

  submeter(): void {
    this.submetido = true;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.aGravar()) {
      return;
    }
    const valor = this.formulario.getRawValue();
    this.guardar.emit({ ...valor, nome: valor.nome.trim() });
  }

  private preparar(rede: RedeDefesaConfig | null): void {
    this.submetido = false;
    this.errosServidor.set({});
    this.erroGeral.set(null);
    this.formulario.reset({
      codigo: rede?.codigo ?? '',
      nome: rede?.nome ?? '',
      aplicaPra: rede?.aplicaPra ?? false,
      aplicaPsa: rede?.aplicaPsa ?? false,
      publicavel: rede?.publicavel ?? false,
    });
  }
}
