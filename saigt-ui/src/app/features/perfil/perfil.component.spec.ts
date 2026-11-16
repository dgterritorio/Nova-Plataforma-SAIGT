import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PerfilComponent } from './perfil.component';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { UtilizadorService } from '../../services/utilizador.service';
import { UtilizadorMe } from '../../models/utilizador.model';

describe('PerfilComponent', () => {
  let fixture: ComponentFixture<PerfilComponent>;
  let authServiceMock: Record<string, ReturnType<typeof vi.fn>>;
  let utilizadorServiceMock: { obterMe: ReturnType<typeof vi.fn>; atualizarMe: ReturnType<typeof vi.fn> };

  const perfilDgt: UtilizadorMe = {
    nome: 'joao.teixeira',
    email: 'joao.teixeira@dgterritorio.gov.pt',
    cargo: null,
    telefone: '912345678',
    morada: 'Rua da Artilharia Um, 107',
    origemAutenticacao: 'AD_DGT',
    perfilInterno: 'MASTER',
    entidade: null,
  };

  const perfilIncm: UtilizadorMe = {
    nome: 'Ana Teixeira',
    email: 'ana.teixeira@cm-setubal.pt',
    cargo: 'Técnica Superior de Urbanismo',
    telefone: null,
    morada: null,
    origemAutenticacao: 'INCM',
    perfilInterno: null,
    entidade: {
      codigo: '3015',
      nome: 'Município de Setúbal',
      tipoEntidade: 'CM',
      nipc: '512523698',
      telefone: '214823000',
      email: 'geral@cm-setubal.pt',
      responsavel: { nome: 'Carlos Pereira', email: 'carlos.pereira@cm-setubal.pt', cargo: 'Diretor do Departamento de Urbanismo' },
    },
  };

  function criarComponente(perfil: UtilizadorMe): void {
    authServiceMock['origem'].mockReturnValue(perfil.origemAutenticacao);
    utilizadorServiceMock.obterMe.mockReturnValue(of(perfil));
    TestBed.configureTestingModule({
      imports: [PerfilComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: UtilizadorService, useValue: utilizadorServiceMock },
      ],
    });
    fixture = TestBed.createComponent(PerfilComponent);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function campo(rotulo: string): HTMLInputElement {
    const label = Array.from(el().querySelectorAll('label')).find((l) => l.textContent?.trim() === rotulo);
    if (!label) {
      throw new Error(`Sem o campo ${rotulo}`);
    }
    return el().querySelector(`#${label.getAttribute('for')}`) as HTMLInputElement;
  }

  function escrever(rotulo: string, valor: string): void {
    const input = campo(rotulo);
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  function botaoGuardar(): HTMLButtonElement {
    return el().querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  function guardar(): void {
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    authServiceMock = {
      origem: vi.fn(),
      perfilInterno: vi.fn().mockReturnValue(null),
      nome: vi.fn().mockReturnValue(null),
      email: vi.fn().mockReturnValue(null),
      cargo: vi.fn().mockReturnValue(null),
      entidade: vi.fn().mockReturnValue(null),
    };
    utilizadorServiceMock = { obterMe: vi.fn(), atualizarMe: vi.fn() };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('utilizador INCM', () => {
    it('lê o perfil do GET /me e não tem nenhum campo editável nem botão de gravar', () => {
      criarComponente(perfilIncm);

      expect(utilizadorServiceMock.obterMe).toHaveBeenCalledTimes(1);
      expect(el().querySelectorAll('main input, main textarea, main button, main form')).toHaveLength(0);
      expect(utilizadorServiceMock.atualizarMe).not.toHaveBeenCalled();
    });

    it('mostra a nota de que os dados vêm da INCM em cada login', () => {
      criarComponente(perfilIncm);

      expect(el().textContent).toContain('Os seus dados são atualizados a partir da INCM em cada login.');
    });

    it('mostra nome, email e cargo do utilizador', () => {
      criarComponente(perfilIncm);
      const texto = el().textContent ?? '';

      expect(texto).toContain('Ana Teixeira');
      expect(texto).toContain('ana.teixeira@cm-setubal.pt');
      expect(texto).toContain('Técnica Superior de Urbanismo');
    });

    it('mostra a entidade com nome, NIF/NIPC, telefone e email e o responsável com nome, email e cargo', () => {
      criarComponente(perfilIncm);
      const texto = el().textContent ?? '';

      for (const valor of ['Município de Setúbal', '512523698', '214823000', 'geral@cm-setubal.pt', 'Carlos Pereira',
        'carlos.pereira@cm-setubal.pt', 'Diretor do Departamento de Urbanismo']) {
        expect(texto).toContain(valor);
      }
      expect(texto).toContain('NIF / NIPC');
      expect(texto).toContain('Responsável da entidade');
    });

    it('não mostra a morada da entidade (artéria, código postal, localidade, designação postal)', () => {
      criarComponente(perfilIncm);
      const texto = el().textContent ?? '';

      for (const rotulo of ['Artéria', 'Código postal', 'Localidade', 'Designação postal']) {
        expect(texto).not.toContain(rotulo);
      }
    });
  });

  describe('utilizador DGT', () => {
    it('só o email, o telefone e a morada são editáveis; o nome é só de leitura', () => {
      criarComponente(perfilDgt);

      expect(campo('Nome completo').disabled).toBe(true);
      expect(campo('Nome completo').value).toBe('joao.teixeira');
      expect(campo('Email').disabled).toBe(false);
      expect(campo('Telefone').disabled).toBe(false);
      expect(campo('Morada').disabled).toBe(false);
      expect(el().querySelectorAll('main input:not([disabled])')).toHaveLength(3);
      expect(el().textContent).not.toContain('Cargo / Função');
    });

    it('«Guardar» fica desativado sem alterações e ativa-se ao mudar um campo', () => {
      criarComponente(perfilDgt);
      expect(botaoGuardar().getAttribute('aria-disabled')).toBe('true');

      escrever('Telefone', '+351 912 000 000');

      expect(botaoGuardar().getAttribute('aria-disabled')).toBeNull();
      expect(el().textContent).toContain('Alterações não guardadas');
    });

    it('envia só os campos alterados e mostra o alerta de sucesso durante 4 s', () => {
      vi.useFakeTimers();
      const atualizado: UtilizadorMe = { ...perfilDgt, email: 'jt@dgterritorio.gov.pt', morada: null };
      utilizadorServiceMock.atualizarMe.mockReturnValue(of(atualizado));
      criarComponente(perfilDgt);

      escrever('Email', ' jt@dgterritorio.gov.pt ');
      escrever('Morada', '');
      guardar();

      expect(utilizadorServiceMock.atualizarMe).toHaveBeenCalledWith({ email: 'jt@dgterritorio.gov.pt', morada: '' });
      expect(el().textContent).toContain('Os seus dados foram atualizados.');
      expect(botaoGuardar().getAttribute('aria-disabled')).toBe('true');

      vi.advanceTimersByTime(3999);
      fixture.detectChanges();
      expect(el().textContent).toContain('Os seus dados foram atualizados.');
      vi.advanceTimersByTime(1);
      fixture.detectChanges();
      expect(el().textContent).not.toContain('Os seus dados foram atualizados.');
    });

    it('valida o formato do email junto do campo e não deixa gravar', () => {
      criarComponente(perfilDgt);

      escrever('Email', 'sem-arroba.pt');

      expect(el().textContent).toContain('Indique um email válido');
      expect(campo('Email').getAttribute('aria-invalid')).toBe('true');
      expect(botaoGuardar().getAttribute('aria-disabled')).toBe('true');
      guardar();
      expect(utilizadorServiceMock.atualizarMe).not.toHaveBeenCalled();
    });

    it('valida os limites e o formato do telefone e o limite da morada', () => {
      criarComponente(perfilDgt);

      escrever('Telefone', 'abc');
      expect(el().textContent).toContain('Use só algarismos, espaços e os sinais');
      escrever('Telefone', '9'.repeat(31));
      expect(el().textContent).toContain('Tem de ter no máximo 30 caracteres.');
      escrever('Telefone', '912345678');
      escrever('Morada', 'm'.repeat(301));
      expect(el().textContent).toContain('Tem de ter no máximo 300 caracteres.');
      expect(botaoGuardar().getAttribute('aria-disabled')).toBe('true');
    });

    it('com 409 do email de outra conta mostra a mensagem junto do email e limpa-a ao editar', () => {
      // O errorEnvelopeInterceptor entrega o envelope da API como ApiError.
      utilizadorServiceMock.atualizarMe.mockReturnValue(throwError(() => new ApiError('REFERENCIA_DUPLICADA',
        'Este email já está associado a outra conta da DGT.', 'trace',
        [{ path: 'email', erro: 'Este email já está associado a outra conta da DGT.' }])));
      criarComponente(perfilDgt);

      escrever('Email', 'outra.pessoa@dgterritorio.gov.pt');
      guardar();

      const erro = el().querySelector(`#${campo('Email').getAttribute('aria-describedby')}`);
      expect(erro?.textContent).toContain('Este email já está associado a outra conta da DGT.');
      expect(campo('Email').getAttribute('aria-invalid')).toBe('true');
      expect(el().querySelector('app-alerta[tipo="erro"]')).toBeNull();

      escrever('Email', 'outra.pessoa2@dgterritorio.gov.pt');
      expect(el().textContent).not.toContain('Este email já está associado a outra conta da DGT.');
    });

    it('com 400 num campo mostra o erro do servidor junto desse campo', () => {
      utilizadorServiceMock.atualizarMe.mockReturnValue(throwError(() => new ApiError('VALIDACAO', 'Dados do perfil inválidos.',
        'trace', [{ path: 'telefone', erro: 'Tem de ter no máximo 30 caracteres.' }])));
      criarComponente(perfilDgt);

      escrever('Telefone', '210000000');
      guardar();

      expect(el().textContent).toContain('Tem de ter no máximo 30 caracteres.');
      expect(campo('Telefone').getAttribute('aria-invalid')).toBe('true');
    });

    it('com um erro sem campo mostra a mensagem do servidor num alerta', () => {
      utilizadorServiceMock.atualizarMe.mockReturnValue(throwError(() => new ApiError('VALIDACAO',
        'Só pode alterar o email, o telefone e a morada.', 'trace', null)));
      criarComponente(perfilDgt);

      escrever('Morada', 'Rua Nova, 1');
      guardar();

      expect(el().querySelector('app-alerta.alerta-erro')?.textContent).toContain('Só pode alterar o email, o telefone e a morada.');
    });

    it('com um erro desconhecido (sem envelope) mostra a mensagem genérica', () => {
      utilizadorServiceMock.atualizarMe.mockReturnValue(throwError(() => new Error('rede')));
      criarComponente(perfilDgt);

      escrever('Morada', 'Rua Nova, 2');
      guardar();

      expect(el().textContent).toContain('Não foi possível gravar as alterações. Tente novamente.');
    });

    it('mostra o perfil de acesso', () => {
      criarComponente(perfilDgt);

      expect(el().textContent).toContain('Master');
      expect(el().textContent).toContain('Gerido pelo administrador SAIGT');
    });
  });

  it('mostra um erro quando o GET /me falha', () => {
    authServiceMock['origem'].mockReturnValue('AD_DGT');
    utilizadorServiceMock.obterMe.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro interno.', 'trace', null)));
    TestBed.configureTestingModule({
      imports: [PerfilComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: UtilizadorService, useValue: utilizadorServiceMock },
      ],
    });
    fixture = TestBed.createComponent(PerfilComponent);
    fixture.detectChanges();

    expect(el().textContent).toContain('Não foi possível carregar os seus dados.');
    expect(el().querySelector('form')).toBeNull();
  });
});
