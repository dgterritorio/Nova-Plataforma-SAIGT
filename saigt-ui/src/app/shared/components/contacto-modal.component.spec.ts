import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { Contacto } from '../../models/entidade.model';
import { EntidadeService } from '../../services/entidade.service';
import { ContactoModalComponent } from './contacto-modal.component';

const CONTACTO: Contacto = {
  id: 3,
  email: 'urbanismo@cm-sintra.pt',
  descricao: 'Urbanismo',
  telefone: '219238500',
  principal: false,
  ativo: true,
  criadoEm: '2026-11-11T10:00:00Z',
};

describe('ContactoModalComponent', () => {
  let fixture: ComponentFixture<ContactoModalComponent>;
  let servico: { criarContacto: ReturnType<typeof vi.fn>; editarContacto: ReturnType<typeof vi.fn> };
  let gravados: Contacto[];

  const el = () => fixture.nativeElement as HTMLElement;
  const campo = (nome: string) => el().querySelector<HTMLInputElement>(`[data-campo="${nome}"] input`)!;
  const caixaPrincipal = () => el().querySelector<HTMLInputElement>('.caixa-principal input[type="checkbox"]')!;
  const botaoGravar = () => el().querySelector<HTMLButtonElement>('button.gravar')!;

  function escrever(nome: string, valor: string): void {
    const input = campo(nome);
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function marcarPrincipal(): void {
    caixaPrincipal().click();
    fixture.detectChanges();
  }

  function submeter(): void {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function abrir(contacto: Contacto | null): void {
    fixture = TestBed.createComponent(ContactoModalComponent);
    fixture.componentInstance.gravado.subscribe((gravado) => gravados.push(gravado));
    fixture.componentRef.setInput('entidadeId', 42);
    fixture.componentRef.setInput('contacto', contacto);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
    vi.runAllTimers();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    gravados = [];
    servico = { criarContacto: vi.fn().mockReturnValue(of(CONTACTO)), editarContacto: vi.fn().mockReturnValue(of(CONTACTO)) };
    TestBed.configureTestingModule({
      imports: [ContactoModalComponent],
      providers: [{ provide: EntidadeService, useValue: servico }],
    });
  });

  afterEach(() => vi.useRealTimers());

  describe('criar', () => {
    beforeEach(() => abrir(null));

    it('tem o título «Adicionar contacto», a caixa «Contacto principal» desmarcada e ligada à ajuda', () => {
      expect(el().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Adicionar contacto');
      const caixa = caixaPrincipal();
      expect(caixa.checked).toBe(false);
      expect(el().querySelector(`label[for="${caixa.id}"]`)?.textContent?.trim()).toBe('Contacto principal');
      expect(el().querySelector(`#${caixa.getAttribute('aria-describedby')}`)).not.toBeNull();
    });

    it('cria um contacto principal com os campos opcionais vazios a null', () => {
      escrever('email', 'geral@cm-sintra.pt');
      marcarPrincipal();
      submeter();

      expect(servico.criarContacto).toHaveBeenCalledWith(42, {
        email: 'geral@cm-sintra.pt',
        descricao: null,
        telefone: null,
        principal: true,
      });
      expect(gravados).toHaveLength(1);
    });

    it('desativa o botão sem e-mail válido ou com telefone incompleto', () => {
      expect(botaoGravar().disabled).toBe(true);
      escrever('email', 'nao-e-email');
      expect(botaoGravar().disabled).toBe(true);
      expect(el().querySelector('[data-campo="email"] .campo-texto-erro')?.textContent?.trim()).toBe('Indique um e-mail válido.');

      escrever('email', 'geral@cm-sintra.pt');
      escrever('telefone', '21923');
      expect(botaoGravar().disabled).toBe(true);
      escrever('telefone', '219238500');
      expect(botaoGravar().disabled).toBe(false);
    });

    it('o telefone só aceita dígitos', () => {
      escrever('telefone', '21-923 8500');
      expect(campo('telefone').value).toBe('219238500');
    });

    it('com outro principal ativo (409), mostra o erro no modal e não avisa quem abriu', () => {
      servico.criarContacto.mockReturnValue(
        throwError(() => new ApiError('REFERENCIA_DUPLICADA', 'Contacto duplicado ou principal já definido.', 't', null)),
      );
      escrever('email', 'outro@cm-sintra.pt');
      marcarPrincipal();
      submeter();

      expect(el().querySelector('app-alerta.alerta-erro')?.textContent).toContain('Contacto duplicado ou principal já definido.');
      expect(gravados).toHaveLength(0);
    });

    it('mostra no campo o erro de um 400', () => {
      servico.criarContacto.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Dados inválidos.', 't', [{ path: 'telefone', erro: 'Telefone inválido.' }])),
      );
      escrever('email', 'geral@cm-sintra.pt');
      escrever('telefone', '219238500');
      submeter();
      expect(el().querySelector('[data-campo="telefone"] .campo-texto-erro')?.textContent?.trim()).toBe('Telefone inválido.');
    });
  });

  describe('editar', () => {
    beforeEach(() => abrir(CONTACTO));

    it('mostra os valores atuais e o botão desativado sem alterações', () => {
      expect(el().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Editar contacto');
      expect(campo('email').value).toBe('urbanismo@cm-sintra.pt');
      expect(campo('telefone').value).toBe('219238500');
      expect(botaoGravar().disabled).toBe(true);
    });

    it('envia só os campos alterados; o telefone apagado vai vazio para o limpar', () => {
      escrever('telefone', '');
      marcarPrincipal();
      submeter();
      expect(servico.editarContacto).toHaveBeenCalledWith(42, 3, { telefone: '', principal: true });
    });
  });
});
