import { Component, OnChanges, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { PERFIL_INTERNO_LABEL, PERFIS_INTERNOS, PerfilInterno } from '../../models/utilizador.model';
import { BotaoComponent } from '../ui/botao/botao.component';
import { ModalComponent } from '../ui/modal/modal.component';
import { OpcaoSeletor, SeletorComponent } from '../ui/seletor/seletor.component';

let proximoId = 0;

/** Sem motivo. Confirmar fica desativado com o perfil igual ao atual. O `nome` diz de quem é o perfil. */
@Component({
  selector: 'app-alterar-perfil-modal',
  imports: [ReactiveFormsModule, BotaoComponent, ModalComponent, SeletorComponent],
  template: `
    <app-modal titulo="Alterar perfil" largura="sm" [aberto]="aberto()" (fechar)="cancelar.emit()">
      <form class="formulario" [id]="idFormulario" [formGroup]="form" (ngSubmit)="confirmarClick()">
        @if (nome()) {
          <p class="alvo">{{ nome() }}</p>
        }
        <app-seletor
          formControlName="perfilInterno"
          rotulo="Perfil"
          ajuda="As sessões ativas desta pessoa terminam e o novo perfil vale no próximo login."
          [opcoes]="opcoesPerfil"
          [compacto]="true"
          [obrigatorio]="true"
        />
      </form>
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" class="botao-cancelar" (click)="cancelar.emit()">
          Cancelar
        </button>
        <button type="submit" app-botao tamanho="pequeno" [attr.form]="idFormulario" [desativado]="perfilInalterado()">
          Alterar perfil
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

    .alvo {
      margin: 0;
      font-size: var(--texto-base);
      font-weight: var(--peso-semi);
      line-height: var(--texto-base-linha);
      color: var(--cor-texto-seccao);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class AlterarPerfilModalComponent implements OnChanges {
  readonly aberto = input(false);
  readonly perfilAtual = input<PerfilInterno | null>(null);
  readonly nome = input<string | null>(null);
  readonly confirmar = output<{ perfilInterno: PerfilInterno }>();
  readonly cancelar = output<void>();

  readonly opcoesPerfil: OpcaoSeletor[] = PERFIS_INTERNOS.map((perfil) => ({ valor: perfil, rotulo: PERFIL_INTERNO_LABEL[perfil] }));
  readonly idFormulario = `alterar-perfil-formulario-${++proximoId}`;

  private readonly formBuilder = inject(FormBuilder);
  readonly form = this.formBuilder.nonNullable.group({
    perfilInterno: PERFIS_INTERNOS[0] as PerfilInterno,
  });
  /** Em signal para o botão se atualizar também quando o valor muda por código. */
  private readonly perfilEscolhido = signal<PerfilInterno>(PERFIS_INTERNOS[0]);

  constructor() {
    this.form.controls.perfilInterno.valueChanges.pipe(takeUntilDestroyed()).subscribe((perfil) => this.perfilEscolhido.set(perfil));
  }

  ngOnChanges(): void {
    if (this.aberto()) {
      this.form.reset({ perfilInterno: this.perfilAtual() ?? PERFIS_INTERNOS[0] });
    }
  }

  perfilInalterado(): boolean {
    return this.perfilEscolhido() === this.perfilAtual();
  }

  confirmarClick(): void {
    if (this.perfilInalterado()) {
      return;
    }
    this.confirmar.emit(this.form.getRawValue());
  }
}
