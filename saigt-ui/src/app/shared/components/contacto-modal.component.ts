import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../../core/models/error-response.model';
import { AtualizarContactoRequest, Contacto, ContactoRequest } from '../../models/entidade.model';
import { EntidadeService } from '../../services/entidade.service';
import { AlertaComponent } from '../ui/alerta/alerta.component';
import { BotaoComponent } from '../ui/botao/botao.component';
import { CampoTextoComponent } from '../ui/campo-texto/campo-texto.component';
import { ModalComponent } from '../ui/modal/modal.component';

type CampoContacto = 'email' | 'descricao' | 'telefone';

const CAMPOS: readonly CampoContacto[] = ['email', 'descricao', 'telefone'];

const MENSAGENS: Record<CampoContacto, string> = {
  email: 'Indique um e-mail válido.',
  descricao: 'Tem de ter no máximo 200 caracteres.',
  telefone: 'O telefone tem 9 dígitos.',
};

function naoEmBranco(controlo: AbstractControl<string | null>): ValidationErrors | null {
  return (controlo.value ?? '').trim() ? null : { required: true };
}

interface ValoresContacto {
  email: string;
  descricao: string;
  telefone: string;
  principal: boolean;
}

let proximoId = 0;

/** Criar (sem `contacto`) ou editar um contacto institucional. Os contactos não se apagam: desativam-se. */
@Component({
  selector: 'app-contacto-modal',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, ModalComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [titulo]="titulo()" largura="md" [aberto]="aberto()" [fecharComFundo]="!aGravar()" (fechar)="pedirFecho()">
      @if (erroGeral(); as mensagem) {
        <app-alerta class="alerta-modal" tipo="erro">{{ mensagem }}</app-alerta>
      }
      <form class="formulario" [id]="idFormulario" [formGroup]="formulario" (ngSubmit)="gravar()">
        <app-campo-texto
          data-campo="email"
          formControlName="email"
          rotulo="E-mail"
          tipo="email"
          placeholder="geral@entidade.pt"
          [compacto]="true"
          [obrigatorio]="true"
          [maxlength]="200"
          [erro]="erros().email"
        />
        <app-campo-texto
          data-campo="descricao"
          formControlName="descricao"
          rotulo="Descrição"
          placeholder="Departamento de Urbanismo"
          [compacto]="true"
          [maxlength]="200"
          [erro]="erros().descricao"
        />
        <app-campo-texto
          data-campo="telefone"
          formControlName="telefone"
          rotulo="Telefone"
          tipo="tel"
          placeholder="912345678"
          [compacto]="true"
          [maxlength]="9"
          [erro]="erros().telefone"
          (input)="restringirTelefone($event)"
        />
        <div class="caixa-principal">
          <input type="checkbox" formControlName="principal" [id]="idPrincipal" [attr.aria-describedby]="idAjudaPrincipal" />
          <label [for]="idPrincipal">Contacto principal</label>
          <p class="ajuda" [id]="idAjudaPrincipal">Recebe a cópia por e-mail das notificações. Só pode haver um principal ativo.</p>
        </div>
      </form>
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aGravar()" (click)="pedirFecho()">
          Cancelar
        </button>
        <button type="submit" app-botao tamanho="pequeno" class="gravar" [attr.form]="idFormulario" [desativado]="!podeGravar()">
          {{ aGravar() ? 'A guardar…' : contacto() ? 'Guardar' : 'Adicionar' }}
        </button>
      </div>
    </app-modal>
  `,
  styles: `
    .formulario {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-4);
    }

    .alerta-modal {
      margin-bottom: var(--espaco-4);
    }

    .caixa-principal {
      display: grid;
      grid-template-columns: auto 1fr;
      column-gap: var(--espaco-2);
      align-items: center;
    }

    .caixa-principal input {
      width: 16px;
      height: 16px;
      margin: 0;
      accent-color: var(--cor-primaria);
    }

    .caixa-principal input:focus-visible {
      outline: var(--anel-foco);
      outline-offset: var(--anel-foco-afastamento);
    }

    .caixa-principal label {
      font-size: var(--texto-sm);
      font-weight: var(--peso-semi);
      line-height: var(--texto-sm-linha);
      color: var(--cor-texto-cartao);
    }

    .ajuda {
      grid-column: 2;
      margin: 0;
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      color: var(--cor-texto-secundario);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class ContactoModalComponent {
  private readonly entidadeService = inject(EntidadeService);

  readonly aberto = input(false);
  readonly entidadeId = input<number | null>(null);
  /** `null` cria um contacto novo. */
  readonly contacto = input<Contacto | null>(null);
  readonly fechar = output<void>();
  readonly gravado = output<Contacto>();

  readonly idFormulario = `contacto-formulario-${++proximoId}`;
  readonly idPrincipal = `${this.idFormulario}-principal`;
  readonly idAjudaPrincipal = `${this.idPrincipal}-ajuda`;

  readonly aGravar = signal(false);
  readonly erroGeral = signal<string | null>(null);
  private readonly errosApi = signal<Partial<Record<CampoContacto, string>>>({});
  private readonly originais = signal<ValoresContacto>({ email: '', descricao: '', telefone: '', principal: false });

  readonly formulario = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [naoEmBranco, Validators.email, Validators.maxLength(200)] }),
    descricao: new FormControl('', { nonNullable: true, validators: Validators.maxLength(200) }),
    telefone: new FormControl('', { nonNullable: true, validators: Validators.pattern(/^\d{9}$/) }),
    principal: new FormControl(false, { nonNullable: true }),
  });

  private readonly estadoFormulario = toSignal(this.formulario.events, { initialValue: null });

  readonly titulo = computed(() => (this.contacto() ? 'Editar contacto' : 'Adicionar contacto'));

  readonly erros = computed<Record<CampoContacto, string | null>>(() => {
    this.estadoFormulario();
    const daApi = this.errosApi();
    const erros = {} as Record<CampoContacto, string | null>;
    for (const campo of CAMPOS) {
      const controlo = this.formulario.controls[campo];
      const mostrar = controlo.invalid && (controlo.touched || controlo.dirty);
      erros[campo] = mostrar ? MENSAGENS[campo] : (daApi[campo] ?? null);
    }
    return erros;
  });

  /** Ao editar, só o que mudou; a descrição e o telefone vazios limpam-nos. */
  readonly alteracoes = computed<AtualizarContactoRequest>(() => {
    this.estadoFormulario();
    const atuais = this.valoresAtuais();
    const originais = this.originais();
    const alteracoes: AtualizarContactoRequest = {};
    if (atuais.email !== originais.email) alteracoes.email = atuais.email;
    if (atuais.descricao !== originais.descricao) alteracoes.descricao = atuais.descricao;
    if (atuais.telefone !== originais.telefone) alteracoes.telefone = atuais.telefone;
    if (atuais.principal !== originais.principal) alteracoes.principal = atuais.principal;
    return alteracoes;
  });

  readonly podeGravar = computed(() => {
    this.estadoFormulario();
    const semAlteracoes = !!this.contacto() && Object.keys(this.alteracoes()).length === 0;
    return this.formulario.valid && !this.aGravar() && !semAlteracoes;
  });

  constructor() {
    effect(() => {
      if (this.aberto()) {
        untracked(() => this.preparar());
      }
    });
    for (const campo of CAMPOS) {
      (this.formulario.controls[campo] as AbstractControl).valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (this.errosApi()[campo]) {
          this.errosApi.update((erros) => {
            const restantes = { ...erros };
            delete restantes[campo];
            return restantes;
          });
        }
      });
    }
  }

  pedirFecho(): void {
    if (!this.aGravar()) {
      this.fechar.emit();
    }
  }

  /** Só dígitos, até 9: cobre a escrita e a colagem. */
  restringirTelefone(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const limpo = campo.value.replace(/\D/g, '').slice(0, 9);
    this.formulario.controls.telefone.setValue(limpo);
    // O campo partilhado só reescreve o <input> quando o valor muda; se o limpo for igual ao anterior, o lixo ficaria visível.
    campo.value = limpo;
  }

  gravar(): void {
    const entidadeId = this.entidadeId();
    if (entidadeId === null || !this.podeGravar()) {
      return;
    }
    const contacto = this.contacto();
    const pedido = contacto
      ? this.entidadeService.editarContacto(entidadeId, contacto.id, this.alteracoes())
      : this.entidadeService.criarContacto(entidadeId, this.pedidoCriar());
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(true);
    pedido.subscribe({
      next: (gravado) => {
        this.aGravar.set(false);
        this.gravado.emit(gravado);
      },
      error: (erro: ApiError) => {
        this.aGravar.set(false);
        this.tratarErro(erro);
      },
    });
  }

  private pedidoCriar(): ContactoRequest {
    const valores = this.valoresAtuais();
    return {
      email: valores.email,
      descricao: valores.descricao || null,
      telefone: valores.telefone || null,
      principal: valores.principal,
    };
  }

  private valoresAtuais(): ValoresContacto {
    const valores = this.formulario.getRawValue();
    return {
      email: valores.email.trim(),
      descricao: valores.descricao.trim(),
      telefone: valores.telefone.trim(),
      principal: valores.principal,
    };
  }

  private tratarErro(erro: ApiError): void {
    const porCampo: Partial<Record<CampoContacto, string>> = {};
    for (const campo of erro.codigo === 'VALIDACAO' ? (erro.campos ?? []) : []) {
      if ((CAMPOS as readonly string[]).includes(campo.path)) {
        porCampo[campo.path as CampoContacto] = campo.erro;
      }
    }
    if (Object.keys(porCampo).length > 0) {
      this.errosApi.set(porCampo);
    } else {
      this.erroGeral.set(erro.mensagem);
    }
  }

  private preparar(): void {
    const contacto = this.contacto();
    const valores: ValoresContacto = {
      email: contacto?.email ?? '',
      descricao: contacto?.descricao ?? '',
      telefone: contacto?.telefone ?? '',
      principal: contacto?.principal ?? false,
    };
    this.originais.set(valores);
    this.formulario.reset(valores);
    this.errosApi.set({});
    this.erroGeral.set(null);
    this.aGravar.set(false);
  }
}
