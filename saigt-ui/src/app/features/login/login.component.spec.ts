import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginComponent } from './login.component';
import { MENSAGEM_ESCOLHA_EXPIRADA, MENSAGEM_SERVICO_INDISPONIVEL } from './mensagens-autenticacao';
import { AuthService } from '../../core/auth/auth.service';
import { ErroAutenticacao } from '../../core/auth/auth.models';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authServiceMock: { login: ReturnType<typeof vi.fn> };
  let routerMock: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };
  let erroQueryParam: string | null;

  function criarComponente(): void {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (nome: string) => (nome === 'erro' ? erroQueryParam : null),
              },
              queryParams: {},
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => {
    authServiceMock = { login: vi.fn() };
    routerMock = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    erroQueryParam = null;
  });

  function preencherFormulario(): void {
    component.form.setValue({ email: 'ana.silva', password: 'senha', utilizadorInternoDgt: true });
  }

  /** Após recuar/avançar, a tab não pode manter o título da página anterior. */
  it('define o título da página como "Autenticação · SAIGT"', () => {
    criarComponente();

    expect(TestBed.inject(Title).getTitle()).toBe('Autenticação · SAIGT');
  });

  it('não submete e marca os campos como tocados quando o formulário é inválido', () => {
    criarComponente();

    component.submeter();

    expect(authServiceMock.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBe(true);
  });

  it('envia o utilizador, a palavra-passe e o mecanismo escolhido ao AuthService', () => {
    criarComponente();
    authServiceMock.login.mockReturnValue(of('SESSAO'));
    preencherFormulario();

    component.submeter();

    expect(authServiceMock.login).toHaveBeenCalledWith({ utilizador: 'ana.silva', password: 'senha', utilizadorInternoDgt: true });
  });

  it('navega para o painel quando o login DGT abre a sessão', () => {
    criarComponente();
    authServiceMock.login.mockReturnValue(of('SESSAO'));
    preencherFormulario();

    component.submeter();

    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/');
    expect(component.mensagemErro()).toBeNull();
  });

  it('navega para a escolha de entidade no login INCM', () => {
    criarComponente();
    authServiceMock.login.mockReturnValue(of('ESCOLHA_ENTIDADE'));
    preencherFormulario();

    component.submeter();

    expect(routerMock.navigate).toHaveBeenCalledWith(['/login/escolha-entidade'], { queryParams: {} });
  });

  it.each([
    [new ErroAutenticacao('RECUSADO', 'Credenciais inválidas.'), 'Credenciais inválidas.'],
    [new ErroAutenticacao('RECUSADO', 'Utilizador sem entidade associada na INCM.'), 'Utilizador sem entidade associada na INCM.'],
    [new ErroAutenticacao('INDISPONIVEL', null), MENSAGEM_SERVICO_INDISPONIVEL],
    [new ErroAutenticacao('OUTRO', null), 'Não foi possível entrar. Tente novamente mais tarde.'],
    [new Error('inesperado'), 'Não foi possível entrar. Tente novamente mais tarde.'],
  ])('mostra o motivo do erro %o', (erro, mensagemEsperada) => {
    criarComponente();
    authServiceMock.login.mockReturnValue(throwError(() => erro));
    preencherFormulario();

    component.submeter();

    expect(component.mensagemErro()).toBe(mensagemEsperada);
    expect(component.aSubmeter()).toBe(false);
  });

  it('mostra o alerta com role="alert" e o motivo no ecrã', () => {
    criarComponente();
    authServiceMock.login.mockReturnValue(throwError(() => new ErroAutenticacao('INDISPONIVEL', null)));
    preencherFormulario();

    component.submeter();
    fixture.detectChanges();

    const alerta = (fixture.nativeElement as HTMLElement).querySelector('app-alerta[role="alert"]');
    expect(alerta?.textContent).toContain(MENSAGEM_SERVICO_INDISPONIVEL);
  });

  it('já não tem a mensagem de conta desativada', () => {
    criarComponente();
    authServiceMock.login.mockReturnValue(throwError(() => new ErroAutenticacao('RECUSADO', null)));
    preencherFormulario();

    component.submeter();

    expect(component.mensagemErro()).not.toContain('desativada');
  });

  it('mostra o aviso de escolha expirada quando chega da escolha de entidade com "erro=SESSAO_EXPIRADA"', () => {
    erroQueryParam = 'SESSAO_EXPIRADA';

    criarComponente();

    expect(component.mensagemErro()).toBe(MENSAGEM_ESCOLHA_EXPIRADA);
    expect(MENSAGEM_ESCOLHA_EXPIRADA).toBe('A sessão de escolha expirou. Entre de novo.');
  });

  it('mostra o aviso de perfil alterado quando chega com "erro=PERFIL_ALTERADO"', () => {
    erroQueryParam = 'PERFIL_ALTERADO';

    criarComponente();

    expect(component.mensagemErro()).toBe('O seu perfil foi alterado. Autentique-se novamente para continuar.');
  });

  it('não mostra nenhum aviso quando chega sem o parâmetro "erro"', () => {
    criarComponente();

    expect(component.mensagemErro()).toBeNull();
  });
});
