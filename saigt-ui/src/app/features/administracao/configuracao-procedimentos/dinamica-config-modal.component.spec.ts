import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { DinamicaConfig, TipoDinamicaConfig } from '../../../models/configuracao.model';
import { AJUDA_TIPO_EM_USO, DinamicaConfigModalComponent, ValorDinamicaConfig } from './dinamica-config-modal.component';

const TIPOS: TipoDinamicaConfig[] = [
  { id: 1, codigo: 'ELABORACAO', nome: 'Elaboração', ativo: true },
  { id: 2, codigo: 'REVISAO', nome: 'Revisão', ativo: true },
];

const ALTERACAO_EM_USO: DinamicaConfig = {
  id: 10,
  tipoInstrumento: { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal' },
  tipoDinamica: { id: 3, codigo: 'ALTERACAO', nome: 'Alteração' },
  associacaoInstrumentos: 'UM',
  ativo: true,
  emUso: true,
};

@Component({
  imports: [DinamicaConfigModalComponent],
  template: `
    <app-dinamica-config-modal
      [aberto]="true"
      tipoInstrumento="PROT — Programa Regional"
      [tiposDinamica]="tipos"
      [dinamica]="dinamica()"
      [erroServidor]="erro()"
      (guardar)="valores.push($event)"
    />
  `,
})
class AnfitriaoComponent {
  readonly tipos = TIPOS;
  readonly dinamica = signal<DinamicaConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly valores: ValorDinamicaConfig[] = [];
}

describe('DinamicaConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(dinamica: DinamicaConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.dinamica.set(dinamica);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const seletor = (rotulo: string): HTMLSelectElement => {
    const label = [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLSelectElement;
  };
  const descricao = (entrada: HTMLElement): string =>
    (entrada.getAttribute('aria-describedby') ?? '')
      .split(' ')
      .filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent?.trim())
      .join(' | ');
  const botaoGuardar = (): HTMLButtonElement =>
    [...dialogo().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Guardar')!;

  function escolher(entrada: HTMLSelectElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('cria: Guardar desativado sem tipo; com Revisão e "Um Instrumento" (omissão) emite os ids', () => {
    const anfitriao = criar();
    expect(dialogo().querySelector('h2')?.textContent).toContain('Adicionar Dinâmica');
    expect(dialogo().textContent).toContain('PROT — Programa Regional');
    expect(seletor('Associação de Instrumentos').value).toBe('UM');
    expect([...seletor('Associação de Instrumentos').options].map((o) => o.textContent?.trim()).slice(1)).toEqual([
      'Um Instrumento',
      'Um ou mais Instrumentos',
      'Opcional',
    ]);
    expect(botaoGuardar().disabled).toBe(true);

    escolher(seletor('Tipo de Dinâmica'), '2');
    expect(botaoGuardar().disabled).toBe(false);
    botaoGuardar().click();

    expect(anfitriao.valores).toEqual([{ tipoDinamicaId: 2, associacaoInstrumentos: 'UM' }]);
  });

  it('edita uma linha com emUso: o Tipo de Dinâmica fica desativado, com a ajuda ligada, e a associação pode mudar', () => {
    const anfitriao = criar(ALTERACAO_EM_USO);
    const tipo = seletor('Tipo de Dinâmica');

    expect(tipo.disabled).toBe(true);
    expect(tipo.value).toBe('3');
    expect(tipo.selectedOptions[0].textContent).toContain('Alteração (inativo)');
    expect(descricao(tipo)).toBe(AJUDA_TIPO_EM_USO);

    escolher(seletor('Associação de Instrumentos'), 'OPCIONAL');
    botaoGuardar().click();
    expect(anfitriao.valores).toEqual([{ tipoDinamicaId: 3, associacaoInstrumentos: 'OPCIONAL' }]);
  });

  it('edita uma linha sem emUso: o Tipo de Dinâmica fica ativo e sem ajuda', () => {
    criar({ ...ALTERACAO_EM_USO, emUso: false, tipoDinamica: { id: 2, codigo: 'REVISAO', nome: 'Revisão' } });
    const tipo = seletor('Tipo de Dinâmica');
    expect(tipo.disabled).toBe(false);
    expect(tipo.hasAttribute('aria-describedby')).toBe(false);
    expect(dialogo().textContent).not.toContain(AJUDA_TIPO_EM_USO);
  });

  it('400 no tipoDinamicaId aparece no campo; 409 EM_USO aparece num alerta de erro', () => {
    const anfitriao = criar();
    escolher(seletor('Tipo de Dinâmica'), '1');
    anfitriao.erro.set(new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'tipoDinamicaId', erro: 'Já configurada.' }]));
    fixture.detectChanges();
    expect(descricao(seletor('Tipo de Dinâmica'))).toBe('Já configurada.');

    anfitriao.erro.set(new ApiError('EM_USO', 'Já há Dinâmicas com esta configuração.', 't', null));
    fixture.detectChanges();
    expect(dialogo().querySelector('app-alerta[role="alert"]')?.textContent).toContain('Já há Dinâmicas com esta configuração.');
    expect(descricao(seletor('Tipo de Dinâmica'))).toBe('');
  });

  it('não tem campo Ordem (o contrato não o tem)', () => {
    criar();
    expect([...dialogo().querySelectorAll('label')].map((l) => l.textContent?.trim())).not.toContain('Ordem');
  });
});
