export type Modo = 'continuar' | 'nova';
export type OrigemIgt = 'novo' | 'existente';
export type PaginaInicial = 'tipo' | 'origem' | 'novo' | 'igt' | 'acao' | 'continuar' | 'nova' | 'semIgt' | 'continuarSemIgt';
/** Sem GeoConform, as Plantas vêm antes dos documentos e os Metadados depois (Figma «Decisão de elaborar sem Geoconform»). */
export type SubpaginaFormulario = 'cabecalho' | 'plantas' | 'documentos' | 'metadados';
