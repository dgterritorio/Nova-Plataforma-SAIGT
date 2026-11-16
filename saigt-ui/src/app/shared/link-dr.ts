/** O `linkDr` vem da INCM ou do registo externo: só se liga a `https`, nunca a outro esquema. */
export function linkDrSeguro(link: string | null): string | null {
  return link?.startsWith('https://') ? link : null;
}
