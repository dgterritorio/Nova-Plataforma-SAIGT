import { formatDate } from '@angular/common';
import { AtoReferivel } from '../../models/instrumento.model';

/**
 * Nomes do catálogo `tipo_procedimento` dos atos que um ato pontual pode visar. A API só manda o código; o assistente
 * prefere o nome que o painel do Instrumento traz e este fica para o detalhe e quando o painel falha.
 */
const NOMES_TIPOS_REFERIVEIS: Readonly<Record<string, string>> = {
  DECISAO_INICIO: 'Decisão de início',
  ABERTURA_DISCUSSAO_PUBLICA: 'Abertura do período de discussão pública',
  ABERTURA_DISCUSSAO_PUBLICA_RERAE: 'Abertura do período de discussão pública (RERAE)',
  ABERTURA_DISCUSSAO_PUBLICA_DL10: 'Abertura do período de discussão pública (DL 10/2024)',
};

export const AJUDA_ATO_REFERIDO = 'Os outros atos e anexos só se aplicam a uma Decisão de início ou a uma Discussão pública já publicadas.';

export function nomeTipoReferido(codigo: string, nomes: Readonly<Record<string, string>> = {}): string {
  return nomes[codigo] ?? NOMES_TIPOS_REFERIVEIS[codigo] ?? codigo;
}

/** «Decisão de início — processo n.º 00005 — DR 123/2026, 10/11/2026». */
export function rotuloAtoReferivel(ato: AtoReferivel, localizacao: string, nomes: Readonly<Record<string, string>> = {}): string {
  const data = ato.dataPublicacaoDr ? formatDate(ato.dataPublicacaoDr, 'dd/MM/yyyy', localizacao) : null;
  const dr = ato.numeroDr ? `DR ${ato.numeroDr}${data ? `, ${data}` : ''}` : data ? `DR de ${data}` : null;
  return [nomeTipoReferido(ato.tipoProcedimento, nomes), `processo n.º ${ato.numeroProcesso}`, dr].filter(Boolean).join(' — ');
}

/** «processo n.º 00005 (Decisão de início)». */
export function resumoAtoReferido(numeroProcesso: string, tipoProcedimento: string | null | undefined, nomes: Readonly<Record<string, string>> = {}): string {
  return tipoProcedimento ? `processo n.º ${numeroProcesso} (${nomeTipoReferido(tipoProcedimento, nomes)})` : `processo n.º ${numeroProcesso}`;
}
