import { Component, ElementRef, Injector, OnInit, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../../../core/auth/auth.service';
import { ErroAutenticacao } from '../../../core/auth/auth.models';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui/botao/botao.component';
import { MENSAGEM_SERVICO_INDISPONIVEL } from '../mensagens-autenticacao';

/**
 * Sempre apresentado no login INCM, com uma ou várias adesões. A escolha vai pelo `incmEnrollmentId`, nunca pelo NIPC.
 * Uma adesão recusada fica indisponível e pode escolher-se outra enquanto a pré-autenticação não expira.
 */
@Component({
  selector: 'app-escolha-entidade',
  standalone: true,
  imports: [AlertaComponent, BotaoComponent],
  templateUrl: './escolha-entidade.component.html',
  styleUrls: ['../autenticacao.css', './escolha-entidade.component.css'],
})
export class EscolhaEntidadeComponent implements OnInit {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  private readonly injector = inject(Injector);

  readonly aSubmeter = signal(false);
  readonly selecionada = signal<string | null>(null);
  readonly indisponiveis = signal<ReadonlySet<string>>(new Set());
  readonly mensagemErro = signal<string | null>(null);
  readonly totalEntidades = computed(() => this.authService.entidadesElegiveis()?.length ?? 0);

  private readonly alertaErro = viewChild('alertaErro', { read: ElementRef });

  constructor() {
    this.title.setTitle('Autenticação INCM · SAIGT');
  }

  ngOnInit(): void {
    if (!this.authService.emEscolhaDeEntidade()) {
      this.router.navigateByUrl('/login');
    }
  }

  selecionar(incmEnrollmentId: string): void {
    if (!this.indisponivel(incmEnrollmentId)) {
      this.selecionada.set(incmEnrollmentId);
    }
  }

  indisponivel(incmEnrollmentId: string): boolean {
    return this.indisponiveis().has(incmEnrollmentId);
  }

  confirmar(): void {
    const incmEnrollmentId = this.selecionada();
    if (incmEnrollmentId) {
      this.escolher(incmEnrollmentId);
    }
  }

  escolher(incmEnrollmentId: string): void {
    this.aSubmeter.set(true);
    this.mensagemErro.set(null);

    this.authService.escolherEntidade(incmEnrollmentId).subscribe({
      next: () => {
        this.aSubmeter.set(false);
        this.router.navigateByUrl('/');
      },
      error: (erro: unknown) => {
        this.aSubmeter.set(false);
        this.tratarErro(incmEnrollmentId, erro);
      },
    });
  }

  /** NIPC em blocos de 3 dígitos, ex.: "501234567" → "501 234 567". */
  formatarNipc(nipc: string): string {
    return nipc.match(/.{1,3}/g)?.join(' ') ?? nipc;
  }

  private tratarErro(incmEnrollmentId: string, erro: unknown): void {
    if (!(erro instanceof ErroAutenticacao) || erro.motivo === 'PRE_AUTENTICACAO_EXPIRADA') {
      this.router.navigate(['/login'], { queryParams: { erro: 'SESSAO_EXPIRADA' } });
      return;
    }
    if (erro.motivo === 'RECUSADO') {
      this.indisponiveis.update((atuais) => new Set([...atuais, incmEnrollmentId]));
      this.selecionada.set(null);
      this.mostrarErro(erro.descricao ?? 'A entidade escolhida não está disponível. Escolha outra.');
      return;
    }
    this.mostrarErro(MENSAGEM_SERVICO_INDISPONIVEL);
  }

  /** O foco vai para o alerta, para quem usa teclado ou leitor de ecrã saber porque não entrou. */
  private mostrarErro(mensagem: string): void {
    this.mensagemErro.set(mensagem);
    afterNextRender(() => (this.alertaErro()?.nativeElement as HTMLElement | undefined)?.focus(), { injector: this.injector });
  }
}
