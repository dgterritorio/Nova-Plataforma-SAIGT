import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CartaoIdentidadeComponent } from './cartao-identidade.component';

describe('CartaoIdentidadeComponent', () => {
  let fixture: ComponentFixture<CartaoIdentidadeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CartaoIdentidadeComponent] });
    fixture = TestBed.createComponent(CartaoIdentidadeComponent);
  });

  it('mostra as iniciais do primeiro e do último nome', () => {
    fixture.componentInstance.nome = 'Ana Teixeira';
    fixture.componentInstance.estadoTexto = 'Ativo';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.avatar').textContent.trim()).toBe('AT');
  });

  it('mostra só uma letra quando o nome tem uma única palavra', () => {
    fixture.componentInstance.nome = 'Ana';
    fixture.componentInstance.estadoTexto = 'Ativo';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.avatar').textContent.trim()).toBe('A');
  });

  it('mostra um traço quando não há nome', () => {
    fixture.componentInstance.estadoTexto = 'Ativo';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.avatar').textContent.trim()).toBe('—');
    expect(fixture.nativeElement.querySelector('.nome').textContent.trim()).toBe('—');
  });

  it('omite subtítulo e email quando ausentes', () => {
    fixture.componentInstance.nome = 'Ana Teixeira';
    fixture.componentInstance.estadoTexto = 'Ativo';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.subtitulo')).toBeNull();
    expect(fixture.nativeElement.querySelector('.email')).toBeNull();
  });

  it('mostra subtítulo, email e o texto de estado quando presentes', () => {
    fixture.componentInstance.nome = 'Ana Teixeira';
    fixture.componentInstance.subtitulo = 'Técnica Superior';
    fixture.componentInstance.email = 'ana.teixeira@dgterritorio.gov.pt';
    fixture.componentInstance.estadoTexto = 'Autenticado via DGT — Active Directory';
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Técnica Superior');
    expect(texto).toContain('ana.teixeira@dgterritorio.gov.pt');
    expect(texto).toContain('Autenticado via DGT — Active Directory');
  });
});
