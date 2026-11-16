import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../../core/auth/auth.service';
import { ErroAutenticacao } from '../../core/auth/auth.models';
import { MENSAGEM_ESCOLHA_EXPIRADA, MENSAGEM_SERVICO_INDISPONIVEL } from './mensagens-autenticacao';
import { AlertaComponent } from '../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../shared/ui/campo-texto/campo-texto.component';

const MENSAGEM_GENERICA = 'Não foi possível entrar. Tente novamente mais tarde.';

/** Um só ecrã para os dois mecanismos: INCM (email) e, com a caixa marcada, DGT (nome de autenticação no AD). */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, AlertaComponent, BotaoComponent, CampoTextoComponent],
  templateUrl: './login.component.html',
  styleUrls: ['./autenticacao.css', './login.component.css'],
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', Validators.required],
    password: ['', Validators.required],
    utilizadorInternoDgt: [false],
  });

  readonly mensagemErro = signal<string | null>(null);
  readonly aSubmeter = signal(false);
  readonly mostrarSenha = signal(false);

  constructor() {
    this.title.setTitle('Autenticação · SAIGT');
    const erro = this.route.snapshot.queryParamMap.get('erro');
    if (erro === 'SESSAO_EXPIRADA') {
      this.mensagemErro.set(MENSAGEM_ESCOLHA_EXPIRADA);
    } else if (erro === 'PERFIL_ALTERADO') {
      // Alterar o próprio perfil termina a sessão, em vez de recarregar com claims desatualizados.
      this.mensagemErro.set('O seu perfil foi alterado. Autentique-se novamente para continuar.');
    }
  }

  campoInvalido(nome: 'email' | 'password'): boolean {
    const controlo = this.form.controls[nome];
    return controlo.invalid && (controlo.dirty || controlo.touched);
  }

  alternarVisibilidadeSenha(): void {
    this.mostrarSenha.set(!this.mostrarSenha());
  }

  submeter(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.mensagemErro.set(null);
    this.aSubmeter.set(true);
    const { email, password, utilizadorInternoDgt } = this.form.getRawValue();

    this.authService.login({ utilizador: email, password, utilizadorInternoDgt }).subscribe({
      next: (resultado) => {
        this.aSubmeter.set(false);
        if (resultado === 'ESCOLHA_ENTIDADE') {
          this.router.navigate(['/login/escolha-entidade'], { queryParams: this.route.snapshot.queryParams });
        } else {
          this.navegarParaDestino();
        }
      },
      error: (erro: unknown) => {
        this.aSubmeter.set(false);
        this.mensagemErro.set(mensagemParaErro(erro));
      },
    });
  }

  private navegarParaDestino(): void {
    this.router.navigateByUrl('/');
  }
}

/** O motivo vem do servidor de autorização (`error_description` do `invalid_grant`): credenciais, sem entidade INCM. */
function mensagemParaErro(erro: unknown): string {
  if (!(erro instanceof ErroAutenticacao)) {
    return MENSAGEM_GENERICA;
  }
  switch (erro.motivo) {
    case 'RECUSADO':
      return erro.descricao ?? MENSAGEM_GENERICA;
    case 'INDISPONIVEL':
      return MENSAGEM_SERVICO_INDISPONIVEL;
    default:
      return MENSAGEM_GENERICA;
  }
}
