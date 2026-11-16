/** Textos do Figma da Elaboração, pelo código do tipo de Instrumento; um tipo sem texto mostra só o nome. */
export const DESCRICOES_TIPO_IGT: Readonly<Record<string, string>> = {
  PDM: 'Instrumento de gestão territorial de âmbito municipal que estabelece a estratégia de desenvolvimento territorial do município.',
  PU: 'Define a organização espacial de parte do território municipal, incluindo a estrutura urbana, o sistema de acessibilidades e transportes, e os parâmetros urbanísticos.',
  PP: 'Desenvolve e concretiza propostas de ocupação de qualquer área do território municipal, estabelecendo regras sobre a implantação das infraestruturas.',
  PDIM: 'Instrumento de gestão territorial de âmbito intermunicipal que articula as estratégias de desenvolvimento de dois ou mais municípios.',
  PUIM: 'Define a organização espacial de áreas que abrangem o território de dois ou mais municípios, com vista à sua coerência.',
  PPIM: 'Desenvolve e concretiza propostas de ocupação de áreas que abrangem o território de dois ou mais municípios.',
};

/** Ordem do Figma da Elaboração; um tipo fora desta lista fica no fim, pela ordem do backend. */
export const ORDEM_TIPOS_IGT: readonly string[] = ['PDM', 'PU', 'PP', 'PDIM', 'PUIM', 'PPIM'];
