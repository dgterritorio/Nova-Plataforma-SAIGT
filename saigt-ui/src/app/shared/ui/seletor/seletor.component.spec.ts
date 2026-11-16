import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { OpcaoSeletor, SeletorComponent } from './seletor.component';

const OPCOES: OpcaoSeletor[] = [
  { valor: 'VALIDADO_DGT', rotulo: 'Validado DGT' },
  { valor: 'REJEITADO_DGT', rotulo: 'Rejeitado DGT' },
];

@Component({
  imports: [SeletorComponent, ReactiveFormsModule],
  template: `
    <app-seletor
      rotulo="Novo estado"
      [opcoes]="opcoes"
      [obrigatorio]="obrigatorio()"
      [erro]="erro()"
      [ajuda]="ajuda()"
      [formControl]="controlo"
    />
  `,
})
class AnfitriaoComponent {
  readonly opcoes = OPCOES;
  readonly obrigatorio = signal(true);
  readonly erro = signal<string | null>(null);
  readonly ajuda = signal<string | null>(null);
  readonly controlo = new FormControl<string | null>(null);
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  const select = el.querySelector('select') as HTMLSelectElement;
  return { fixture, el, select, anfitriao: fixture.componentInstance };
}

describe('SeletorComponent', () => {
  it('liga o rótulo ao select e marca o obrigatório', () => {
    const { el, select } = criar();

    const rotulo = el.querySelector('label')!;
    expect(rotulo.getAttribute('for')).toBe(select.id);
    expect(rotulo.textContent?.replace(/\s+/g, ' ').trim()).toBe('Novo estado *');
    expect(select.required).toBe(true);
  });

  it('mostra o placeholder desativado e as opções', () => {
    const { select } = criar();

    const opcoes = [...select.options].map((o) => [o.textContent?.trim(), o.disabled]);
    expect(opcoes).toEqual([
      ['Selecione…', true],
      ['Validado DGT', false],
      ['Rejeitado DGT', false],
    ]);
    expect(select.value).toBe('');
  });

  it('ao escolher uma opção, o valor chega ao FormControl', () => {
    const { select, anfitriao } = criar();

    select.value = 'REJEITADO_DGT';
    select.dispatchEvent(new Event('change'));

    expect(anfitriao.controlo.value).toBe('REJEITADO_DGT');
  });

  it('o valor do FormControl seleciona a opção e o reset volta ao placeholder', () => {
    const { fixture, select, anfitriao } = criar();

    anfitriao.controlo.setValue('VALIDADO_DGT');
    fixture.detectChanges();
    expect(select.value).toBe('VALIDADO_DGT');

    anfitriao.controlo.reset();
    fixture.detectChanges();
    expect(select.value).toBe('');
  });

  it('é normal por omissão e marca o host quando compacto', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Estado');
    fixture.componentRef.setInput('opcoes', OPCOES);
    fixture.detectChanges();
    expect(fixture.nativeElement.classList).not.toContain('seletor-compacto');

    fixture.componentRef.setInput('compacto', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.classList).toContain('seletor-compacto');
  });

  it('desativa o select quando o FormControl está desativado', () => {
    const { fixture, select, anfitriao } = criar();

    anfitriao.controlo.disable();
    fixture.detectChanges();

    expect(select.disabled).toBe(true);
  });

  it('marca o controlo como tocado ao sair do campo', () => {
    const { select, anfitriao } = criar();

    select.dispatchEvent(new Event('blur'));

    expect(anfitriao.controlo.touched).toBe(true);
  });

  it('com erro, mostra a mensagem ligada por aria-describedby e aria-invalid', () => {
    const { fixture, el, select, anfitriao } = criar();
    anfitriao.erro.set('Escolha um estado.');
    fixture.detectChanges();

    const erro = el.querySelector('.seletor-erro')!;
    expect(erro.textContent?.trim()).toBe('Escolha um estado.');
    expect(select.getAttribute('aria-describedby')).toBe(erro.id);
    expect(select.getAttribute('aria-invalid')).toBe('true');
  });

  it('o erro aparece dentro de uma região aria-live que já existia sem erro, para ser anunciado', () => {
    const { fixture, el, anfitriao } = criar();
    const regiao = el.querySelector('[aria-live="polite"]')!;
    expect(regiao.textContent?.trim()).toBe('');

    anfitriao.erro.set('Escolha um estado.');
    fixture.detectChanges();

    expect(el.querySelector('[aria-live="polite"]')).toBe(regiao);
    expect(regiao.querySelector('.seletor-erro')?.textContent?.trim()).toBe('Escolha um estado.');
  });

  it('com ajuda, mostra-a ligada por aria-describedby, antes do erro', () => {
    const { fixture, el, select, anfitriao } = criar();
    expect(el.querySelector('.seletor-ajuda')).toBeNull();
    expect(select.hasAttribute('aria-describedby')).toBe(false);

    anfitriao.ajuda.set('O tipo não pode mudar.');
    anfitriao.erro.set('Escolha um estado.');
    fixture.detectChanges();

    const ajuda = el.querySelector('.seletor-ajuda')!;
    const erro = el.querySelector('.seletor-erro')!;
    expect(ajuda.textContent?.trim()).toBe('O tipo não pode mudar.');
    expect(select.getAttribute('aria-describedby')).toBe(`${ajuda.id} ${erro.id}`);
  });

  it('agrupa num optgroup as opções seguidas com o mesmo grupo e deixa fora as que não têm grupo', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Item');
    fixture.componentRef.setInput('opcoes', [
      { valor: '1', rotulo: 'Regulamento', grupo: 'Publicação' },
      { valor: '2', rotulo: 'Planta', grupo: 'Publicação' },
      { valor: '3', rotulo: 'Relatório', grupo: 'Depósito' },
      { valor: 'x', rotulo: 'Sem grupo' },
    ] satisfies OpcaoSeletor[]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const grupos = [...el.querySelectorAll('optgroup')].map((g) => [g.label, [...g.querySelectorAll('option')].map((o) => o.value)]);
    expect(grupos).toEqual([
      ['Publicação', ['1', '2']],
      ['Depósito', ['3']],
    ]);
    expect((el.querySelector('option[value="x"]') as HTMLOptionElement).parentElement?.tagName).toBe('SELECT');
  });

  it('com ajuda, liga o texto por aria-describedby, junto com o erro', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Concelho');
    fixture.componentRef.setInput('opcoes', OPCOES);
    fixture.componentRef.setInput('ajuda', 'Usado para pré-preencher.');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const select = el.querySelector('select')!;
    const ajuda = el.querySelector('.seletor-ajuda')!;
    expect(ajuda.textContent?.trim()).toBe('Usado para pré-preencher.');
    expect(select.getAttribute('aria-describedby')).toBe(ajuda.id);

    fixture.componentRef.setInput('erro', 'Concelho inexistente.');
    fixture.detectChanges();
    const erro = el.querySelector('.seletor-erro')!;
    expect(select.getAttribute('aria-describedby')).toBe(`${ajuda.id} ${erro.id}`);
  });

  it('junta ao aria-describedby os ids de avisos de fora, depois da ajuda e do erro', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Concelho');
    fixture.componentRef.setInput('opcoes', OPCOES);
    fixture.componentRef.setInput('descritoPorAdicional', 'aviso-externo');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const select = el.querySelector('select')!;
    expect(select.getAttribute('aria-describedby')).toBe('aviso-externo');

    fixture.componentRef.setInput('ajuda', 'Usado para pré-preencher.');
    fixture.componentRef.setInput('erro', 'Concelho inexistente.');
    fixture.detectChanges();
    const ajuda = el.querySelector('.seletor-ajuda')!;
    const erro = el.querySelector('.seletor-erro')!;
    expect(select.getAttribute('aria-describedby')).toBe(`${ajuda.id} ${erro.id} aviso-externo`);
  });

  it('com permitirVazio, o placeholder escolhe-se e devolve null', () => {
    const fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
    const seletor = fixture.debugElement.children[0].componentInstance as SeletorComponent;
    expect(seletor.permitirVazio()).toBe(false);

    const vazio = TestBed.createComponent(SeletorComponent);
    vazio.componentRef.setInput('rotulo', 'Tipo');
    vazio.componentRef.setInput('opcoes', OPCOES);
    vazio.componentRef.setInput('placeholder', 'Todos');
    vazio.componentRef.setInput('permitirVazio', true);
    vazio.detectChanges();
    const select = (vazio.nativeElement as HTMLElement).querySelector('select')!;
    expect(select.options[0].disabled).toBe(false);
    let recebido: string | null = 'x';
    vazio.componentInstance.registerOnChange((v) => (recebido = v));
    select.value = 'VALIDADO_DGT';
    select.dispatchEvent(new Event('change'));
    select.value = '';
    select.dispatchEvent(new Event('change'));
    expect(recebido).toBeNull();
  });

  it('com rotuloOculto, o rótulo continua ligado ao select mas fica só para o leitor de ecrã', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Tipo de entidade');
    fixture.componentRef.setInput('opcoes', OPCOES);
    fixture.componentRef.setInput('rotuloOculto', true);
    fixture.detectChanges();
    const rotulo = (fixture.nativeElement as HTMLElement).querySelector('label')!;
    expect(rotulo.classList).toContain('seletor-rotulo-oculto');
    expect(rotulo.getAttribute('for')).toBe((fixture.nativeElement as HTMLElement).querySelector('select')!.id);
  });
  it('rotuloAria dá ao select um nome acessível mais completo; sem ele, não há aria-label', () => {
    const fixture = TestBed.createComponent(SeletorComponent);
    fixture.componentRef.setInput('rotulo', 'Grupo');
    fixture.componentRef.setInput('opcoes', OPCOES);
    fixture.detectChanges();
    const select = (fixture.nativeElement as HTMLElement).querySelector('select')!;
    expect(select.hasAttribute('aria-label')).toBe(false);

    fixture.componentRef.setInput('rotuloAria', 'Grupo da condição 2 do grupo 1');
    fixture.detectChanges();
    expect(select.getAttribute('aria-label')).toBe('Grupo da condição 2 do grupo 1');
  });
});
