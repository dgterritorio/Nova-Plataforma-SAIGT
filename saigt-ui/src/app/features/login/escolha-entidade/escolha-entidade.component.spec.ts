import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EscolhaEntidadeComponent } from './escolha-entidade.component';
import { AuthService } from '../../../core/auth/auth.service';
import { EntidadeElegivel, ErroAutenticacao } from '../../../core/auth/auth.models';
import { MENSAGEM_SERVICO_INDISPONIVEL } from '../mensagens-autenticacao';

const SEM_CORRESPONDENCIA = 'A entidade não está criada na SAIGT. Foi enviado um pedido de apoio à DGT.';
const CASCAIS: EntidadeElegivel = { incmEnrollmentId: 'ENR-101', nipc: '505187531', nome: 'Município de Cascais' };
const SINTRA: EntidadeElegivel = { incmEnrollmentId: 'ENR-102', nipc: '500051062', nome: 'Câmara Municipal de Sintra' };
const SEM_ENTIDADE: EntidadeElegivel = {
  incmEnrollmentId: 'ENR-103',
  nipc: '999999990',
  nome: 'Entidade de Teste Sem Correspondência',
};

describe('EscolhaEntidadeComponent', () => {
  let fixture: ComponentFixture<EscolhaEntidadeComponent>;
  let entidades: EntidadeElegivel[];
  let authServiceMock: {
    emEscolhaDeEntidade: () => boolean;
    entidadesElegiveis: () => EntidadeElegivel[] | null;
    escolherEntidade: ReturnType<typeof vi.fn>;
  };
  let routerMock: { navigateByUrl: ReturnType<typeof vi.fn>; navigate: ReturnType<typeof vi.fn> };

  function criarComponente(): void {
    fixture = TestBed.createComponent(EscolhaEntidadeComponent);
    fixture.detectChanges();
  }

  function elemento(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function opcao(incmEnrollmentId: string): HTMLButtonElement {
    const botao = elemento().querySelector<HTMLButtonElement>(`button[data-adesao="${incmEnrollmentId}"]`);
    if (!botao) {
      throw new Error(`Sem opção para ${incmEnrollmentId}`);
    }
    return botao;
  }

  function botaoContinuar(): HTMLButtonElement {
    return elemento().querySelector<HTMLButtonElement>('button.continuar') as HTMLButtonElement;
  }

  function texto(seletor: string): string {
    return (elemento().querySelector(seletor)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  beforeEach(() => {
    entidades = [CASCAIS, SINTRA, SEM_ENTIDADE];
    authServiceMock = {
      emEscolhaDeEntidade: () => true,
      entidadesElegiveis: () => entidades,
      escolherEntidade: vi.fn(),
    };
    routerMock = { navigateByUrl: vi.fn(), navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [EscolhaEntidadeComponent],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  /** Após recuar/avançar, a tab não pode manter o título da página anterior. */
  it('define o título da página como "Autenticação INCM · SAIGT"', () => {
    criarComponente();

    expect(TestBed.inject(Title).getTitle()).toBe('Autenticação INCM · SAIGT');
  });

  it('encaminha para o login quando não há escolha de entidade em curso', () => {
    authServiceMock.emEscolhaDeEntidade = () => false;

    criarComponente();

    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('com uma entidade: selo «1 entidade», texto de confirmação, sem pré-seleção e «Continuar» desativado', () => {
    entidades = [SINTRA];

    criarComponente();

    expect(texto('.banner-badge')).toBe('1 entidade');
    expect(texto('.secao-intro')).toBe(
      'Foi encontrada uma entidade associada à sua conta INCM. Confirme-a para continuar nesta sessão de trabalho.',
    );
    expect(opcao('ENR-102').getAttribute('aria-pressed')).toBe('false');
    expect(fixture.componentInstance.selecionada()).toBeNull();
    expect(botaoContinuar().disabled).toBe(true);
  });

  it('com várias entidades: selo «{n} entidades» e o texto das várias', () => {
    criarComponente();

    expect(texto('.banner-badge')).toBe('3 entidades');
    expect(texto('.secao-intro')).toContain('Foram encontradas várias entidades associadas à sua conta INCM.');
    expect(elemento().querySelectorAll('button.linha-entidade')).toHaveLength(3);
    expect(elemento().querySelectorAll('button.linha-entidade[aria-pressed="true"]')).toHaveLength(0);
  });

  it('cada opção mostra o nome e o NIPC formatado', () => {
    criarComponente();

    expect(opcao('ENR-101').querySelector('.linha-nome')?.textContent?.trim()).toBe('Município de Cascais');
    expect(opcao('ENR-101').querySelector('.linha-nipc')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('NIPC 505 187 531');
  });

  it('selecionar marca a opção com aria-pressed e ativa «Continuar», sem submeter', () => {
    criarComponente();

    opcao('ENR-101').click();
    fixture.detectChanges();

    expect(opcao('ENR-101').getAttribute('aria-pressed')).toBe('true');
    expect(opcao('ENR-102').getAttribute('aria-pressed')).toBe('false');
    expect(botaoContinuar().disabled).toBe(false);
    expect(authServiceMock.escolherEntidade).not.toHaveBeenCalled();
  });

  it('não submete ao confirmar sem nenhuma entidade selecionada', () => {
    criarComponente();

    fixture.componentInstance.confirmar();

    expect(authServiceMock.escolherEntidade).not.toHaveBeenCalled();
  });

  it('confirma pelo incmEnrollmentId (nunca pelo NIPC) e entra na aplicação', () => {
    criarComponente();
    authServiceMock.escolherEntidade.mockReturnValue(of(undefined));

    fixture.componentInstance.selecionar('ENR-101');
    fixture.componentInstance.confirmar();

    expect(authServiceMock.escolherEntidade).toHaveBeenCalledWith('ENR-101');
    expect(authServiceMock.escolherEntidade).not.toHaveBeenCalledWith('505187531');
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('entidade sem correspondência: mostra a descrição num alerta com foco, marca a opção indisponível e deixa escolher outra', async () => {
    criarComponente();
    authServiceMock.escolherEntidade.mockReturnValueOnce(throwError(() => new ErroAutenticacao('RECUSADO', SEM_CORRESPONDENCIA)));

    fixture.componentInstance.selecionar('ENR-103');
    fixture.componentInstance.confirmar();
    fixture.detectChanges();
    await fixture.whenStable();

    const alerta = elemento().querySelector<HTMLElement>('app-alerta.alerta-escolha');
    expect(alerta?.getAttribute('role')).toBe('alert');
    expect(alerta?.textContent).toContain(SEM_CORRESPONDENCIA);
    expect(document.activeElement).toBe(alerta);
    expect(opcao('ENR-103').disabled).toBe(true);
    expect(opcao('ENR-103').textContent).toContain('Indisponível');
    expect(opcao('ENR-103').getAttribute('aria-pressed')).toBe('false');
    expect(botaoContinuar().disabled).toBe(true);
    expect(routerMock.navigate).not.toHaveBeenCalled();

    fixture.componentInstance.selecionar('ENR-103');
    expect(fixture.componentInstance.selecionada()).toBeNull();

    authServiceMock.escolherEntidade.mockReturnValueOnce(of(undefined));
    opcao('ENR-101').click();
    fixture.detectChanges();
    botaoContinuar().click();

    expect(authServiceMock.escolherEntidade).toHaveBeenLastCalledWith('ENR-101');
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('pré-autenticação expirada: volta ao login com o aviso da escolha expirada', () => {
    criarComponente();
    authServiceMock.escolherEntidade.mockReturnValue(
      throwError(() => new ErroAutenticacao('PRE_AUTENTICACAO_EXPIRADA', 'Pré-autenticação inválida, expirada ou já usada.')),
    );

    fixture.componentInstance.escolher('ENR-101');

    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { erro: 'SESSAO_EXPIRADA' } });
  });

  it('serviço indisponível: mostra a mensagem e mantém a opção disponível', () => {
    criarComponente();
    authServiceMock.escolherEntidade.mockReturnValue(throwError(() => new ErroAutenticacao('INDISPONIVEL', null)));

    fixture.componentInstance.selecionar('ENR-101');
    fixture.componentInstance.confirmar();
    fixture.detectChanges();

    expect(texto('app-alerta.alerta-escolha')).toBe(MENSAGEM_SERVICO_INDISPONIVEL);
    expect(opcao('ENR-101').disabled).toBe(false);
    expect(routerMock.navigate).not.toHaveBeenCalled();
  });

  it('desativa as opções enquanto a escolha está a ser enviada', () => {
    criarComponente();
    authServiceMock.escolherEntidade.mockReturnValue(new Subject<void>());

    fixture.componentInstance.selecionar('ENR-101');
    fixture.componentInstance.confirmar();
    fixture.detectChanges();

    expect(opcao('ENR-102').disabled).toBe(true);
  });
});
