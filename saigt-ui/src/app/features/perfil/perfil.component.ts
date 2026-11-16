import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { map, startWith } from 'rxjs';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { CartaoIdentidadeComponent } from '../../shared/components/cartao-identidade.component';
import { AlertaComponent } from '../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../shared/ui/botao/botao.component';
import { CampoTextoComponent } from '../../shared/ui/campo-texto/campo-texto.component';
import { CartaoComponent } from '../../shared/ui/cartao/cartao.component';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { UtilizadorService } from '../../services/utilizador.service';
import { AtualizarPerfilRequest, PERFIL_INTERNO_LABEL, UtilizadorMe } from '../../models/utilizador.model';

const DURACAO_ALERTA_SUCESSO_MS = 4000;
const EMAIL_VALIDO = /^[^\s@]+@[^\s@.]+\.[^\s@]+$/;
const TELEFONE_VALIDO = /^(?=.*\d)[\d +().-]+$/;
const MAXIMO_EMAIL = 200;
const MAXIMO_TELEFONE = 30;
const MAXIMO_MORADA = 300;
type CampoEditavel = 'email' | 'telefone' | 'morada';
const CAMPOS_EDITAVEIS: readonly CampoEditavel[] = ['email', 'telefone', 'morada'];

/**
 * Perfil do Utilizador, a partir do `GET /api/v1/utilizadores/me` (o token não traz o responsável da entidade nem o
 * telefone e a morada dos DGT). INCM: tudo só de leitura. AD_DGT: o nome é só de leitura; o email, o telefone e a
 * morada editam-se e gravam-se com `PATCH`, que só envia os campos alterados.
 */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [AppShellComponent, AlertaComponent, BotaoComponent, CampoTextoComponent, CartaoComponent, CartaoIdentidadeComponent, ReactiveFormsModule],
  templateUrl: './perfil.component.html',
  styleUrl: './perfil.component.css',
})
export class PerfilComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly utilizadorService = inject(UtilizadorService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly perfil = signal<UtilizadorMe | null>(null);
  readonly mensagemErro = signal<string | null>(null);
  readonly mensagemSucesso = signal<string | null>(null);
  readonly aGravar = signal(false);
  private readonly errosServidor = signal<Partial<Record<CampoEditavel, string>>>({});
  private temporizadorSucesso: ReturnType<typeof setTimeout> | undefined;

  readonly nomeControlo = new FormControl({ value: '', disabled: true }, { nonNullable: true });
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.maxLength(MAXIMO_EMAIL), Validators.pattern(EMAIL_VALIDO)]],
    telefone: ['', [Validators.maxLength(MAXIMO_TELEFONE), Validators.pattern(TELEFONE_VALIDO)]],
    morada: ['', [Validators.maxLength(MAXIMO_MORADA)]],
  });
  readonly maximoEmail = MAXIMO_EMAIL;
  readonly maximoTelefone = MAXIMO_TELEFONE;
  readonly maximoMorada = MAXIMO_MORADA;

  /** Muda a cada tecla, para os erros e o «Guardar» se recalcularem. */
  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(null), map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  private readonly estadoForm = toSignal(this.form.statusChanges.pipe(startWith(this.form.status)), {
    initialValue: this.form.status,
  });

  /** A origem vem do perfil carregado; enquanto carrega, a do token decide a variante. */
  readonly origem = computed(() => this.perfil()?.origemAutenticacao ?? this.authService.origem());

  readonly alteracoes = computed<AtualizarPerfilRequest>(() => {
    const perfil = this.perfil();
    const valores = this.valores();
    const alteracoes: AtualizarPerfilRequest = {};
    if (!perfil) {
      return alteracoes;
    }
    for (const campo of CAMPOS_EDITAVEIS) {
      if (valores[campo].trim() !== (perfil[campo] ?? '')) {
        alteracoes[campo] = valores[campo].trim();
      }
    }
    return alteracoes;
  });
  readonly temAlteracoes = computed(() => Object.keys(this.alteracoes()).length > 0);
  readonly podeGuardar = computed(() => this.temAlteracoes() && this.estadoForm() === 'VALID' && !this.aGravar());

  readonly erroEmail = computed(() => this.erroDoCampo('email', 'Indique um email válido (ex.: nome@dgterritorio.gov.pt).'));
  readonly erroTelefone = computed(() => this.erroDoCampo('telefone', 'Use só algarismos, espaços e os sinais + ( ) - .'));
  readonly erroMorada = computed(() => this.erroDoCampo('morada', ''));

  readonly perfilInternoLabel = computed(() => {
    const perfil = this.perfil()?.perfilInterno ?? this.authService.perfilInterno();
    return perfil ? PERFIL_INTERNO_LABEL[perfil] : '—';
  });
  readonly estadoCartao = computed(() =>
    this.origem() === 'AD_DGT' ? 'Autenticado via DGT — Active Directory' : 'Autenticado via INCM',
  );
  readonly subtituloPagina = computed(() => {
    switch (this.origem()) {
      case 'AD_DGT':
        return 'Sessão iniciada via DGT Active Directory · Informação editável';
      case 'INCM':
        return 'Sessão iniciada via INCM · Informação não editável';
      default:
        return 'Sessão sem origem reconhecida.';
    }
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.temporizadorSucesso));
    for (const campo of CAMPOS_EDITAVEIS) {
      this.form.controls[campo].valueChanges
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.limparErroServidor(campo));
    }
    this.utilizadorService
      .obterMe()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => this.aplicarPerfil(resposta),
        error: () => this.mensagemErro.set('Não foi possível carregar os seus dados.'),
      });
  }

  /** Um erro do servidor num campo desaparece quando o valor desse campo muda. */
  private limparErroServidor(campo: CampoEditavel): void {
    if (this.errosServidor()[campo]) {
      this.errosServidor.update((erros) => ({ ...erros, [campo]: undefined }));
    }
  }

  guardar(): void {
    if (!this.podeGuardar()) {
      return;
    }
    this.aGravar.set(true);
    this.mensagemErro.set(null);
    this.limparSucesso();
    this.utilizadorService
      .atualizarMe(this.alteracoes())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resposta) => {
          this.aplicarPerfil(resposta);
          this.mostrarSucesso('Os seus dados foram atualizados.');
        },
        error: (erro: unknown) => this.tratarErro(erro),
      });
  }

  /** O `errorEnvelopeInterceptor` entrega um `ApiError`; os 400 e 409 de um campo ficam junto dele. */
  private tratarErro(erro: unknown): void {
    this.aGravar.set(false);
    const apiErro = erro instanceof ApiError ? erro : null;
    const camposEditaveis = (apiErro?.campos ?? []).filter((c): c is { path: CampoEditavel; erro: string } =>
      CAMPOS_EDITAVEIS.includes(c.path as CampoEditavel),
    );
    if (camposEditaveis.length > 0) {
      const erros: Partial<Record<CampoEditavel, string>> = {};
      for (const campo of camposEditaveis) {
        erros[campo.path] = campo.erro;
        this.form.controls[campo.path].markAsTouched();
      }
      this.errosServidor.set(erros);
      return;
    }
    this.mensagemErro.set(apiErro?.mensagem || 'Não foi possível gravar as alterações. Tente novamente.');
  }

  private erroDoCampo(campo: CampoEditavel, formatoInvalido: string): string | null {
    this.valores();
    this.estadoForm();
    const doServidor = this.errosServidor()[campo];
    if (doServidor) {
      return doServidor;
    }
    const controlo: AbstractControl = this.form.controls[campo];
    if (!controlo.errors || !(controlo.dirty || controlo.touched)) {
      return null;
    }
    if (controlo.errors['maxlength']) {
      return `Tem de ter no máximo ${controlo.errors['maxlength'].requiredLength} caracteres.`;
    }
    return controlo.errors['pattern'] ? formatoInvalido : null;
  }

  private aplicarPerfil(resposta: UtilizadorMe): void {
    this.perfil.set(resposta);
    this.nomeControlo.setValue(resposta.nome ?? '');
    this.form.reset({ email: resposta.email ?? '', telefone: resposta.telefone ?? '', morada: resposta.morada ?? '' });
    this.errosServidor.set({});
    this.aGravar.set(false);
  }

  private mostrarSucesso(mensagem: string): void {
    this.mensagemSucesso.set(mensagem);
    this.temporizadorSucesso = setTimeout(() => this.mensagemSucesso.set(null), DURACAO_ALERTA_SUCESSO_MS);
  }

  private limparSucesso(): void {
    clearTimeout(this.temporizadorSucesso);
    this.mensagemSucesso.set(null);
  }
}
