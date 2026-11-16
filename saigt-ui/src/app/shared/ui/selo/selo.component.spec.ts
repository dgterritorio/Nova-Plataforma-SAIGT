import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SeloComponent, TomSelo } from './selo.component';

function criar(tom?: TomSelo, forma?: 'retangulo' | 'pill'): HTMLElement {
  const fixture = TestBed.createComponent(SeloComponent);
  if (tom) fixture.componentRef.setInput('tom', tom);
  if (forma) fixture.componentRef.setInput('forma', forma);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('SeloComponent', () => {
  it.each<TomSelo>(['neutro', 'info', 'aviso', 'sucesso', 'erro', 'cinza', 'laranja', 'violeta', 'ciano'])('aplica a classe do tom %s', (tom) => {
    expect(criar(tom).classList).toContain(`selo-${tom}`);
  });

  it.each(['violeta', 'ciano'])('o tom %s usa os papéis semânticos, sem hex', (tom) => {
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/selo/selo.component.css'), 'utf-8');
    const regra = new RegExp(`:host\\(\\.selo-${tom}\\) \\{([^}]*)\\}`).exec(css)?.[1] ?? '';
    expect(regra).toContain(`var(--cor-selo-${tom}-fundo)`);
    expect(regra).toContain(`var(--cor-selo-${tom})`);
    expect(regra).not.toMatch(/#[0-9a-f]{3,8}/i);
  });

  it('é neutro e retangular por omissão', () => {
    const selo = criar();
    expect(selo.classList).toContain('selo-neutro');
    expect(selo.classList).toContain('selo-retangulo');
  });

  it('aceita a forma pill', () => {
    expect(criar('info', 'pill').classList).toContain('selo-pill');
  });

  it('é normal por omissão e aceita o tamanho pequeno, em maiúsculas', () => {
    expect(criar().classList).toContain('selo-normal');
    const fixture = TestBed.createComponent(SeloComponent);
    fixture.componentRef.setInput('tamanho', 'pequeno');
    fixture.detectChanges();
    expect(fixture.nativeElement.classList).toContain('selo-pequeno');
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/selo/selo.component.css'), 'utf-8');
    expect(css).toMatch(/:host\(\.selo-pequeno\) \{[^}]*text-transform: uppercase/);
  });
});
