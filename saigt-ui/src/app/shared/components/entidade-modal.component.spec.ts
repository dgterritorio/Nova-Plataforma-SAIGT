import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../core/models/error-response.model';
import { Concelho, Entidade } from '../../models/entidade.model';
import { EntidadeService } from '../../services/entidade.service';
import { ReferenciaSnitService } from '../../services/referencia-snit.service';
import { EntidadeModalComponent, ModoEntidadeModal } from './entidade-modal.component';

const CONCELHOS: Concelho[] = [
  { codigo: '1106', nome: 'LISBOA', nut2: 'LISBOA E VALE DO TEJO' },
  { codigo: '1111', nome: 'SINTRA', nut2: 'LISBOA E VALE DO TEJO' },
  { codigo: '0412', nome: 'VINHAIS', nut2: null },
];

function entidade(campos: Partial<Entidade> = {}): Entidade {
  return {
    id: 42,
    codigo: '5245',
    nipc: null,
    dico: null,
    descricaoIncm: 'Município de Sintra',
    descricaoSaigt: 'Câmara Municipal de Sintra',
    tipoEntidade: 'CM',
    telefone: null,
    email: null,
    ativo: true,
    atualizadoEm: null,
    nome: 'Município de Sintra',
    utilizadorNome: null,
    utilizadorEmail: null,
    utilizadorCargo: null,
    contactoPrincipal: null,
    ...campos,
  };
}

describe('EntidadeModalComponent', () => {
  let fixture: ComponentFixture<EntidadeModalComponent>;
  let servico: { criar: ReturnType<typeof vi.fn>; atualizar: ReturnType<typeof vi.fn> };
  let referencias: { concelhos: ReturnType<typeof vi.fn> };
  let gravadas: Entidade[];

  const el = () => fixture.nativeElement as HTMLElement;
  const campo = (nome: string) => el().querySelector<HTMLInputElement>(`[data-campo="${nome}"] input`)!;
  const seletor = (nome: string) => el().querySelector<HTMLSelectElement>(`[data-campo="${nome}"] select`);
  const botaoGravar = () => el().querySelector<HTMLButtonElement>('button.gravar')!;
  const erroDe = (nome: string) => el().querySelector(`[data-campo="${nome}"] .campo-texto-erro, [data-campo="${nome}"] .seletor-erro`)?.textContent?.trim();

  function escrever(nome: string, valor: string): void {
    const input = campo(nome);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function escolher(nome: string, valor: string): void {
    const select = seletor(nome)!;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submeter(): void {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function abrir(modo: ModoEntidadeModal, emEdicao: Entidade | null = null, concelhos: Observable<Concelho[]> = of(CONCELHOS)): void {
    referencias.concelhos.mockReturnValue(concelhos);
    fixture = TestBed.createComponent(EntidadeModalComponent);
    fixture.componentInstance.gravada.subscribe((gravada) => gravadas.push(gravada));
    fixture.componentRef.setInput('modo', modo);
    fixture.componentRef.setInput('entidade', emEdicao);
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
    vi.runAllTimers();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    gravadas = [];
    servico = { criar: vi.fn().mockReturnValue(of(entidade())), atualizar: vi.fn().mockReturnValue(of(entidade())) };
    referencias = { concelhos: vi.fn() };
    TestBed.configureTestingModule({
      imports: [EntidadeModalComponent],
      providers: [
        { provide: EntidadeService, useValue: servico },
        { provide: ReferenciaSnitService, useValue: referencias },
      ],
    });
  });

  afterEach(() => vi.useRealTimers());

  function preencherObrigatorios(tipo = 'PCM'): void {
    escrever('codigo', '9001');
    escrever('descricao-incm', 'Presidência do Conselho de Ministros');
    escrever('descricao-saigt', 'PCM');
    escolher('tipo', tipo);
  }

  describe('modo criar', () => {
    beforeEach(() => abrir('criar'));

    it('tem o título «Adicionar entidade» e carrega os concelhos uma vez ao abrir', () => {
      expect(el().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Adicionar entidade');
      expect(referencias.concelhos).toHaveBeenCalledTimes(1);
    });

    it('cria sem nipc nem dico quando ficam vazios, e avisa quem abriu', () => {
      preencherObrigatorios();
      expect(botaoGravar().disabled).toBe(false);

      submeter();

      expect(servico.criar).toHaveBeenCalledWith({
        codigo: '9001',
        descricaoIncm: 'Presidência do Conselho de Ministros',
        descricaoSaigt: 'PCM',
        tipoEntidade: 'PCM',
      });
      expect(gravadas).toHaveLength(1);
    });

    it('desativa o botão sem tipo', () => {
      escrever('codigo', '9001');
      escrever('descricao-incm', 'X');
      escrever('descricao-saigt', 'X');
      expect(botaoGravar().disabled).toBe(true);
    });

    it('desativa o botão com o NIPC com 8 dígitos e explica no campo', () => {
      preencherObrigatorios();
      escrever('nipc', '50123456');
      expect(botaoGravar().disabled).toBe(true);
      expect(erroDe('nipc')).toBe('O NIPC tem 9 dígitos.');

      escrever('nipc', '501234567');
      expect(botaoGravar().disabled).toBe(false);
    });

    it('mostra o Concelho só nas CM, com o rótulo «{nome} ({NUTS II})», o valor DTCC e a ajuda ligada', () => {
      escolher('tipo', 'PCM');
      expect(seletor('concelho')).toBeNull();

      escolher('tipo', 'CM');
      const select = seletor('concelho')!;
      const opcoes = [...select.options].map((opcao) => [opcao.value, opcao.textContent?.trim()]);
      expect(opcoes).toEqual([
        ['', 'Sem concelho'],
        ['1106', 'LISBOA (LISBOA E VALE DO TEJO)'],
        ['1111', 'SINTRA (LISBOA E VALE DO TEJO)'],
        ['0412', 'VINHAIS'],
      ]);
      const ajuda = el().querySelector(`#${select.getAttribute('aria-describedby')}`);
      expect(ajuda?.textContent?.trim()).toBe('Usado para pré-preencher a NUTS II e o concelho na Elaboração de planos municipais.');
      expect(el().querySelector(`label[for="${select.id}"]`)?.textContent?.trim()).toBe('Concelho');
    });

    it('envia o dico escolhido numa CM', () => {
      preencherObrigatorios('CM');
      escolher('concelho', '1111');
      submeter();
      expect(servico.criar).toHaveBeenCalledWith(expect.objectContaining({ tipoEntidade: 'CM', dico: '1111' }));
    });

    it('com o código repetido (409), mantém-se aberto com o erro', () => {
      servico.criar.mockReturnValue(
        throwError(() => new ApiError('REFERENCIA_DUPLICADA', 'Código de entidade já existe.', 't', null)),
      );
      preencherObrigatorios();
      submeter();

      expect(el().querySelector('app-alerta.alerta-erro')?.textContent).toContain('Código de entidade já existe.');
      expect(el().querySelector('app-alerta')?.getAttribute('role')).toBe('alert');
      expect(gravadas).toHaveLength(0);
      expect(botaoGravar().disabled).toBe(false);
    });

    it('mostra cada erro de um 400 no campo respetivo, incluindo o dico', () => {
      servico.criar.mockReturnValue(
        throwError(
          () =>
            new ApiError('VALIDACAO', 'Dados da entidade inválidos.', 't', [
              { path: 'nipc', erro: 'NIPC inválido.' },
              { path: 'dico', erro: 'Concelho inexistente.' },
            ]),
        ),
      );
      preencherObrigatorios('CM');
      escrever('nipc', '501234560');
      escolher('concelho', '1111');
      submeter();

      expect(erroDe('nipc')).toBe('NIPC inválido.');
      expect(erroDe('concelho')).toBe('Concelho inexistente.');
      expect(seletor('concelho')!.getAttribute('aria-invalid')).toBe('true');
      expect(el().querySelector('app-alerta.alerta-erro')).toBeNull();
    });
  });

  it('sem a lista de concelhos, o Concelho diz que está indisponível e o resto do formulário funciona', () => {
    abrir('criar', null, throwError(() => new ApiError('ERRO_INTERNO', 'Falhou', 't', null)));
    preencherObrigatorios('CM');

    const select = seletor('concelho')!;
    expect(select.disabled).toBe(true);
    expect(el().querySelector(`#${select.getAttribute('aria-describedby')}`)?.textContent?.trim()).toBe('Lista de concelhos indisponível.');

    submeter();
    expect(servico.criar).toHaveBeenCalledWith(expect.objectContaining({ codigo: '9001', tipoEntidade: 'CM' }));
  });

  describe('modo editar', () => {
    it('tem o título «Editar entidade», os valores atuais e o botão desativado sem alterações', () => {
      abrir('editar', entidade({ nipc: '501234567', dico: '1111' }));

      expect(el().querySelector('.modal-titulo')?.textContent?.trim()).toBe('Editar entidade');
      expect(campo('codigo').value).toBe('5245');
      expect(campo('nipc').value).toBe('501234567');
      expect(seletor('concelho')!.value).toBe('1111');
      expect(botaoGravar().disabled).toBe(true);
      expect(el().textContent).not.toContain('próximo login');
    });

    it('ao escolher SINTRA numa CM, o PATCH leva só dico «1111» e aparece o aviso do próximo login', () => {
      abrir('editar', entidade());
      escolher('concelho', '1111');

      expect(el().textContent).toContain('A alteração vale no próximo login dos utilizadores desta entidade.');
      submeter();
      expect(servico.atualizar).toHaveBeenCalledWith(42, { dico: '1111' });
      expect(gravadas).toHaveLength(1);
    });

    it('ao mudar uma CM com concelho para CIM, esconde o Concelho e o PATCH leva dico vazio', () => {
      abrir('editar', entidade({ dico: '1111' }));
      escolher('tipo', 'CIM');

      expect(seletor('concelho')).toBeNull();
      expect(el().textContent).toContain('A alteração vale no próximo login');
      submeter();
      expect(servico.atualizar).toHaveBeenCalledWith(42, { tipoEntidade: 'CIM', dico: '' });
    });

    it('ao corrigir o NIPC e a descrição INCM, o PATCH leva só esses dois campos e sem aviso de login', () => {
      abrir('editar', entidade({ nipc: '500000000' }));
      escrever('nipc', '501234567');
      escrever('descricao-incm', 'MUNICIPIO DE SINTRA');

      expect(el().textContent).not.toContain('próximo login');
      submeter();
      expect(servico.atualizar).toHaveBeenCalledWith(42, { nipc: '501234567', descricaoIncm: 'MUNICIPIO DE SINTRA' });
    });

    it('apagar o NIPC envia-o vazio, para o limpar', () => {
      abrir('editar', entidade({ nipc: '500000000' }));
      escrever('nipc', '');
      submeter();
      expect(servico.atualizar).toHaveBeenCalledWith(42, { nipc: '' });
    });

    it('volta a ficar sem alterações quando o valor regressa ao original', () => {
      abrir('editar', entidade());
      escrever('descricao-saigt', 'Outra');
      expect(botaoGravar().disabled).toBe(false);
      escrever('descricao-saigt', 'Câmara Municipal de Sintra');
      expect(botaoGravar().disabled).toBe(true);
    });

    it('com a lista de concelhos indisponível, mantém o concelho gravado e não o envia', () => {
      abrir('editar', entidade({ dico: '1111' }), throwError(() => new ApiError('ERRO_INTERNO', 'Falhou', 't', null)));
      escrever('nipc', '501234567');
      submeter();
      expect(servico.atualizar).toHaveBeenCalledWith(42, { nipc: '501234567' });
    });
  });

  it('o campo obrigatório vazio mostra o erro ligado por aria-describedby', () => {
    abrir('criar');
    escrever('codigo', '   ');
    const input = campo('codigo');
    expect(erroDe('codigo')).toBe('Indique o código.');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain(el().querySelector('[data-campo="codigo"] .campo-texto-erro')!.id);
  });
});
