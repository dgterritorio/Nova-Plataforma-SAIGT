import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ESTADOS_PROCESSO, EstadoProcessoBadgeComponent, rotuloEstado } from './estado-processo-badge.component';

describe('estado-processo-badge', () => {
  it.each([
    ['ENVIADO_DGT', 'Enviado DGT'],
    ['ANULADO_INCM', 'Anulado INCM'],
    ['REJEITADO_DGT', 'Rejeitado DGT'],
  ])('rotula %s como "%s"', (estado, rotulo) => {
    expect(rotuloEstado(estado)).toBe(rotulo);
  });

  it.each([
    ['SUBMETIDO', 'Enviado DGT'],
    ['REJEITADO_INCM', 'Anulado INCM'],
  ])('traduz a entrada antiga %s do histórico para "%s"', (estado, rotulo) => {
    expect(rotuloEstado(estado)).toBe(rotulo);
  });

  it('mostra um estado desconhecido tal como veio', () => {
    expect(rotuloEstado('OUTRO')).toBe('OUTRO');
  });

  it('não oferece os estados antigos no filtro', () => {
    expect(ESTADOS_PROCESSO).toContain('ENVIADO_DGT');
    expect(ESTADOS_PROCESSO).toContain('ANULADO_INCM');
    expect(ESTADOS_PROCESSO).not.toContain('SUBMETIDO');
    expect(ESTADOS_PROCESSO).not.toContain('REJEITADO_INCM');
  });

  it.each([
    ['ENVIADO_DGT', 'Enviado DGT'],
    ['ANULADO_INCM', 'Anulado INCM'],
    ['SUBMETIDO', 'Enviado DGT'],
  ])('o badge de %s diz "%s"', (estado, rotulo) => {
    const fixture = TestBed.createComponent(EstadoProcessoBadgeComponent);
    fixture.componentRef.setInput('estado', estado);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(rotulo);
  });

  it.each([
    ['EM_SUBMISSAO', 'info'],
    ['ENVIADO_DGT', 'aviso'],
    ['EM_VALIDACAO_DGT', 'aviso'],
    ['EM_VALIDACAO_INCM', 'cinza'],
    ['ANULADO_INCM', 'cinza'],
    ['VALIDADO_DGT', 'sucesso'],
    ['PUBLICADO', 'sucesso'],
    ['REJEITADO_DGT', 'erro'],
    ['RASCUNHO', 'neutro'],
    ['ENVIADO_INCM', 'neutro'],
    ['SUBMETIDO', 'aviso'],
    ['OUTRO', 'neutro'],
  ])('renderiza %s num app-selo com o tom %s', (estado, tom) => {
    const fixture = TestBed.createComponent(EstadoProcessoBadgeComponent);
    fixture.componentRef.setInput('estado', estado);
    fixture.detectChanges();

    const selo: HTMLElement = fixture.nativeElement.querySelector('app-selo');
    expect(selo.classList).toContain(`selo-${tom}`);
    expect(selo.classList).toContain('selo-retangulo');
  });
});
