import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AssociacaoInstrumentos,
  DinamicaConfig,
  ROTULOS_ASSOCIACAO,
  TipoDinamicaConfig,
} from '../../../models/configuracao.model';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../../../shared/ui/seletor/seletor.component';

export interface ValorDinamicaConfig {
  tipoDinamicaId: number;
  associacaoInstrumentos: AssociacaoInstrumentos;
}

type CampoDinamica = 'tipoDinamicaId' | 'associacaoInstrumentos';

const CAMPOS: readonly CampoDinamica[] = ['tipoDinamicaId', 'associacaoInstrumentos'];

export const AJUDA_TIPO_EM_USO = 'Já há Dinâmicas iniciadas com esta configuração: o tipo não pode mudar.';

let proximoId = 0;

/**
 * Cria ou edita a configuração de uma Dinâmica num tipo de Instrumento. Sem "Ordem": o contrato não a tem
 * (a coluna foi removida do DB-PROC-004) e a lista segue a ordem do catálogo de tipos de Dinâmica.
 */
@Component({
  selector: 'app-dinamica-config-modal',
  imports: [AlertaComponent, BotaoComponent, ModalComponent, SeletorComponent, ReactiveFormsModule],
  templateUrl: './dinamica-config-modal.component.html',
  styles: `
    .formulario {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-4);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class DinamicaConfigModalComponent {
  private readonly formBuilder = inject(FormBuilder);

  readonly aberto = input(false);
  /** Ex.: "PROT — Programa Regional de Ordenamento do Território". */
  readonly tipoInstrumento = input.required<string>();
  /** Tipos de Dinâmica ativos; o da linha em edição entra mesmo que esteja inativo. */
  readonly tiposDinamica = input.required<readonly TipoDinamicaConfig[]>();
  /** Nula: criação. */
  readonly dinamica = input<DinamicaConfig | null>(null);
  readonly aGravar = input(false);
  readonly erroServidor = input<ApiError | null>(null);
  readonly guardar = output<ValorDinamicaConfig>();
  readonly fechar = output<void>();

  readonly idFormulario = `formulario-dinamica-${++proximoId}`;
  readonly opcoesAssociacao: readonly OpcaoSeletor[] = (Object.keys(ROTULOS_ASSOCIACAO) as AssociacaoInstrumentos[]).map(
    (valor) => ({ valor, rotulo: ROTULOS_ASSOCIACAO[valor] }),
  );
  readonly titulo = computed(() => (this.dinamica() ? 'Editar Dinâmica' : 'Adicionar Dinâmica'));
  readonly emUso = computed(() => this.dinamica()?.emUso ?? false);
  readonly opcoesTipo = computed<OpcaoSeletor[]>(() => {
    const opcoes = this.tiposDinamica().map((tipo) => ({ valor: String(tipo.id), rotulo: tipo.nome }));
    const atual = this.dinamica()?.tipoDinamica;
    if (atual && !opcoes.some((opcao) => opcao.valor === String(atual.id))) {
      opcoes.push({ valor: String(atual.id), rotulo: `${atual.nome} (inativo)` });
    }
    return opcoes;
  });
  readonly ajudaTipo = computed(() => (this.emUso() ? AJUDA_TIPO_EM_USO : null));

  readonly errosServidor = signal<Partial<Record<CampoDinamica, string>>>({});
  readonly erroGeral = signal<ApiError | null>(null);
  private submetido = false;

  readonly formulario = this.formBuilder.group({
    tipoDinamicaId: this.formBuilder.control<string | null>(null, Validators.required),
    associacaoInstrumentos: this.formBuilder.control<string | null>('UM', Validators.required),
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        const dinamica = this.dinamica();
        untracked(() => this.preparar(dinamica));
      }
    });
    effect(() => {
      const erro = this.erroServidor();
      untracked(() => this.mostrarErroServidor(erro));
    });
    for (const campo of CAMPOS) {
      this.formulario.controls[campo].valueChanges.subscribe(() => {
        if (this.errosServidor()[campo]) {
          this.errosServidor.update((erros) => ({ ...erros, [campo]: undefined }));
        }
      });
    }
  }

  erroDoCampo(campo: CampoDinamica): string | null {
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo = this.formulario.controls[campo];
    if (!controlo.errors?.['required'] || !(controlo.touched || this.submetido)) {
      return null;
    }
    return campo === 'tipoDinamicaId' ? 'Escolha o tipo de Dinâmica.' : 'Escolha a associação de Instrumentos.';
  }

  submeter(): void {
    this.submetido = true;
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.aGravar()) {
      return;
    }
    const valor = this.formulario.getRawValue();
    this.guardar.emit({
      tipoDinamicaId: Number(valor.tipoDinamicaId),
      associacaoInstrumentos: valor.associacaoInstrumentos as AssociacaoInstrumentos,
    });
  }

  private preparar(dinamica: DinamicaConfig | null): void {
    this.submetido = false;
    this.errosServidor.set({});
    this.erroGeral.set(null);
    this.formulario.reset({
      tipoDinamicaId: dinamica ? String(dinamica.tipoDinamica.id) : null,
      associacaoInstrumentos: dinamica?.associacaoInstrumentos ?? 'UM',
    });
    const tipo = this.formulario.controls.tipoDinamicaId;
    if (dinamica?.emUso) {
      tipo.disable();
    } else {
      tipo.enable();
    }
  }

  private mostrarErroServidor(erro: ApiError | null): void {
    if (!erro) {
      this.errosServidor.set({});
      this.erroGeral.set(null);
      return;
    }
    const porCampo: Partial<Record<CampoDinamica, string>> = {};
    for (const campo of erro.campos ?? []) {
      if ((CAMPOS as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoDinamica] = campo.erro;
      }
    }
    this.errosServidor.set(porCampo);
    this.erroGeral.set(Object.keys(porCampo).length > 0 ? null : erro);
  }
}
