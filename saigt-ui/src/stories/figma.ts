const PASTA_MOCKUPS = 'https://gitlab.conkord.net/delivery-iberia/saigt/saigt-implementation/-/tree/master/docs/design-to-build/mockups';

export const ECRAS_FIGMA = {
  'UI-001': 'Painel/UI-001_Homepage-Entidade',
  'UI-050': 'Criar-Processo/UI-050_Criar-Processo-Confirmar-Pedido',
  'UI-051': 'Criar-Processo/UI-051_Preencher-Cabecalho',
  'UI-016': 'Consultar-Processos/UI-016_Consultar-Processos',
  'UI-026': 'Aceder-ao-Sistema/UI-026_Login',
  'UI-045': 'Criar-Processo/UI-045_Criar-Processo-Tipo-Camara-Municipal',
  'UI-046': 'Criar-Processo/UI-046_Criar-Processo-IGT-Novo-ou-Existente',
  'UI-047': 'Criar-Processo/UI-047_Criar-Processo-Selecionar-IGT-Existente',
  'UI-058': 'Relatorios/UI-058_Relatorios',
  'UI-060': 'Utilizadores-e-Entidades/UI-060_Utilizadores-DGT',
  'UI-063': 'Backoffice-Configuracao/UI-063_Workflows',
  'UI-069': 'Backoffice-Processos/UI-069_Detalhe-Processo-Alterar-Estado',
} as const;

export type EcraFigma = keyof typeof ECRAS_FIGMA;

export function design(ecra: EcraFigma): { type: 'link'; url: string; ecra: EcraFigma } {
  return { type: 'link', url: `${PASTA_MOCKUPS}/${ECRAS_FIGMA[ecra]}`, ecra };
}

export function referenciaFigma(...ecras: EcraFigma[]): string {
  return `Referência no Figma: ${ecras.map((ecra) => `[${ecra}](${PASTA_MOCKUPS}/${ECRAS_FIGMA[ecra]})`).join(' · ')}.`;
}
