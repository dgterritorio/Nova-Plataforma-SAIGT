import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ListaOpcoesComponent, OpcaoLista } from './lista-opcoes.component';

const OPCOES: OpcaoLista[] = [
  { valor: 'pdm', titulo: 'Plano Diretor Municipal', etiqueta: 'PDM', descricao: 'Âmbito municipal' },
  { valor: 'pu', titulo: 'Plano de Urbanização', etiqueta: 'PU', selo: 'Ativo' },
  { valor: 'pp', titulo: 'Plano de Pormenor', etiqueta: 'PP', desativada: true },
];

function criar(entradas: Record<string, unknown> = {}) {
  const fixture = TestBed.createComponent(ListaOpcoesComponent);
  fixture.componentRef.setInput('legenda', 'Tipo de IGT');
  fixture.componentRef.setInput('opcoes', OPCOES);
  for (const [nome, valor] of Object.entries(entradas)) {
    fixture.componentRef.setInput(nome, valor);
  }
  fixture.detectChanges();
  return fixture;
}

function radios(el: HTMLElement): HTMLInputElement[] {
  return [...el.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
}

describe('ListaOpcoesComponent', () => {
  it('é um grupo de radios nativos, com a legenda lida pelo leitor de ecrã', () => {
    const el = criar().nativeElement as HTMLElement;

    expect(el.querySelector('fieldset legend')?.textContent).toBe('Tipo de IGT');
    expect(radios(el)).toHaveLength(3);
    expect(new Set(radios(el).map((radio) => radio.name)).size).toBe(1);
  });

  it('mostra etiqueta, título, descrição e selo de cada opção', () => {
    const el = criar().nativeElement as HTMLElement;
    const opcoes = [...el.querySelectorAll('label.opcao')];

    expect(opcoes[0].querySelector('.opcao-etiqueta')?.textContent).toBe('PDM');
    expect(opcoes[0].querySelector('.opcao-titulo')?.textContent).toBe('Plano Diretor Municipal');
    expect(opcoes[0].querySelector('.opcao-descricao')?.textContent).toBe('Âmbito municipal');
    expect(opcoes[1].querySelector('.opcao-selo')?.textContent).toBe('Ativo');
    expect(opcoes[2].querySelector('.opcao-selo')).toBeNull();
  });

  it('marca a opção do valor e escolher outra atualiza o valor', () => {
    const fixture = criar({ valor: 'pu' });
    const el = fixture.nativeElement as HTMLElement;

    expect(radios(el).map((radio) => radio.checked)).toEqual([false, true, false]);

    radios(el)[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.valor()).toBe('pdm');
    expect(el.querySelector('label.opcao-escolhida .opcao-titulo')?.textContent).toBe('Plano Diretor Municipal');
  });

  it('uma opção desativada não se escolhe', () => {
    const fixture = criar();
    const el = fixture.nativeElement as HTMLElement;

    expect(radios(el)[2].disabled).toBe(true);
    expect(radios(el)[0].disabled).toBe(false);
  });

  it('desativado desativa todas as opções', () => {
    const el = criar({ desativado: true }).nativeElement as HTMLElement;

    expect(radios(el).every((radio) => radio.disabled)).toBe(true);
  });

  it('mostra a descrição como citação, a nota com o tom e a data', () => {
    const el = criar({
      opcoes: [
        {
          valor: 'd1',
          titulo: 'ALTERACAO',
          descricao: 'Alteração do regulamento',
          citacao: true,
          nota: { rotulo: 'Data de caducidade', valor: '11/02/2026', tom: 'aviso' },
          data: '10/03/2025',
        },
      ],
    }).nativeElement as HTMLElement;

    expect(el.querySelector('.opcao-citacao')?.textContent).toBe('“Alteração do regulamento”');
    expect(el.querySelector('.opcao-nota')?.textContent).toContain('Data de caducidade');
    expect(el.querySelector('.opcao-nota-valor.opcao-nota-aviso')?.textContent).toBe('11/02/2026');
    expect(el.querySelector('.opcao-data')?.textContent).toBe('10/03/2025');
  });

  it('mostra o prazo dentro do rótulo do radio, com <time> e o selo de aviso quando existe', () => {
    const el = criar({
      opcoes: [
        { valor: 'a', titulo: 'Alteração', prazo: { rotulo: 'Caduca em', data: '16/11/2026', datetime: '2026-11-16', selo: 'Faltam 10 dias' } },
        { valor: 'b', titulo: 'Revisão', prazo: { rotulo: 'Caduca em', data: '06/02/2027', datetime: '2027-02-06' } },
        { valor: 'c', titulo: 'Elaboração' },
      ],
    }).nativeElement as HTMLElement;
    const [comSelo, semSelo, semPrazo] = [...el.querySelectorAll('label.opcao')];

    const prazo = comSelo.querySelector('.opcao-prazo') as HTMLElement;
    expect(prazo.textContent?.replace(/\s+/g, ' ').trim()).toBe('Caduca em 16/11/2026 Faltam 10 dias');
    expect(prazo.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-16');
    expect(prazo.querySelector('app-selo')?.className).toContain('selo-aviso');

    expect(semSelo.querySelector('.opcao-prazo time')?.textContent).toBe('06/02/2027');
    expect(semSelo.querySelector('.opcao-prazo app-selo')).toBeNull();
    expect(semPrazo.querySelector('.opcao-prazo')).toBeNull();
  });

  it('na grelha mostra o ícone de cada opção, escondido do leitor de ecrã', () => {
    const el = criar({
      disposicao: 'grelha',
      opcoes: [
        { valor: 'novo', titulo: 'Novo IGT', icone: 'mais' },
        { valor: 'existente', titulo: 'IGT Existente', icone: 'prancheta' },
      ],
    }).nativeElement as HTMLElement;

    expect(el.querySelector('fieldset')?.classList.contains('lista-grelha')).toBe(true);
    const icones = [...el.querySelectorAll('.opcao-icone')];
    expect(icones).toHaveLength(2);
    expect(icones.every((icone) => icone.getAttribute('aria-hidden') === 'true')).toBe(true);
  });
});
