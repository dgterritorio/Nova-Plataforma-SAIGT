import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { MarcadorEstaticoComponent } from './marcador-estatico.component';

describe('MarcadorEstaticoComponent', () => {
  let fixture: ComponentFixture<MarcadorEstaticoComponent>;

  function criar(motivo: string, issue: string): void {
    TestBed.configureTestingModule({ imports: [MarcadorEstaticoComponent] });
    fixture = TestBed.createComponent(MarcadorEstaticoComponent);
    fixture.componentRef.setInput('motivo', motivo);
    fixture.componentRef.setInput('issue', issue);
    fixture.detectChanges();
  }

  it('mostra um "*"', () => {
    criar('Sem endpoint de agregação', '#100');
    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('*');
  });

  it('mostra o motivo e a issue pendente no title e no aria-label', () => {
    criar('Sem endpoint de agregação', '#100');
    const span = fixture.nativeElement.querySelector('.marcador-estatico') as HTMLElement;

    expect(span.title).toBe('Conteúdo de demonstração — Sem endpoint de agregação (pendente de #100)');
    expect(span.getAttribute('aria-label')).toBe('Conteúdo de demonstração — Sem endpoint de agregação (pendente de #100)');
  });

  it('omite o "pendente de" quando não há issue', () => {
    TestBed.configureTestingModule({ imports: [MarcadorEstaticoComponent] });
    fixture = TestBed.createComponent(MarcadorEstaticoComponent);
    fixture.componentRef.setInput('motivo', 'Versão escrita no componente, não vem do build');
    fixture.detectChanges();

    const span = fixture.nativeElement.querySelector('.marcador-estatico') as HTMLElement;
    expect(span.title).toBe('Conteúdo de demonstração — Versão escrita no componente, não vem do build');
  });
});
