import { Component, signal, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { InstrumentoAbrangido, ModalidadeRatificacao, TerritorioInstrumento } from '../../../models/processo.model';
import { FormularioRatificacaoComponent } from './formulario-ratificacao.component';

@Component({
  standalone: true,
  imports: [FormularioRatificacaoComponent],
  template: `
    <app-formulario-ratificacao
      [tipoInstrumento]="tipoInstrumento()"
      [territorio]="territorio()"
      [designacao]="designacao()"
      [instrumentosAbrangidos]="instrumentos()"
      [valorInicial]="valorInicial()"
      [bloqueado]="bloqueado()"
      [erroServidor]="erro()"
      [seccaoVisivel]="visivel()"
    >
      <p class="cabecalho-projetado">Informação Geral</p>
    </app-formulario-ratificacao>
  `,
})
class AnfitriaoComponent {
  readonly tipoInstrumento = signal<string | null>('Plano Diretor Municipal');
  readonly territorio = signal<TerritorioInstrumento | null>({
    nutsII: [{ codigo: '1B', nome: 'Grande Lisboa' }],
    concelhos: [{ codigo: '1111', nome: 'SINTRA' }],
  });
  readonly designacao = signal<string | null>('Ratificação do PDM');
  readonly instrumentos = signal<InstrumentoAbrangido[] | null>([{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }]);
  readonly valorInicial = signal<ModalidadeRatificacao | null>(null);
  readonly bloqueado = signal(false);
  readonly erro = signal<string | null>(null);
  readonly visivel = signal(true);
  readonly formulario = viewChild.required(FormularioRatificacaoComponent);
}

describe('FormularioRatificacaoComponent', () => {
  let fixture: ComponentFixture<AnfitriaoComponent>;

  function criar(alterar: (anfitriao: AnfitriaoComponent) => void = () => undefined): void {
    fixture = TestBed.createComponent(AnfitriaoComponent);
    alterar(fixture.componentInstance);
    fixture.detectChanges();
  }

  const seccao = (): HTMLElement | null => fixture.nativeElement.querySelector('section.cartao-ratificacao');
  const rotulos = (): string[] => Array.from(seccao()!.querySelectorAll('dt')).map((dt) => (dt as HTMLElement).textContent!.trim());
  const valores = (): string[] => Array.from(seccao()!.querySelectorAll('dd')).map((dd) => (dd as HTMLElement).textContent!.trim());
  const opcoes = (): HTMLInputElement[] => Array.from(seccao()!.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
  const grupo = (): HTMLFieldSetElement => seccao()!.querySelector('fieldset.modalidade')!;

  it('mostra o Instrumento a ratificar só de leitura e o Tipo de procedimento como opções, pela ordem da EF', () => {
    criar();

    const titulo = seccao()!.querySelector('h2')!;
    expect(titulo.textContent!.trim()).toBe('Ratificação');
    expect(seccao()!.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(rotulos()).toEqual(['Tipo de instrumento', 'NUT II', 'Concelho', 'Designação']);
    expect(valores()).toEqual(['Plano Diretor Municipal', 'Grande Lisboa', 'Sintra', 'PDM de Sintra']);
    const ordem = Array.from(seccao()!.querySelectorAll('dt, legend')).map((elemento) => elemento.textContent!.trim());
    expect(ordem).toEqual(['Tipo de instrumento', 'Tipo de procedimento *', 'NUT II', 'Concelho', 'Designação']);
    expect(Array.from(grupo().querySelectorAll('label')).map((label) => label.textContent!.trim())).toEqual([
      'Publicação – Ratificação',
      'Alteração – Ratificação',
    ]);
    expect(seccao()!.querySelectorAll('input:not([type="radio"]), select, textarea')).toHaveLength(0);
  });

  it('o grupo de opções é anunciado como obrigatório, com legenda', () => {
    criar();

    expect(grupo().getAttribute('role')).toBe('radiogroup');
    expect(grupo().getAttribute('aria-required')).toBe('true');
    expect(grupo().querySelector('legend')!.textContent).toContain('Tipo de procedimento');
    expect(new Set(opcoes().map((opcao) => opcao.name)).size).toBe(1);
  });

  it('no PDIM mostra os concelhos e a designação do PDIM', () => {
    criar((anfitriao) => {
      anfitriao.tipoInstrumento.set('Plano Diretor Intermunicipal');
      anfitriao.territorio.set({
        nutsII: [{ codigo: '1B', nome: 'Grande Lisboa' }],
        concelhos: [
          { codigo: '1111', nome: 'SINTRA' },
          { codigo: '1106', nome: 'CASCAIS' },
        ],
      });
      anfitriao.instrumentos.set([{ id: 'i2', designacao: 'PDIM da Serra', tipoInstrumento: 'PDIM' }]);
    });

    expect(rotulos()).toEqual(['Tipo de instrumento', 'NUT II', 'Concelhos', 'Designação']);
    expect(valores()).toEqual(['Plano Diretor Intermunicipal', 'Grande Lisboa', 'Sintra, Cascais', 'PDIM da Serra']);
  });

  it('repõe a modalidade gravada e devolve a escolha em dados()', () => {
    criar((anfitriao) => anfitriao.valorInicial.set('ALTERACAO'));

    expect(opcoes()[1].checked).toBe(true);
    expect(fixture.componentInstance.formulario().dados()).toEqual({ modalidadeRatificacao: 'ALTERACAO' });

    opcoes()[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.formulario().dados()).toEqual({ modalidadeRatificacao: 'PUBLICACAO' });
    expect(fixture.componentInstance.formulario().modalidade()).toBe('PUBLICACAO');
  });

  it('sem escolha, dados() leva a modalidade nula', () => {
    criar();

    expect(fixture.componentInstance.formulario().dados()).toEqual({ modalidadeRatificacao: null });
  });

  it('o erro do servidor fica junto do grupo, anunciado e ligado por aria-describedby, e sai ao escolher', () => {
    criar((anfitriao) => anfitriao.erro.set('Indique se a ratificação é uma Publicação ou uma Alteração.'));

    const erro = seccao()!.querySelector<HTMLElement>('.erro-campo')!;
    expect(erro.getAttribute('role')).toBe('alert');
    expect(erro.textContent!.trim()).toBe('Indique se a ratificação é uma Publicação ou uma Alteração.');
    expect(grupo().getAttribute('aria-describedby')).toBe(erro.id);
    expect(grupo().getAttribute('aria-invalid')).toBe('true');

    opcoes()[1].click();
    fixture.detectChanges();

    expect(seccao()!.querySelector('.erro-campo')).toBeNull();
    expect(grupo().getAttribute('aria-describedby')).toBeNull();
  });

  it('aplicarErro mostra a mensagem e põe o foco no grupo', async () => {
    criar();

    document.body.appendChild(fixture.nativeElement);
    fixture.componentInstance.formulario().aplicarErro('Valores aceites: PUBLICACAO, ALTERACAO.');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(seccao()!.querySelector('.erro-campo')!.textContent!.trim()).toBe('Valores aceites: PUBLICACAO, ALTERACAO.');
    expect(document.activeElement).toBe(opcoes()[0]);
  });

  it('bloqueada depois da primeira submissão: opções desativadas, aviso ligado ao grupo e a gravada continua em dados()', () => {
    criar((anfitriao) => {
      anfitriao.valorInicial.set('PUBLICACAO');
      anfitriao.bloqueado.set(true);
    });

    expect(opcoes().every((opcao) => opcao.disabled)).toBe(true);
    const aviso = seccao()!.querySelector<HTMLElement>('.aviso-bloqueado')!;
    expect(aviso.textContent!.trim()).toBe('Bloqueado depois da primeira submissão');
    expect(grupo().getAttribute('aria-describedby')).toBe(aviso.id);
    expect(fixture.componentInstance.formulario().dados()).toEqual({ modalidadeRatificacao: 'PUBLICACAO' });

    fixture.componentInstance.bloqueado.set(false);
    fixture.detectChanges();

    expect(opcoes().every((opcao) => !opcao.disabled)).toBe(true);
  });

  it('projeta o cabeçalho comum depois da secção e, com a secção escondida, mantém a escolha', () => {
    criar((anfitriao) => anfitriao.valorInicial.set('ALTERACAO'));

    const projetado = fixture.nativeElement.querySelector('.cabecalho-projetado')!;
    expect(seccao()!.compareDocumentPosition(projetado) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    fixture.componentInstance.visivel.set(false);
    fixture.detectChanges();

    expect(seccao()).toBeNull();
    expect(fixture.nativeElement.querySelector('.cabecalho-projetado')).not.toBeNull();
    expect(fixture.componentInstance.formulario().dados()).toEqual({ modalidadeRatificacao: 'ALTERACAO' });
  });

  it('sem dados do Instrumento mostra travessões e, sem abrangidos, a designação do processo', () => {
    criar((anfitriao) => {
      anfitriao.tipoInstrumento.set(null);
      anfitriao.territorio.set(null);
      anfitriao.instrumentos.set([]);
    });

    expect(valores()).toEqual(['—', '—', '—', 'Ratificação do PDM']);
  });
});
