import { describe, expect, it } from 'vitest';
import { ehPerfilBackoffice, ehUtilizadorDgt, temCentroNotificacoes, temPlantasSaigt } from './perfis-backoffice';

describe('perfis-backoffice', () => {
  it.each([
    ['MASTER', true],
    ['TECNICO', false],
    ['VISITANTE', false],
    [null, false],
  ] as const)('ehPerfilBackoffice(%s) é %s — o backoffice é só do MASTER', (perfil, esperado) => {
    expect(ehPerfilBackoffice(perfil)).toBe(esperado);
  });

  it.each([
    ['MASTER', true],
    ['TECNICO', true],
    ['VISITANTE', false],
    [null, false],
  ] as const)('temPlantasSaigt(%s) é %s — Master e Técnico', (perfil, esperado) => {
    expect(temPlantasSaigt(perfil)).toBe(esperado);
  });

  it.each([
    ['MASTER', true],
    ['TECNICO', true],
    ['VISITANTE', true],
    [null, false],
  ] as const)('ehUtilizadorDgt(%s) é %s — qualquer perfil interno', (perfil, esperado) => {
    expect(ehUtilizadorDgt(perfil)).toBe(esperado);
  });

  it.each([
    ['MASTER', true],
    ['TECNICO', false],
    ['VISITANTE', false],
    [null, true],
  ] as const)('temCentroNotificacoes(%s) é %s — só MASTER e utilizadores das entidades', (perfil, esperado) => {
    expect(temCentroNotificacoes(perfil)).toBe(esperado);
  });
});
