import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { ProcedimentoConfig, TipoProcedimentoConfig } from '../../../models/configuracao.model';
import {
  AJUDA_TIPO_PROCEDIMENTO_EM_USO,
  AVISO_SEM_FORMULARIO,
  ProcedimentoConfigModalComponent,
  ValorProcedimentoConfig,
} from './procedimento-config-modal.component';

const TIPOS: TipoProcedimentoConfig[] = [
  { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' },
  { id: 2, codigo: 'PARECER_NOVO', nome: 'Parecer novo', ativo: true, formulario: null },
];

const DECISAO_EM_USO: ProcedimentoConfig = {
  id: 10,
  instrumentoDinamicaConfigId: 5,
  tipoProcedimento: { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início' },
  ordem: 1,
  obrigatorio: true,
  serieDr: 'SEGUNDA_H',
  tamanhoMaximoProcesso: 5368709120,
  repetivel: true,
  atoPontual: false,
  ativo: true,
  formulario: 'INFORMACAO_GERAL',
  emUso: true,
  visivelNaDinamica: false,
};

@Component({
  imports: [ProcedimentoConfigModalComponent],
  template: `
    <app-procedimento-config-modal
      [aberto]="true"
      dinamica="PROT — Revisão"
      [tiposProcedimento]="tipos"
      [procedimento]="procedimento()"
      [erroServidor]="erro()"
      (guardar)="valores.push($event)"
    />
  `,
})
class AnfitriaoComponent {
  readonly tipos = TIPOS;
  readonly procedimento = signal<ProcedimentoConfig | null>(null);
  readonly erro = signal<ApiError | null>(null);
  readonly valores: ValorProcedimentoConfig[] = [];
}

describe('ProcedimentoConfigModalComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(procedimento: ProcedimentoConfig | null = null): AnfitriaoComponent {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.componentInstance.procedimento.set(procedimento);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const dialogo = (): HTMLElement => document.querySelector('[role="dialog"]') as HTMLElement;
  const campo = <T extends HTMLElement>(rotulo: string): T => {
    const label = [...dialogo().querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as T;
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

  function escrever(entrada: HTMLInputElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  it('ao criar: alerta para configurar permissões e itens, "Obrigatório" marcado e as outras caixas não', () => {
    criar();

    expect(dialogo().querySelector('h2')?.textContent).toContain('Adicionar Procedimento');
    expect(dialogo().textContent).toContain('PROT — Revisão');
    expect(dialogo().querySelector('app-alerta[role="status"]')?.textContent).toContain(
      'Depois de criar, configure as permissões e os itens documentais deste Procedimento.',
    );
    expect(campo<HTMLInputElement>('Obrigatório').checked).toBe(true);
    expect(campo<HTMLInputElement>('Repetível em sequência').checked).toBe(false);
    expect(campo<HTMLInputElement>('Ato pontual (fora do sequenciamento)').checked).toBe(false);
    expect(campo<HTMLInputElement>('Visível, só em leitura, às entidades com processos na mesma Dinâmica').checked).toBe(false);
    expect(descricao(campo('Limite agregado do processo (MB)'))).toContain('Vazio = sem limite. Valor de omissão do legado: 5120 MB.');
    expect([...campo<HTMLSelectElement>('Série do DR').options].map((o) => o.textContent?.trim()).slice(1)).toEqual([
      '1.ª série',
      '2.ª série — parte H',
      '2.ª série — parte C',
    ]);
  });

  it('Decisão de início, ordem 1, 2.ª série — parte H e 5120 MB: emite 5368709120 bytes', () => {
    const anfitriao = criar();
    escolher(campo('Tipo de Procedimento'), '1');
    escrever(campo('Ordem'), '1');
    escolher(campo('Série do DR'), 'SEGUNDA_H');
    escrever(campo('Limite agregado do processo (MB)'), '5120');
    campo<HTMLInputElement>('Ato pontual (fora do sequenciamento)').click();
    campo<HTMLInputElement>('Visível, só em leitura, às entidades com processos na mesma Dinâmica').click();
    fixture.detectChanges();
    botaoGuardar().click();
    fixture.detectChanges();

    expect(anfitriao.valores).toEqual([
      {
        tipoProcedimentoId: 1,
        ordem: 1,
        serieDr: 'SEGUNDA_H',
        tamanhoMaximoProcesso: 5368709120,
        obrigatorio: true,
        repetivel: false,
        atoPontual: true,
        visivelNaDinamica: true,
      },
    ]);
  });

  it('limite vazio segue como null; ordem 0 e limite decimal dão erro e Guardar fica desativado', () => {
    const anfitriao = criar();
    escolher(campo('Tipo de Procedimento'), '1');
    escolher(campo('Série do DR'), 'PRIMEIRA');
    escrever(campo('Ordem'), '0');
    escrever(campo('Limite agregado do processo (MB)'), '1.5');

    expect(descricao(campo('Ordem'))).toContain('Use um número inteiro igual ou superior a 1.');
    expect(descricao(campo('Limite agregado do processo (MB)'))).toContain('Use um número inteiro de MB igual ou superior a 1, ou deixe vazio.');
    expect(botaoGuardar().disabled).toBe(true);

    escrever(campo('Ordem'), '2');
    escrever(campo('Limite agregado do processo (MB)'), '');
    botaoGuardar().click();
    fixture.detectChanges();
    expect(anfitriao.valores[0].tamanhoMaximoProcesso).toBeNull();
    expect(anfitriao.valores[0].ordem).toBe(2);
  });

  it('um tipo sem formulário escolhido mostra o aviso; ao voltar a um com formulário o aviso sai', () => {
    criar();
    expect(dialogo().textContent).not.toContain(AVISO_SEM_FORMULARIO);

    escolher(campo('Tipo de Procedimento'), '2');
    const aviso = [...dialogo().querySelectorAll('app-alerta')].find((a) => a.textContent?.includes(AVISO_SEM_FORMULARIO));
    expect(aviso?.classList).toContain('alerta-aviso');

    escolher(campo('Tipo de Procedimento'), '1');
    expect(dialogo().textContent).not.toContain(AVISO_SEM_FORMULARIO);
  });

  it('edição com emUso: o tipo fica desativado com a ajuda, o limite aparece em MB e as caixas como estão', () => {
    const anfitriao = criar(DECISAO_EM_USO);

    const tipo = campo<HTMLSelectElement>('Tipo de Procedimento');
    expect(tipo.disabled).toBe(true);
    expect(descricao(tipo)).toContain(AJUDA_TIPO_PROCEDIMENTO_EM_USO);
    expect(campo<HTMLInputElement>('Limite agregado do processo (MB)').value).toBe('5120');
    expect(campo<HTMLInputElement>('Repetível em sequência').checked).toBe(true);
    expect(dialogo().textContent).not.toContain('Depois de criar');

    botaoGuardar().click();
    fixture.detectChanges();
    expect(anfitriao.valores[0]).toEqual({
      tipoProcedimentoId: 1,
      ordem: 1,
      serieDr: 'SEGUNDA_H',
      tamanhoMaximoProcesso: 5368709120,
      obrigatorio: true,
      repetivel: true,
      atoPontual: false,
      visivelNaDinamica: false,
    });
  });

  it('400 com campos fica no campo do mesmo path; 409 EM_USO vai para o alerta de erro no topo', () => {
    const anfitriao = criar(DECISAO_EM_USO);
    anfitriao.erro.set(new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'tamanhoMaximoProcesso', erro: 'Tem de ser maior que 0.' }]));
    fixture.detectChanges();
    expect(descricao(campo('Limite agregado do processo (MB)'))).toContain('Tem de ser maior que 0.');
    expect(dialogo().querySelector('app-alerta[role="alert"]')).toBeNull();

    anfitriao.erro.set(new ApiError('EM_USO', 'Já há processos desta configuração.', 't', null));
    fixture.detectChanges();
    const alerta = dialogo().querySelector('app-alerta[role="alert"]');
    expect(alerta?.textContent).toContain('Já há processos desta configuração.');
    expect(dialogo().querySelector('form')?.firstElementChild).toBe(alerta);
  });
});
