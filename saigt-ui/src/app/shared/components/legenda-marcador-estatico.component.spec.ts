import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { LegendaMarcadorEstaticoComponent } from './legenda-marcador-estatico.component';

describe('LegendaMarcadorEstaticoComponent', () => {
  it('mostra a legenda do marcador estático', () => {
    TestBed.configureTestingModule({ imports: [LegendaMarcadorEstaticoComponent] });
    const fixture: ComponentFixture<LegendaMarcadorEstaticoComponent> = TestBed.createComponent(LegendaMarcadorEstaticoComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe(
      '* conteúdo de demonstração — ainda sem dados reais.',
    );
  });
});
